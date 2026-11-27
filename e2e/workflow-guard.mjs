/**
 * 工作流用例：这条规则到底管不管用、看不懂的地方改了没有。
 *
 * 起因：实际使用反馈"工作流不知所云"。查下来是两件事——
 *   1. 设计器里已保存的规则只有一个名字 + 编辑/删除，不点「编辑」根本看不到允许什么；
 *   2. 看板拖拽被拦时只说"不允许"，不告诉你还能往哪去（/api/workflow/transitions 前端从没调过）。
 *
 * 这个用例就盯着这两点，顺带证明工作流不是装饰：越界的拖拽确实被拦、允许的拖拽确实能过。
 *
 * 跑法：node e2e/workflow-guard.mjs http://localhost:5173
 * 依赖：项目 id=1 上已配置 STORY 工作流（demo 库里是 scrum 模板：待办↔进行中↔已完成）。
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const requirePlaywright = () => {
  const mod = process.env.PLAYWRIGHT_MODULE || "playwright";
  return require(mod);
};

const BASE = process.argv[2] || "http://localhost:5173";
const API = process.env.MIMO_API || "http://localhost:8080/api";
const ADMIN = { username: "admin", password: "123456" };
const STAMP = Date.now().toString().slice(-6);
const TITLE = `工作流用例${STAMP}`;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}  — ${detail}`);
}

async function api(method, path, body, token) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return json;
}

async function login(creds) {
  const json = await api("POST", "/auth/login", creds);
  if (!json?.data?.token) throw new Error("登录失败: " + JSON.stringify(json).slice(0, 160));
  return json.data.token;
}

async function newPage(browser, creds = ADMIN) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  // 关掉新手引导：真 key 是 localStorage.mimo_tour_state，遮罩会吃掉所有点击
  await context.addInitScript(() => {
    try {
      const done = { status: "done", v: 2, at: new Date().toISOString() };
      localStorage.setItem(
        "mimo_tour_state",
        JSON.stringify({ welcome: done, board: done, collab: done })
      );
    } catch {
      /* 忽略 */
    }
  });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  // 连跑十几套时，Vite 重新编译首屏偶发超过 20 秒（后端刚重启时更明显）。
  // 这是环境抖动不是产品问题：重试一次，别让整套用例 0 条检查点就红掉。
  let lastError = null;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded", timeout: 45000 });
      lastError = null;
      break;
    } catch (e) {
      lastError = e;
      await page.waitForTimeout(1500);
    }
  }
  if (lastError) throw lastError;
  await page.getByPlaceholder("请输入用户名").fill(creds.username);
  await page.getByPlaceholder("请输入密码").fill(creds.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
  return { context, page };
}

/** vuedraggable(SortableJS) 只认鼠标事件序列，locator.dragTo 不触发 */
async function dragCard(page, title, targetColumn) {
  const card = page.locator(`.issue-card-wrapper:has-text("${title}")`).first();
  await card.waitFor({ state: "visible", timeout: 15000 });
  const box = await card.boundingBox();
  const tbox = await targetColumn.boundingBox();
  if (!box || !tbox) throw new Error("拿不到卡片/列的位置");
  await page.mouse.move(box.x + box.width / 2, box.y + 14);
  await page.mouse.down();
  await page.waitForTimeout(150);
  await page.mouse.move(box.x + box.width / 2 + 12, box.y + 30, { steps: 8 });
  await page.waitForTimeout(150);
  await page.mouse.move(tbox.x + tbox.width / 2, tbox.y + tbox.height / 2, { steps: 20 });
  await page.waitForTimeout(250);
  await page.mouse.up();
  await page.waitForTimeout(2200);
}

/**
 * 拖一次，然后**等提示条真的出现**（最多 5 秒）。
 * 固定 `waitForTimeout(2200)` 在共享 runner 上不够稳：v2.22.4 的 CI 就红过一次
 * 「6/7 通过」，而本地连跑 5 次都是 8/8——典型的"拖拽后消息还没渲染就去读"
 * （BUG-137）。这里不等死时间，改成等条件；超时了返回 false，由调用方决定要不要再拖一次。
 */
