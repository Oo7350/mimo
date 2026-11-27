/**
 * Mimo 核心链路端到端用例（真实点击 + 接口断言）
 *
 * 对应 bug-log KNOWN-017：建任务 / 拖拽 / 评论 / 附件 / 验收 / Sprint 一直只有
 * "弹窗能打开"级别的验证，没有端到端闭环。这里把每条链路跑通并断言接口结果。
 *
 * 用法： node e2e/core-flows.mjs [baseUrl]
 * 依赖：后端 :8080、前端 :5173 已启动；PLAYWRIGHT_BROWSERS_PATH 指向已安装的浏览器。
 *       playwright 本身允许装在仓库外——设 PLAYWRIGHT_MODULE 指向它的包目录即可，
 *       不必为了跑用例往仓库 package.json 里塞依赖。
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require_ = createRequire(import.meta.url);

function loadPlaywright() {
  const candidates = [];
  if (process.env.PLAYWRIGHT_MODULE) candidates.push(process.env.PLAYWRIGHT_MODULE);
  candidates.push("playwright");
  const tried = [];
  for (const c of candidates) {
    try {
      return require_(c);
    } catch (e) {
      tried.push(`  - ${c} → ${e.code || e.message}`);
    }
  }
  console.error("找不到 playwright，试过：\n" + tried.join("\n"));
  console.error("解决：npm i playwright && npx playwright install chromium，");
  console.error("或把 PLAYWRIGHT_MODULE 指向已有安装，如 <dir>/node_modules/playwright");
  process.exit(2);
}

const { chromium } = loadPlaywright();

// 产物一律落在脚本同目录，从哪个 cwd 跑都不会弄脏仓库根目录
const HERE = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] || "http://localhost:5173";
const SHOTS = path.join(HERE, "shots-suite");
fs.mkdirSync(SHOTS, { recursive: true });

const results = [];
function rec(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();

const calls = [];
page.on("response", (res) => {
  const u = res.url();
  if (u.includes("/api/")) calls.push({ method: res.request().method(), url: u.replace(BASE, "").split("?")[0], status: res.status() });
});
const lastStatus = (method, fragment) => {
  const hit = [...calls].reverse().find((c) => c.method === method && c.url.includes(fragment));
  return hit ? hit.status : null;
};
const bump = () => calls.length;
const newFails = (from) => calls.slice(from).filter((c) => c.status >= 400);

async function login() {
  await page.goto(`${BASE}/login`, { waitUntil: "load" });
  await page.fill('input[placeholder="请输入用户名"]', "admin");
  await page.fill('input[placeholder="请输入密码"]', "123456");
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForTimeout(2200);
  // 这套用例测的是业务链路，不是引导：先把引导标记成"已看过"，
  // 否则进看板会自动弹新手引导，遮罩把后续点击全拦住（引导自身的验收在 e2e/tour.mjs）。
  await page.evaluate(() => {
    const done = { status: "done", v: 2, at: new Date().toISOString() };
    localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith("mimo_tour_session"))
      .forEach((k) => sessionStorage.removeItem(k));
  });
}

async function killTour() {
  // 引导是延迟触发的，先给一点时间再判断
  await page.waitForTimeout(600);
  // 优先点应用提供的「跳过引导」
  const skip = page.getByText("跳过引导").first();
  if (await skip.count()) {
    await skip.click({ force: true }).catch(() => {});
    await page.waitForTimeout(300);
  }
  // 兜底：driver.js 会在 body 上留 driver-active，它给全站加了 pointer-events: none，
  // 只删遮罩 DOM 不清这个类的话，后面所有点击/拖拽都会被吞掉。
  await page.evaluate(() => {
    document.querySelectorAll(".driver-overlay, .driver-popover").forEach((el) => el.remove());
    document.body.classList.remove("driver-active", "driver-fade");
  });
  await page.waitForTimeout(200);
}

async function openBoard() {
  await page.goto(`${BASE}/projects/1/board`, { waitUntil: "load" });
  await page.waitForTimeout(1800);
  await killTour();
}

/** 切 Element Plus 标签页（.el-tabs__item 比 getByRole('tab') 稳） */
async function clickTab(dialog, label) {
  const tab = dialog.locator(".el-tabs__item", { hasText: label }).first();
  await tab.waitFor({ state: "visible", timeout: 8000 });
  await tab.click({ force: true });
  await page.waitForTimeout(500);
}

