/**
 * 在线人数的"口径"是否写在界面上（KNOWN-002 的 UI 半边）。
 *
 * 背景：只把在线数字做准还不够。多实例部署又没接 Redis 时，后端只知道本实例的连接，
 * 那个数字**只对一台机器成立**；如果界面照样写「1 人在线」，用户会读成"其他人都下线了"。
 * 所以后端现在多返回 presenceStore / presenceShared / presenceNote，界面必须显示出来。
 *
 * 这个脚本用拦截接口的方式覆盖两种口径，不需要真的再起一台后端：
 *   A. presenceShared=false → 必须出现「（仅本实例）」+ 说明图标
 *   B. presenceShared=true  → 不许出现「（仅本实例）」
 *
 * 用法：node e2e/presence-source-label.mjs [http://localhost:5173]
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
// 只认环境变量，退回到包名——不要写死某台机器上的绝对路径：CI 里那条路径不存在，
// 报错会变成看不懂的 MODULE_NOT_FOUND（v2.22.2 跨平台体检时改掉）。
const playwrightPath = process.env.PLAYWRIGHT_MODULE || "playwright";
const { chromium } = require(playwrightPath);

const BASE = process.argv[2] || "http://localhost:5173";
const ACCOUNT = { username: "admin", password: "123456" };

const passed = [];
const failed = [];
function check(name, ok, detail = "") {
  (ok ? passed : failed).push(name);
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
}

const HEADER = ".td-chat-header__online";

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded", timeout: 45000 });
  await page.getByPlaceholder("请输入用户名").fill(ACCOUNT.username);
  await page.getByPlaceholder("请输入密码").fill(ACCOUNT.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 30000 });
}

async function openFirstTeam(page) {
  await page.goto(`${BASE}/teams`, { waitUntil: "domcontentloaded", timeout: 45000 });
  const card = page.locator(".team-card, .td-card, .team-item").first();
  await card.waitFor({ timeout: 20000 });
  await card.click();
  await page.waitForSelector(HEADER, { timeout: 20000 });
}

/** 拦截 presence 接口，返回指定口径；同时把团队 id 换成真实值 */
async function stubPresence(page, { shared, store }) {
  await page.route("**/api/teams/*/presence", async (route) => {
    const teamId = Number(route.request().url().match(/teams\/(\d+)\/presence/)?.[1] || 0);
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: 200,
        message: "ok",
        data: {
          teamId,
          onlineCount: 2,
          totalMembers: 5,
          onlineUserIds: [1, 2],
          presenceStore: store,
          presenceShared: shared,
          presenceNote: shared
            ? "在线状态由 Redis 共享，多实例部署下同样准确"
            : "在线状态只统计本实例，多实例部署时其他人的在线情况可能看不到",
        },
      }),
    });
  });
}

async function textOf(page) {
  return (await page.locator(HEADER).innerText()).replace(/\s+/g, " ").trim();
}

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  // 关掉新手引导，否则遮罩会拦下点击（与其它套件同一套 key / 版本号）
  await context.addInitScript(() => {
    const done = { status: "done", v: 2, at: new Date().toISOString() };
    localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
  });
  const page = await context.newPage();
  page.setDefaultTimeout(25000);

  await login(page);
  await stubPresence(page, { shared: false, store: "MEMORY" });
  await openFirstTeam(page);
  await page.waitForTimeout(1200);

  const localText = await textOf(page);
  check("本实例口径时写明「（仅本实例）」", localText.includes("（仅本实例）"), localText);
  check("本实例口径时仍显示两个数字（在线 / 成员总数）",
    /\d+\s*人在线/.test(localText) && /\d+\s*位成员/.test(localText), localText);

  const hintCount = await page.locator(".td-chat-header__hint").count();
  check("本实例口径时有说明图标（hover 给出原因）", hintCount === 1, `count=${hintCount}`);

  // 换口径：共享时不该再出现"仅本实例"
  await page.unroute("**/api/teams/*/presence");
  await stubPresence(page, { shared: true, store: "REDIS" });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForSelector(HEADER, { timeout: 20000 });
  await page.waitForTimeout(1200);

  const sharedText = await textOf(page);
  check("共享口径时不写「（仅本实例）」", !sharedText.includes("仅本实例"), sharedText);
  check("共享口径时数字照样显示",
    /\d+\s*人在线/.test(sharedText) && /\d+\s*位成员/.test(sharedText), sharedText);
  const hintShared = await page.locator(".td-chat-header__hint").count();
  check("共享口径时没有说明图标", hintShared === 0, `count=${hintShared}`);

  await browser.close();

  console.log(`\n=== ${passed.length}/${passed.length + failed.length} 通过 ===`);
  if (failed.length) {
    console.log("失败项：" + failed.join(" / "));
    process.exit(1);
  }
}

main().catch((error) => {
  console.error("执行异常：", error.message);
  process.exit(1);
});
