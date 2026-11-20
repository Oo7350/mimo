/**
 * AI 降级（未配置 Key）的浏览器验证：
 * 1) 打开 AI 面板能看到"未配置"提示条，而不是让用户点一次失败一次；
 * 2) 发一条消息，气泡里是给人看的中文说明，不是异常堆栈 / 上游原文；
 * 3) 任务详情「AI 润色」失败时只弹错误，不改写描述框里的正文。
 *
 * 用法: node e2e/ai-degrade.mjs [baseUrl]
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
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();

async function killTour() {
  const skip = page.getByText("跳过引导").first();
  if (await skip.count()) {
    await skip.click({ force: true }).catch(() => {});
    await page.waitForTimeout(300);
  }
  await page.evaluate(() => {
    document.querySelectorAll(".driver-overlay, .driver-popover").forEach((el) => el.remove());
    document.body.classList.remove("driver-active", "driver-fade");
  });
}

async function login() {
  await page.goto(`${BASE}/login`, { waitUntil: "load" });
  await page.fill('input[placeholder="请输入用户名"]', "admin");
  await page.fill('input[placeholder="请输入密码"]', "123456");
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForTimeout(2200);
  // driver.js 的 driver-active 会给全站加 pointer-events: none
  await killTour();
}

try {
  await login();

  // ---- 用例 1：面板顶部提示条 ----
  try {
    await page.locator(".ai-chat-panel__trigger").click({ force: true });
    await page.waitForSelector(".ai-chat-panel__body", { timeout: 8000 });
    const notice = page.locator(".ai-chat-panel__notice");
    const count = await notice.count();
    const text = count ? (await notice.first().innerText()).trim() : "";
    rec(
      "1.AI 未配置提示条",
      count === 1 && text.includes("未配置"),
      `count=${count} text=${text.slice(0, 40)}`
    );
  } catch (e) {
    await page.screenshot({ path: path.join(SHOTS, "ai-1-notice.png") });
    rec("1.AI 未配置提示条", false, String(e).slice(0, 160));
  }

  // ---- 用例 2：发消息得到中文说明 ----
  try {
    const box = page.locator(".ai-chat-panel__textarea textarea, .ai-chat-panel__input textarea").first();
    await box.fill("你好");
    await box.press("Enter");
    await page.waitForTimeout(3000);
    const bubbles = page.locator(".ai-chat-panel__msg--assistant .ai-chat-panel__msg-bubble");
    const n = await bubbles.count();
    const reply = n ? (await bubbles.last().innerText()).trim() : "";
    const friendly = reply.includes("未配置") || reply.includes("不可用");
    const leaked = /Exception|java\.|at com\.|DEEPSEEK_API_KEY 环境变量/.test(reply);
    rec("2.对话返回友好说明", n > 0 && friendly && !leaked, `bubbles=${n} reply=${reply.slice(0, 50)}`);
  } catch (e) {
    await page.screenshot({ path: path.join(SHOTS, "ai-2-chat.png") });
    rec("2.对话返回友好说明", false, String(e).slice(0, 160));
  }

  // ---- 用例 3：润色失败不改写描述 ----
  try {
    await page.goto(`${BASE}/projects/1/board`, { waitUntil: "load" });
    await page.waitForTimeout(2500);
    await killTour();
    const card = page.locator(".issue-card-wrapper").first();
    await card.click();
    await page.waitForSelector(".el-dialog", { timeout: 8000 });
    await page.waitForTimeout(800);

    // 切到「基本信息」页签（描述框在这里）
    const detailTab = page.locator(".el-dialog .el-tabs__item", { hasText: "基本信息" }).first();
    if (await detailTab.count()) await detailTab.dispatchEvent("click");
    await page.waitForTimeout(500);

    const desc = page.locator(".el-dialog textarea").first();
    // 描述为空时润色按钮是禁用的，先填一段草稿
    await desc.fill("做个登录 白屏了 看下");
    const before = await desc.inputValue();
    // 点「AI 润色描述」——未配置 Key 时应只弹错误提示
    const polishBtn = page.locator(".el-dialog button", { hasText: "润色" }).first();
    if (!(await polishBtn.count())) throw new Error("找不到「润色」按钮");
    await polishBtn.click();
    await page.waitForTimeout(2500);

    const after = await desc.inputValue();
    const preview = await page.locator(".ai-polish-preview").count();
    const toast = await page.locator(".el-message").first().innerText().catch(() => "");
    rec(
      "3.润色失败不改写正文",
      before === after && preview === 0 && /未配置|不可用/.test(toast),
      `unchanged=${before === after} preview=${preview} toast=${toast.slice(0, 40)}`
    );
  } catch (e) {
    await page.screenshot({ path: path.join(SHOTS, "ai-3-polish.png") });
    rec("3.润色失败不改写正文", false, String(e).slice(0, 160));
  }
} finally {
  await page.screenshot({ path: path.join(SHOTS, "ai-final.png") });
  await browser.close();
}

const passed = results.filter((r) => r.ok).length;
console.log(`\n通过 ${passed}/${results.length}`);
process.exit(passed === results.length ? 0 : 1);