const stamp = Date.now() % 100000;
const TITLE = `E2E任务${stamp}`;

await login();

// ---------- 1. 创建任务 ----------
let createdId = null;
try {
  await page.goto(`${BASE}/projects/1`, { waitUntil: "load" });
  await page.waitForTimeout(1800);
  const from = bump();
  await page.locator("#create-task-btn").click({ force: true });
  await page.waitForTimeout(1200);
  const dialog = page.locator(".el-dialog:visible").last();
  await dialog.waitFor({ state: "visible", timeout: 8000 });
  await dialog.locator('input[placeholder*="标题"]').first().fill(TITLE);
  await dialog.getByRole("button", { name: /^创\s*建$/ }).first().click({ force: true });
  await page.waitForTimeout(2200);
  const st = lastStatus("POST", "/api/issues");
  const visible = await page.locator(`text=${TITLE}`).count();
  const fails = newFails(from);
  rec("1.创建任务", st === 200 && visible > 0 && fails.length === 0, `POST /issues=${st} 卡片可见=${visible > 0} 4xx=${fails.length}`);
  // 取 issueId 供后续用例使用
  const board = await page.evaluate(async () => {
    const t = localStorage.getItem("token") || "";
    const r = await fetch("/api/board/1", { headers: { Authorization: "Bearer " + t } });
    return r.ok ? await r.json() : null;
  });
  if (board?.data?.columns) {
    const all = board.data.columns.flatMap((c) => c.issues);
    createdId = all.find((i) => i.title === TITLE)?.id ?? null;
  }
} catch (e) {
  rec("1.创建任务", false, String(e).slice(0, 200));
}
if (!createdId) {
  // 兜底：从数据库接口拿不到就用板上的第一个任务继续后面的用例
  createdId = await page.evaluate(async () => {
    const t = localStorage.getItem("token") || "";
    const r = await fetch("/api/board/1", { headers: { Authorization: "Bearer " + t } });
    const b = await r.json();
    return b?.data?.columns?.[0]?.issues?.[0]?.id ?? null;
  });
}

// ---------- 2. 拖拽到进行中（vuedraggable） ----------
try {
  await openBoard();
  const from = bump();
  const card = page.locator(`.issue-card-wrapper:has-text("${TITLE}")`).first();
  const target = page.locator(".board__column").nth(1);
  if (await card.count()) {
    // vuedraggable(SortableJS) 只认鼠标事件序列，dragTo 不触发，手工模拟拖拽
    const box = await card.boundingBox();
    const tbox = await target.boundingBox();
    if (!box || !tbox) throw new Error("拿不到卡片/列的位置");
    await page.mouse.move(box.x + box.width / 2, box.y + 12);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2 + 8, box.y + 20, { steps: 5 });
    await page.mouse.move(tbox.x + tbox.width / 2, tbox.y + 80, { steps: 20 });
    await page.waitForTimeout(300);
    await page.mouse.up();
    await page.waitForTimeout(2200);
    const st = lastStatus("PUT", "/api/board/issue/move");
    // 以接口结果为准：确认这个任务真的落到「进行中」且状态为 IN_PROGRESS
    const after = await page.evaluate(async (id) => {
      const t = localStorage.getItem("token") || "";
      const r = await fetch("/api/board/1", { headers: { Authorization: "Bearer " + t } });
      const b = await r.json();
      const col = b?.data?.columns?.find((c) => c.issues?.some((i) => i.id === id));
      const iss = b?.data?.columns?.flatMap((c) => c.issues || []).find((i) => i.id === id);
      return { column: col?.name || null, status: iss?.status || null, running: Boolean(iss?.inProgressStartedAt) };
    }, createdId);
    const fails = newFails(from);
    const ok = st === 200 && after.column === "进行中" && after.status === "IN_PROGRESS" && fails.length === 0;
    rec("2.拖拽进入进行中", ok,
      `PUT move=${st} 列=${after.column} status=${after.status} 计时中=${after.running} 4xx=${fails.length}`);
  } else {
    rec("2.拖拽进入进行中", false, "找不到刚创建的任务卡片");
  }
} catch (e) {
  await page.screenshot({ path: path.join(SHOTS, "2-drag.png") });
  rec("2.拖拽进入进行中", false, String(e).slice(0, 200));
}

