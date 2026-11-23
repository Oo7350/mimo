/**
 * 「下一步 / 接下来做什么」用例（v2.18.4）
 *
 * 对应这一轮的 P1-P3：
 *   P1 工作项详情给出四段链路 + 唯一主动作（待办 → 进行中 → 待验收 → 已完成）
 *   P2 工作台首屏「接下来做什么」：有数据给三件事，空库给三步上手清单
 *   P3 每页一句话定位（PageHint），这里抽查审计日志页是否真的渲染出来
 *
 * 用法：node e2e/next-step.mjs [baseUrl]
 * 依赖：后端 :8080、前端 :5173 已启动；PLAYWRIGHT_MODULE / PLAYWRIGHT_BROWSERS_PATH 指向已有安装。
 */
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require_ = createRequire(import.meta.url);

function loadPlaywright() {
  const candidates = [];
  if (process.env.PLAYWRIGHT_MODULE) candidates.push(process.env.PLAYWRIGHT_MODULE);
  candidates.push("playwright");
  for (const c of candidates) {
    try {
      return require_(c);
    } catch {
      /* 继续试下一个 */
    }
  }
  console.error("找不到 playwright：设 PLAYWRIGHT_MODULE=<dir>/node_modules/playwright");
  process.exit(2);
}

const { chromium } = loadPlaywright();
const HERE = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] || "http://localhost:5173";
const ADMIN = { username: "admin", password: "123456" };

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

async function apiLogin(creds) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  const json = await res.json();
  if (!json?.data?.token) throw new Error("登录失败: " + JSON.stringify(json).slice(0, 160));
  return json.data.token;
}

