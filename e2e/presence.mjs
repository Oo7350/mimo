/**
 * 在线状态验证（KNOWN-013）：浏览器真的连了 WebSocket，团队页就该显示"1 人在线"。
 *
 * 这条针对的是一句曾经撒谎的文案：
 *   以前 `onlineCount = members.length`，写的是「N 位成员」却在"在线"位置显示，等于编数字。
 * 现在 /api/teams/{id}/presence 返回真实在线数，这里断言：
 *   1. 打开页面（WebSocket 连上）后，接口里 onlineCount ≥ 1
 *   2. 页面上那行字是"N 人在线 · M 位成员"，两个数都在
 *   3. 断开页面后，在线数回落到 0（不留下假在线）
 *
 * 跑法：node e2e/presence.mjs http://localhost:5173
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const BASE = process.argv[2] || "http://localhost:5173";
const ADMIN = { username: "admin", password: "123456" };
const TEAM_ID = 1;

let passed = 0;
let failed = 0;

function check(name, ok, detail) {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
  if (ok) passed++;
  else failed++;
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(() => {
  const done = { status: "done", v: 2, at: new Date().toISOString() };
  localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
});
const page = await context.newPage();
page.setDefaultTimeout(25000);

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
await page.getByPlaceholder("请输入用户名").fill(ADMIN.username);
await page.getByPlaceholder("请输入密码").fill(ADMIN.password);
await page.getByRole("button", { name: /登\s*录/ }).click();
await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });

// 等 WebSocket 真连上（前端 useWebSocket 在布局层建立连接）
await page.waitForTimeout(3000);

const token = await page.evaluate(() => localStorage.getItem("token") || localStorage.getItem("mimo_token"));
const apiPresence = async () => {
  const res = await page.evaluate(async (t) => {
    const r = await fetch("/api/teams/1/presence", { headers: { Authorization: `Bearer ${t}` } });
    return r.ok ? await r.json() : { error: r.status };
  }, token);
  return res.data;
};

const withSocket = await apiPresence();
check(
  "浏览器连着 WebSocket 时，在线人数至少 1",
  (withSocket?.onlineCount ?? 0) >= 1,
  `onlineCount=${withSocket?.onlineCount}, totalMembers=${withSocket?.totalMembers}`
);

check(
  "在线人数不会超过成员总数",
  (withSocket?.onlineCount ?? 0) <= (withSocket?.totalMembers ?? 0),
  `online=${withSocket?.onlineCount} / total=${withSocket?.totalMembers}`
);

// 页面上那句话必须两个数都在
await page.goto(`${BASE}/teams/${TEAM_ID}`, { waitUntil: "domcontentloaded" });
// 首屏可能先查到 0（WS 还没连上），等补刷那两次跑完再看
await page.waitForTimeout(9000);
const label = await page.evaluate(() => {
  const el = document.querySelector(".td-chat-header__online");
  return el ? el.textContent.trim() : null;
});
check(
  "团队页文案是「N 人在线 · M 位成员」",
  Boolean(label && /\d+\s*人在线/.test(label) && /\d+\s*位成员/.test(label)),
  `文案=${label}`
);

// 断开连接后在线数回落（不留假在线）
await page.close();
await context.close();
await browser.close();

const probe = await chromium.launch({ headless: true });
const ctx2 = await probe.newContext();
const p2 = await ctx2.newPage();
await p2.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
const readAfter = async () => p2.evaluate(async () => {
  const r = await fetch("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "admin", password: "123456" }),
  });
  const login = await r.json();
  const res = await fetch("/api/teams/1/presence", {
    headers: { Authorization: `Bearer ${login.data.token}` },
  });
  const j = await res.json();
  return j.data;
});


// 服务端发现断开有两条路：浏览器正常关闭 SockJS 会发 DISCONNECT 帧（快），
// 进程被杀/网络断只能等心跳超时（慢，最迟一个心跳周期 10 秒 + 调度延迟）。
// 所以这里先做一次"优雅离开"（导航到 about:blank，让前端主动断开），再最多等 40 秒。
await p2.waitForTimeout(500);
const startedAt = Date.now();
let after = null;
for (let i = 0; i < 20; i++) {
  after = await readAfter();
  if ((after?.onlineCount ?? 1) === 0) break;
  await p2.waitForTimeout(2000);
}
const elapsed = Math.round((Date.now() - startedAt) / 100) / 10;
check(
  "关掉页面后在线人数回落，不留假在线",
  (after?.onlineCount ?? 1) === 0,
  `onlineCount=${after?.onlineCount}，耗时 ${elapsed}s（心跳周期 10s）`
);

await ctx2.close();
await probe.close();

console.log(`\n=== ${passed}/${passed + failed} 通过 ===`);
if (failed > 0) process.exitCode = 1;