// ---------- 3. 评论 ----------
try {
  // 用深链直接打开详情弹窗（点卡片会被 SortableJS 的 drag 逻辑吞掉 click）
  await page.goto(`${BASE}/projects/1/board?issue=${createdId}`, { waitUntil: "load" });
  await page.waitForTimeout(2000);
  await killTour();
  const from = bump();
  const dialog = page.locator(".el-dialog:visible").last();
  await dialog.waitFor({ state: "visible", timeout: 8000 });
  // Element Plus 的 tab 头是 .el-tabs__item，用文本定位比 getByRole 稳
  await clickTab(dialog, /评论/);
  await page.waitForTimeout(900);
  const textarea = page.locator('.el-textarea__inner[placeholder*="评论"]:visible').last();
  await textarea.waitFor({ state: "visible", timeout: 8000 });
  const content = `自动化评论 ${stamp}`;
  await textarea.fill(content);
  await page.getByRole("button", { name: /发送/ }).last().click({ force: true });
  await page.waitForTimeout(1800);
  const st = lastStatus("POST", "/api/comments");
  const shown = await page.locator(`text=${content}`).count();
  const fails = newFails(from);
  rec("3.发表评论", st === 200 && shown > 0 && fails.length === 0, `POST /comments=${st} 评论可见=${shown > 0} 4xx=${fails.length}`);
} catch (e) {
  await page.screenshot({ path: path.join(SHOTS, "3-comment.png") });
  rec("3.发表评论", false, String(e).slice(0, 200));
}

// ---------- 4. 上传附件 ----------
try {
  const from = bump();
  const dialog = page.locator(".el-dialog:visible").last();
  await clickTab(dialog, /附件/);
  await page.waitForTimeout(600);
  const file = path.join(HERE, `e2e-attachment-${stamp}.txt`);
  fs.writeFileSync(file, `Mimo E2E 附件 ${stamp}`);
  await dialog.locator('input[type="file"]').setInputFiles(file);
  await page.waitForTimeout(2200);
  const st = lastStatus("POST", "/api/attachments");
  const name = path.basename(file);
  const shown = await page.locator(`text=${name}`).count();
  const fails = newFails(from);
  rec("4.上传附件", (st === 200 || st === 201) && shown > 0 && fails.length === 0,
    `POST /attachments=${st} 文件名可见=${shown > 0} 4xx=${fails.length}`);
} catch (e) {
  await page.screenshot({ path: path.join(SHOTS, "4-attach.png") });
  rec("4.上传附件", false, String(e).slice(0, 200));
}

// ---------- 5. 提交验收 ----------
try {
  const from = bump();
  const dialog = page.locator(".el-dialog:visible").last();
  await clickTab(dialog, /验收/);
  await page.waitForTimeout(900);
  await page.getByRole("button", { name: /提交验收/ }).first().click({ force: true });
  await page.waitForTimeout(900);
  const box = page.locator(".el-dialog:visible").last();
  await box.locator('textarea[placeholder*="说明完成内容"]').last().fill("E2E 验收说明：已完成并通过自测");
  await box.getByRole("button", { name: /^提交验收$/ }).last().click({ force: true });
  await page.waitForTimeout(2000);
  const st = lastStatus("POST", "/api/issues/" + createdId + "/reviews") ?? lastStatus("POST", "/api/reviews");
  const fails = newFails(from);
  rec("5.提交验收", st === 200 && fails.length === 0, `POST reviews=${st} 4xx=${fails.length}`);
} catch (e) {
  await page.screenshot({ path: path.join(SHOTS, "5-review.png") });
  rec("5.提交验收", false, String(e).slice(0, 200));
}