async function api(token, method, url, body) {
  const res = await fetch(`${BASE}/api${url}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function freshPage(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript(() => {
    try {
      sessionStorage.setItem("mimo_tour_session_welcome", "1");
      sessionStorage.setItem("mimo_tour_session_board", "1");
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

async function main() {
  const token = await apiLogin(ADMIN);
  const projects = await api(token, "GET", "/projects/my");
  const project = projects?.data?.[0];
  if (!project) throw new Error("库里没有项目");

  // 用一个**专门造出来**的工作项当测试床：现网数据可能已经卡在验收中，
  // 状态不确定的样本没法断言"待办 → 开始处理"。用完删掉，不留痕迹。
  const created = await api(token, "POST", "/issues", {
    projectId: project.id,
    title: `链路用例：下一步 ${Date.now()}`,
    type: "TASK",
    priority: "MEDIUM",
    assigneeId: 1,
    status: "TODO",
  });
  const issueId = created?.data?.id;
  if (!issueId) throw new Error("创建工作项失败: " + JSON.stringify(created).slice(0, 200));
  console.log(`准备：project=${project.id} 用例工作项=${issueId}\n`);

  const browser = await chromium.launch({ headless: true });

  try {
    // ============ 用例 1：详情页有四段链路，待办状态下主动作是「开始处理」 ============
    {
      const { context, page } = await freshPage(browser);
      try {
        await page.goto(`${BASE}/projects/${project.id}/board?issue=${issueId}`, {
          waitUntil: "domcontentloaded",
        });
        const strip = page.locator(".next-step");
        await strip.waitFor({ state: "visible", timeout: 20000 });
        const text = await strip.innerText();
        const hasChain = ["待办", "进行中", "待验收", "已完成"].every((t) => text.includes(t));
        const primary = strip.locator(".next-step__actions button").first();
        const primaryLabel = (await primary.innerText()).trim();
        record(
          "详情页「下一步」给出四段链路，待办状态主动作是开始处理",
          hasChain && primaryLabel.includes("开始处理"),
          `链路=${hasChain}，主动作=${primaryLabel}`
        );

        // ============ 用例 2：点「开始处理」真的把状态推进到进行中 ============
        await primary.click();
        await page.waitForTimeout(2500); // 等 updateIssue + reload 走完
        const after = await api(token, "GET", `/issues/${issueId}`);
        const stripText = await page.locator(".next-step").innerText();
        record(
          "点「开始处理」后状态变为进行中，主动作变成提交验收",
          after?.data?.status === "IN_PROGRESS" && stripText.includes("提交验收"),
          `接口状态=${after?.data?.status}`
        );

        // ============ 用例 3：从「下一步」直接拉起提交验收弹窗 ============
        await page
          .locator(".next-step__actions button", { hasText: "提交验收" })
          .first()
          .click();
        const submitDialog = page.locator(".el-dialog", { hasText: "提交任务验收" }).first();
        await submitDialog.waitFor({ state: "visible", timeout: 15000 });
        record("「下一步」能把提交验收弹窗直接拉起来", true, "弹窗标题=提交任务验收");
      } catch (e) {
        record("详情页「下一步」链路用例", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ============ 用例 4：工作台首屏「接下来做什么」 ============
    {
      const { context, page } = await freshPage(browser);
      try {
        await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
        const block = page.locator(".dash-next");
        await block.waitFor({ state: "visible", timeout: 20000 });
        // 等工作台数据落地：有具体事项 / 上手清单 / 空态，三者都算"结论已出"。
        // 只等 .dash-next 可见会在骨架屏阶段就读到"没有卡在你这儿的事"，
        // 而那正是被修掉的假结论（数据没到就先下判断）。
        await page.waitForFunction(
          () => {
            const el = document.querySelector(".dash-next");
            if (!el) return false;
            return !!(
              el.querySelector(".dash-next__item") ||
              el.querySelector(".dash-next__empty") ||
              el.querySelector(".dash-next__steps")
            );
          },
          null,
          { timeout: 20000 }
        );
        const title = await page.locator(".dash-next__title").innerText();
        const hint = await page.locator(".dash-next__hint").innerText();
        const items = await page.locator(".dash-next__item").count();
        const steps = await page.locator(".dash-next__steps").count();
        record(
          "工作台首屏有「接下来做什么」，并给出可点的具体事项",
          title.includes("接下来做什么") && (items > 0 || steps > 0),
          `标题=${title}，提示=${hint}，三件事=${items}，上手清单=${steps}`
        );
        // 点第一条能落到具体工作项
        if (items > 0) {
          await page.locator(".dash-next__item").first().click();
          await page.waitForURL(/\/projects\/\d+\/board\?issue=\d+/, { timeout: 20000 });
          record("「接下来做什么」第一项能直达对应工作项", true, page.url().replace(BASE, ""));
        }
      } catch (e) {
        record("工作台首屏「接下来做什么」", false, e.message);
      } finally {
        await context.close();
      }
    }

        // ============ 用例 5：每页一句话定位（抽查审计日志页） ============
    {
      const { context, page } = await freshPage(browser);
      try {
        await page.goto(`${BASE}/admin/audit-logs`, { waitUntil: "domcontentloaded" });
        const hint = page.locator(".page-hint");
        await hint.waitFor({ state: "visible", timeout: 20000 });
        const text = await hint.innerText();
        record(
          "管理页有「这一页解决什么问题」的一句话说明",
          text.includes("留痕") && page.url().includes("/admin/audit-logs"),
          text.slice(0, 40) + "…"
        );
      } catch (e) {
        record("管理页「一句话定位」", false, e.message);
      } finally {
        await context.close();
      }
    }
  } finally {
    // 删掉用例造出来的工作项（删不掉要显式告警，别静默留垃圾数据）
    try {
      const del = await api(token, "DELETE", `/issues/${issueId}`);
      if (del?.code !== 200) {
        console.warn(`⚠ 用例工作项 #${issueId} 未能删除（${del?.message || del?.code}），请手工清理`);
      }
    } catch (e) {
      console.warn(`⚠ 用例工作项 #${issueId} 删除请求失败：${e.message}`);
    }
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} 通过 ===`);
  if (failed.length) {
    console.log("失败项：\n" + failed.map((f) => ` - ${f.name}: ${f.detail}`).join("\n"));
    process.exitCode = 1;
  }
  console.log(`报告目录：${HERE}`);
}

main().catch((e) => {
  console.error("用例执行异常:", e);
  process.exitCode = 1;
});
