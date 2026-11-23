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

async function newPage(browser) {
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
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("请输入用户名").fill(ADMIN.username);
  await page.getByPlaceholder("请输入密码").fill(ADMIN.password);
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
        // scrum 模板只允许相邻移动：待办 → 已完成 必须被拦
        await dragCard(page, TITLE, page.locator(".board__column-body").nth(2));
        const msgs = await page.locator(".el-message").allInnerTexts();
        const text = msgs.join(" | ");
        const blocked = /工作流不允许/.test(text);
        const whereHint = /当前可以从这里去/.test(text);
        const after = await api("GET", `/issues/${issueId}`, null, token);
        record(
          "越界的拖拽确实被工作流拦下（卡片留在原列）",
          blocked && after?.data?.columnId === 1,
          `提示=「${text.slice(0, 70)}」, columnId=${after?.data?.columnId}`
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
        await dragCard(page, TITLE, page.locator(".board__column-body").nth(1));
        const after = await api("GET", `/issues/${issueId}`, null, token);
        record(
          "规则允许的移动照常成功（待办 → 进行中）",
          after?.data?.columnId === 2 && after?.data?.status === "IN_PROGRESS",
          `columnId=${after?.data?.columnId}, status=${after?.data?.status}`
        );
      } catch (e) {
        record("允许的移动", false, e.message);
      } finally {
        await context.close();
      }
    }
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