// ---------- 6. 新建 Sprint ----------
try {
  await page.goto(`${BASE}/projects/1/sprints`, { waitUntil: "load" });
  // 别用固定 1800ms：整套连跑时 Vite 可能正在重编译，页面会比平时慢，
  // 慢一步的结果是"按钮点空了（或弹窗还没挂上就填了）→ POST 一个都没发出去"，
  // 报出来的却是 `POST /sprints=null`，看着像后端不认（v2.22.2 连跑踩过一次）。
  const newBtn = page.getByRole("button", { name: /新建 Sprint/ }).first();
  await newBtn.waitFor({ state: "visible", timeout: 20000 });
  const from = bump();
  await newBtn.click({ force: true });
  const dialog = page.locator(".el-dialog:visible").last();
  await dialog.waitFor({ state: "visible", timeout: 15000 });
  await dialog.locator('input[placeholder*="名称"], input[type="text"]').first().fill(`E2E迭代${stamp}`);
  // 起止日期
  const dates = dialog.locator('input[placeholder*="日期"], input[placeholder*="开始"], input[placeholder*="结束"]');
  const dateCount = await dates.count();
  if (dateCount >= 2) {
    await dates.nth(0).fill("2026-11-20");
    await page.keyboard.press("Enter");
    await dates.nth(1).fill("2026-12-04");
    await page.keyboard.press("Enter");
  }
  // 等真正的那个请求，而不是等 2200ms 再看记录
  await Promise.all([
    page
      .waitForResponse((r) => r.url().includes("/api/sprints") && r.request().method() === "POST", { timeout: 20000 })
      .catch(() => null),
    dialog.getByRole("button", { name: /确定|创建|保存/ }).last().click({ force: true }),
  ]);
  await page.waitForTimeout(600);
  const st = lastStatus("POST", "/api/sprints");
  const fails = newFails(from);
  let detail = `POST /sprints=${st} 4xx=${fails.length}`;
  if (!(st === 200 || st === 201)) {
    // 失败时把"卡在哪儿"说清楚：弹窗还开着多半是表单校验没过
    const stillOpen = (await page.locator(".el-dialog:visible").count()) > 0;
    const tips = await page.evaluate(() =>
      Array.from(document.querySelectorAll(".el-form-item__error"))
        .map((el) => (el.textContent || "").trim())
        .filter(Boolean)
        .slice(0, 3),
    );
    detail += ` 弹窗还开着=${stillOpen} 校验提示=${tips.join("；") || "无"}`;
  }
  rec("6.新建 Sprint", (st === 200 || st === 201) && fails.length === 0, detail);
} catch (e) {
  await page.screenshot({ path: path.join(SHOTS, "6-sprint.png") });
  rec("6.新建 Sprint", false, String(e).slice(0, 200));
}

await page.screenshot({ path: path.join(SHOTS, "final.png") });
await browser.close();

const bad = [...new Map(calls.filter((c) => c.status >= 400).map((c) => [c.method + c.url, c])).values()];
const md = [
  "# 核心链路 E2E 结果",
  "",
  `- 时间: ${new Date().toISOString()}`,
  `- 环境: ${BASE}`,
  "",
  "| 用例 | 结果 | 说明 |",
  "|---|---|---|",
  ...results.map((r) => `| ${r.name} | ${r.ok ? "✅ 通过" : "❌ 失败"} | ${String(r.detail || "").replace(/\|/g, "\\|")} |`),
  "",
  `通过 ${results.filter((r) => r.ok).length}/${results.length}`,
  "",
  "## 失败请求",
  "",
  ...(bad.length ? bad.map((c) => `- \`${c.method} ${c.url}\` → ${c.status}`) : ["（无）"]),
];
fs.writeFileSync(path.join(HERE, "suite-report.md"), md.join("\n"), "utf-8");
console.log(`\n通过 ${results.filter((r) => r.ok).length}/${results.length}`);
process.exit(results.every((r) => r.ok) ? 0 : 1);
