/**
 * 新手引导用例（真实浏览器）。
 *
 * 回归的是这一批问题（bug-log BUG-078~081）：
 * 1. 引导退出后在 body 上留 driver-active，全站 pointer-events: none —— 用户以为页面卡死
 * 2. 入口 `v-if="!tourDone"`，看过一次就永久消失，也没有"重新观看"
 * 3. 只有一个写死看板的 3 步引导，从别的页面触发也在讲看板列
 * 4. 跳过 = 完成，跳过的人再也看不到
 *
 * 用例：自动触发 → 跳过 → 页面还能点 → 入口仍在 → 能重播 → 错页面给提示 → Esc 收尾。
 *
 * 用法: node e2e/tour.mjs [baseUrl]
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require_ = createRequire(import.meta.url);
const HERE = path.dirname(fileURLToPath(import.meta.url));

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
  process.exit(2);
}

const { chromium } = loadPlaywright();

const BASE = process.argv[2] || "http://localhost:5173";
const SHOTS = path.join(HERE, "shots-suite");
fs.mkdirSync(SHOTS, { recursive: true });

const results = [];
function rec(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
}

const browser = await chromium.launch({ headless: true });
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();

const snap = () =>
  page.evaluate(() => ({
    popovers: document.querySelectorAll(".driver-popover").length,
    overlay: document.querySelectorAll(".driver-overlay").length,
    skip: document.querySelectorAll(".driver-skip-btn").length,
    bodyClass: document.body.className || "",
    progress: document.querySelector(".driver-popover-progress-text")?.textContent?.trim() || "",
    title: document.querySelector(".driver-popover-title")?.textContent?.trim() || "",
  }));

async function login() {
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.fill('input[placeholder="请输入用户名"]', "admin");
  await page.fill('input[placeholder="请输入密码"]', "123456");
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForTimeout(2200);
}

async function openTourMenu() {
  // Element Plus 的 dropdown 关闭后 DOM 还在，必须按 :visible 等真正展开的那一个，
  // 否则 force 点到隐藏项上等于没点（命令不会触发）。
  await page.keyboard.press("Escape").catch(() => {});
  await page.waitForTimeout(250);
  await page.locator(".mimo-header__user").click({ force: true });
  await page.waitForSelector(".el-dropdown-menu__item:visible", { timeout: 8000 });
  await page.waitForTimeout(300);
}

async function clickMenuItem(text) {
  await page.locator(`.el-dropdown-menu__item:visible`, { hasText: text }).first().click();
  await page.waitForTimeout(800);
}

try {
  await login();
  // 只清引导自己的标记：localStorage 里还有登录 token，整片 clear 会被踢回登录页
  await page.evaluate(() => {
    localStorage.removeItem("mimo_tour_state");
    localStorage.removeItem("mimo_tour_completed");
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith("mimo_tour_session"))
      .forEach((k) => sessionStorage.removeItem(k));
  });

  // ---- 1. 首次进看板自动触发，且步骤是完整的一套（≥5 步） ----
  await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("#create-task-btn", { timeout: 15000 });
  await page.waitForSelector(".driver-popover", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(600);
  let s = await snap();
  rec(
    "1.进看板自动出现引导",
    s.popovers === 1 && s.skip === 1 && /\/\s*\d+/.test(s.progress) && parseInt(s.progress.split("/")[1]) >= 5,
    `popover=${s.popovers} 进度=${s.progress || "(空)"} 标题=${s.title}`
  );

  // ---- 2. 点跳过：遮罩清干净、状态记为 skipped、页面恢复可点 ----
  await page.locator(".driver-skip-btn").first().click({ force: true });
  await page.waitForTimeout(800);
  s = await snap();
  const state = await page.evaluate(() => JSON.parse(localStorage.getItem("mimo_tour_state") || "{}"));
  rec(
    "2.跳过后无残留遮罩",
    s.popovers === 0 && s.overlay === 0 && !s.bodyClass.includes("driver-active"),
    `popover=${s.popovers} overlay=${s.overlay} body="${s.bodyClass}"`
  );
  rec(
    "3.跳过不算完成、仍可重看",
    state.board?.status === "skipped",
    `state.board=${JSON.stringify(state.board || null)}`
  );

  const clickable = await page
    .locator(".issue-card-wrapper")
    .first()
    .click({ timeout: 4000 })
    .then(() => true)
    .catch(() => false);
  if (clickable) await page.keyboard.press("Escape");
  await page.waitForTimeout(400);
  rec("4.跳过后页面仍可点击", clickable, `卡片可点=${clickable}`);

  // ---- 5. 入口常驻 + 能重播 ----
  await openTourMenu();
  const items = await page.locator(".el-dropdown-menu__item").allInnerTexts();
  const hasEntry = items.some((t) => t.includes("新手引导"));
  await page.keyboard.press("Escape");
  await page.locator("body").click({ position: { x: 5, y: 300 } }).catch(() => {});
  await page.waitForTimeout(300);
  rec("5.引导入口常驻（看过也能重看）", hasEntry, `菜单=${JSON.stringify(items)}`);

  await openTourMenu();
  await clickMenuItem("看板实操");
  await page.waitForTimeout(600);
  s = await snap();
  rec("6.从菜单重播看板引导", s.popovers === 1 && s.skip === 1, `popover=${s.popovers} 标题=${s.title}`);

  // ---- 7. Esc 收尾，body 不留类 ----
  await page.keyboard.press("Escape");
  await page.waitForTimeout(700);
  s = await snap();
  rec(
    "7.Esc 退出后清理干净",
    s.popovers === 0 && !s.bodyClass.includes("driver-active"),
    `popover=${s.popovers} body="${s.bodyClass}"`
  );

  // ---- 8. 在错的页面起看板引导：给提示而不是讲错位置 ----
  await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1500);
  await openTourMenu();
  await clickMenuItem("看板实操");
  await page.waitForTimeout(700);
  s = await snap();
  await page.waitForSelector(".el-message", { timeout: 6000 }).catch(() => {});
  const toast = (
    await page
      .locator(".el-message")
      .allInnerTexts()
      .catch(() => [])
  ).join(" | ");
  rec(
    "8.错页面启动看板引导只给提示",
    s.popovers === 0 && /需要在项目看板页启动/.test(toast),
    `popover=${s.popovers} toast=${toast.slice(0, 40)}`
  );

  // ---- 9. 首页跑「快速上手」能正常完成并记为 done ----
  await page.evaluate(() => {
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith("mimo_tour_session"))
      .forEach((k) => sessionStorage.removeItem(k));
  });
  await openTourMenu();
  await clickMenuItem("快速上手");
  await page.waitForTimeout(700);
  s = await snap();
  const started = s.popovers === 1;
  let guard = 0;
  while (guard++ < 12) {
    const nextBtn = page.locator(".driver-popover button", { hasText: /下一步|开始使用/ }).first();
    if (!(await nextBtn.count())) break;
    await nextBtn.click();
    await page.waitForTimeout(500);
  }
  await page.waitForTimeout(600);
  const after = await snap();
  const stateAfter = await page.evaluate(() => JSON.parse(localStorage.getItem("mimo_tour_state") || "{}"));
  rec(
    "9.走完引导记为完成",
    started && after.popovers === 0 && stateAfter.welcome?.status === "done",
    `启动=${started} 结束popover=${after.popovers} 状态=${stateAfter.welcome?.status || "(无)"}`
  );
} catch (e) {
  await page.screenshot({ path: path.join(SHOTS, "tour-fail.png") }).catch(() => {});
  rec("用例执行", false, String(e).slice(0, 200));
} finally {
  await page.screenshot({ path: path.join(SHOTS, "tour-final.png") }).catch(() => {});
  await browser.close();
}

const passed = results.filter((r) => r.ok).length;
console.log(`\n通过 ${passed}/${results.length}`);
process.exit(passed === results.length ? 0 : 1);