async function dragAndWaitForTip(page, title, targetColumn) {
  await dragCard(page, title, targetColumn);
  const tip = page.locator(".el-message").first();
  const appeared = await tip.waitFor({ state: "visible", timeout: 5000 }).then(() => true).catch(() => false);
  return appeared;
}

async function main() {
  const token = await login(ADMIN);

  // 造一张自己的 STORY 卡（放在「待办」列），不碰 demo 数据
  const created = await api(
    "POST",
    "/issues",
    { projectId: 1, type: "STORY", title: TITLE, status: "TODO", columnId: 1, priority: "MEDIUM" },
    token
  );
  if (created?.code !== 200) throw new Error("建卡失败: " + JSON.stringify(created).slice(0, 200));
  const issueId = created.data.id;
  console.log(`准备：在项目 1 建了一张 STORY 卡「${TITLE}」(id=${issueId})\n`);

  const { chromium } = requirePlaywright();
  const browser = await chromium.launch({ headless: true });

  try {
    // ---------- 1. 设计器不点「编辑」就能看懂规则 ----------
    {
      const { context, page } = await newPage(browser);
      try {
        await page.goto(`${BASE}/projects/1/workflow`, { waitUntil: "domcontentloaded" });
        await page.locator(".workflow-designer__overview").waitFor({ state: "visible", timeout: 20000 });
        const rows = await page.locator(".workflow-designer__rules .el-table__body tr").count();
        const headers = await page.locator(".workflow-designer__rules th").allInnerTexts();
        const hint = await page.locator(".page-hint").first().innerText();
        record(
          "不点「编辑」就能看到这套工作流允许的移动",
          rows >= 1 && headers.join(" ").includes("谁可以"),
          `规则 ${rows} 条，表头=「${headers.map((h) => h.trim()).filter(Boolean).join("/")}」，说明=${hint.slice(0, 28)}…`
        );
      } catch (e) {
        record("设计器只读概览", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ---------- 2. 越界拖拽被拦住，并且告诉用户还能去哪 ----------
    {
      const { context, page } = await newPage(browser);
      try {
        await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
        await page.locator(".board__column").first().waitFor({ state: "visible", timeout: 20000 });
        await page.waitForTimeout(1000);
        // scrum 模板只允许相邻移动：待办 → 已完成 必须被拦。
        // 拖拽是鼠标事件序列，慢机上可能没落到目标列（提示条不出现）——那样就**再拖一次**，
        // 并在 detail 里说明"重试过"，别让一次抖动冒充"功能坏了"。
        let retried = false;
        let appeared = await dragAndWaitForTip(page, TITLE, page.locator(".board__column-body").nth(2));
        if (!appeared) {
          retried = true;
          await page.waitForTimeout(500);
          appeared = await dragAndWaitForTip(page, TITLE, page.locator(".board__column-body").nth(2));
        }
        const msgs = await page.locator(".el-message").allInnerTexts();
        const text = msgs.join(" | ");
        const blocked = /工作流不允许/.test(text);
        const whereHint = /当前可以从这里去/.test(text);
        const after = await api("GET", `/issues/${issueId}`, null, token);
        record(
          "越界的拖拽确实被工作流拦下（卡片留在原列）",
          blocked && after?.data?.columnId === 1,
          `提示=「${text.slice(0, 70)}」, columnId=${after?.data?.columnId}${retried ? "（拖了两次，第一次没出现提示）" : ""}`
        );
        record(
          "被拦时界面告诉用户「还能去哪」，而不是只说不允许",
          whereHint,
          whereHint ? "提示里带了允许的目标列" : `提示里没有可去方向：${text.slice(0, 60)}`
        );
      } catch (e) {
        record("越界拖拽被拦", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ---------- 3. 允许的移动照常能过（证明规则不是只会添堵） ----------
    {
      const { context, page } = await newPage(browser);
      try {
        await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
        await page.locator(".board__column").first().waitFor({ state: "visible", timeout: 20000 });
        await page.waitForTimeout(1000);
        // 允许的移动：拖完先看库里 columnId 变了没有，没变就再拖一次（和上面越界拖同理，
        // 慢机上鼠标序列可能没落到目标列）。detail 里写明是否重试过。
        let retried = false;
        await dragCard(page, TITLE, page.locator(".board__column-body").nth(1));
        let after = await api("GET", `/issues/${issueId}`, null, token);
        if (after?.data?.columnId !== 2) {
          retried = true;
          await page.waitForTimeout(500);
          await dragCard(page, TITLE, page.locator(".board__column-body").nth(1));
          after = await api("GET", `/issues/${issueId}`, null, token);
        }
        record(
          "规则允许的移动照常成功（待办 → 进行中）",
          after?.data?.columnId === 2 && after?.data?.status === "IN_PROGRESS",
          `columnId=${after?.data?.columnId}, status=${after?.data?.status}${retried ? "（拖了两次，第一次没动）" : ""}`
        );
      } catch (e) {
        record("允许的移动", false, e.message);
      } finally {
        await context.close();
      }
    }
    // ---------- 4. 节点责任人：这一步归谁管，别人推不动（v2.19.6） ----------
    {
      const TITLE2 = `${TITLE}责任`;
      const created2 = await api(
        "POST",
        "/issues",
        { projectId: 1, type: "STORY", title: TITLE2, status: "TODO", columnId: 1, priority: "MEDIUM" },
        token
      );
      const issueId2 = created2?.data?.id;
      const wfBefore = await api("GET", "/workflow/project/1/type/STORY", null, token);
      const originalConfig = wfBefore?.data?.config;
      const adminId = (await api("GET", "/user/profile", null, token))?.data?.id;

      try {
        if (!issueId2) throw new Error("第二张卡创建失败: " + JSON.stringify(created2).slice(0, 160));

        // 需要一个「团队里有、但没有任何管理权限」的账号来验证拦截。
        // 坑：开发库里 zhangsan / lisi 在 team_members 里都是 ROLE_ADMIN，
        // 而"管理员永远能推进"是设计内的逃生口，用他们根本测不出拦截。
        const outsider = `e2e-wfowner${STAMP}`;
        await api("POST", "/auth/register", {
          username: outsider,
          password: "123456",
          email: `${outsider}@example.com`,
        });
        const found = await api("GET", `/user/search?keyword=${encodeURIComponent(outsider)}`, null, token);
        const outsiderId = (found?.data || []).find((u) => u.username === outsider)?.id;
        if (outsiderId) {
          await api("POST", "/teams/invite", { teamId: 1, userId: outsiderId, role: "ROLE_MEMBER" }, token);
        }

        // 把「待办」这一节点的责任人设成 admin
        const cfg = JSON.parse(JSON.stringify(originalConfig));
        cfg.nodes.forEach((n) => {
          if (n.columnId === 1) n.ownerUserId = adminId;
        });
        const saved = await api(
          "POST",
          "/workflow",
          { projectId: 1, issueType: "STORY", name: wfBefore.data.name, config: cfg },
          token
        );
        if (saved?.code !== 200) throw new Error("保存带责任人的工作流失败: " + JSON.stringify(saved).slice(0, 160));

        // 4a. 非责任人（纯成员）拖不动这一步
        {
          const { context, page } = await newPage(browser, { username: outsider, password: "123456" });
          try {
            await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
            await page.locator(".board__column").first().waitFor({ state: "visible", timeout: 20000 });
            await page.waitForTimeout(1000);
            await dragCard(page, TITLE2, page.locator(".board__column-body").nth(1));
            const text = (await page.locator(".el-message").allInnerTexts()).join(" | ");
            const after = await api("GET", `/issues/${issueId2}`, null, token);
            record(
              "节点指定了责任人之后，纯成员推不动这一步（卡片留在原列）",
              /负责/.test(text) && after?.data?.columnId === 1,
              `提示=「${text.slice(0, 70)}」, columnId=${after?.data?.columnId}` +
                (outsiderId ? "" : "（临时账号未建成功）")
            );
            record(
              "被责任人挡住时，提示里点了名（说出这一步归谁）",
              /这一步由「admin」负责/.test(text),
              /这一步由「admin」负责/.test(text) ? "提示里带了责任人名字" : `没点名：${text.slice(0, 60)}`
            );
          } catch (e) {
            record("责任人拦住非责任人", false, e.message);
          } finally {
            await context.close();
          }
        }

        // 4b. 责任人本人（这里用 admin 兜底验证管理员的放行口）能推
        {
          const { context, page } = await newPage(browser);
          try {
            await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
            await page.locator(".board__column").first().waitFor({ state: "visible", timeout: 20000 });
            await page.waitForTimeout(1000);
            await dragCard(page, TITLE2, page.locator(".board__column-body").nth(1));
            const after = await api("GET", `/issues/${issueId2}`, null, token);
            record(
              "责任人（或项目管理员）照常能推进到下一步",
              after?.data?.columnId === 2,
              `columnId=${after?.data?.columnId}`
            );
          } catch (e) {
            record("责任人能推进", false, e.message);
          } finally {
            await context.close();
          }
        }
        // 4c. 设计器里能给每个节点指定责任人（配了才有上面那套拦截）
        {
          const { context, page } = await newPage(browser);
          try {
            await page.goto(`${BASE}/projects/1/workflow`, { waitUntil: "domcontentloaded" });
            await page.locator(".workflow-designer__overview").waitFor({ state: "visible", timeout: 20000 });
            await page.locator(".workflow-designer__alert button", { hasText: "编辑" }).first().click();
            await page.locator("[data-workflow-canvas]").waitFor({ state: "visible", timeout: 10000 });
            // Element Plus 2.x 的单选 placeholder 渲染成 span.el-select__placeholder，不是 input 属性
            const ownerSelects = await page
              .locator("[data-workflow-canvas] .workflow-designer__card .el-select")
              .count();
            const placeholders = await page
              .locator("[data-workflow-canvas] .workflow-designer__card .el-select__placeholder")
              .allInnerTexts();
            const nodeCount = await page
              .locator("[data-workflow-canvas] .workflow-designer__card")
              .count();
            record(
              "设计器里每个节点都能指定责任人",
              nodeCount > 0 && ownerSelects === nodeCount && placeholders.some((p) => p.includes("责任人")),
              `节点 ${nodeCount} 个，责任人下拉 ${ownerSelects} 个，占位文案=「${placeholders.join("/").slice(0, 40)}」`
            );
          } catch (e) {
            record("设计器责任人下拉", false, e.message);
          } finally {
            await context.close();
          }
        }

      } finally {
        // 把工作流改回去，别把 demo 项目的规则留成"只有 admin 能推进"
        if (originalConfig) {
          await api(
            "POST",
            "/workflow",
            { projectId: 1, issueType: "STORY", name: wfBefore.data.name, config: originalConfig },
            token
          );
        }
        if (issueId2) await api("DELETE", `/issues/${issueId2}`, null, token);
      }    }
  } finally {
    const del = await api("DELETE", `/issues/${issueId}`, null, token);
    if (del?.code !== 200) console.warn(`⚠ 清理未成功 issue=${issueId}`);
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} 通过 ===`);
  if (failed.length) {
    console.log("失败项：\n" + failed.map((f) => ` - ${f.name}: ${f.detail}`).join("\n"));
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error("用例执行异常:", e);
  process.exitCode = 1;
});
