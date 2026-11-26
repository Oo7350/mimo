/**
 * 在线状态验证（KNOWN-013）：浏览器真的连了 WebSocket，团队页就该显示"1 人在线"。
 *
 * 这条针对的是一句曾经撒谎的文案：
 *   以前 `onlineCount = members.length`，写的是「N 位成员」却在"在线"位置显示，等于编数字。
 * 现在 /api/teams/{id}/presence 返回真实在线数，这里断言：
 *   1. 打开页面（WebSocket 连上）后，在线人数比开页前多（这一页真的被算进去）
 *   2. 页面上那行字是"N 人在线 · M 位成员"，两个数都在
 *   3. 断开页面后，在线数回落到开页前的水位（不留下假在线）
 *
 * 注意第 3 条比的是**基线**而不是 0：开发机上常有人真开着页面（自己边改边看），
 * 写死等 0 只会让用例变脆——那不是产品留了假在线，是别人的正常会话。
 *
 * 跑法：node e2e/presence.mjs http://localhost:5173
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const BASE = process.argv[2] || "http://localhost:5173";
const API_BASE = process.env.MIMO_API || "http://localhost:8080/api";
const ADMIN = { username: "admin", password: "123456" };
const TEAM_ID = 1;

let passed = 0;
let failed = 0;

function check(name, ok, detail) {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
  if (ok) passed++;
  else failed++;
}

/**
 * 先量一个基线：不连 WebSocket 的纯接口调用，看看**别人**有多少人在线。
 *
 * 这里踩过两个坑（都不是产品问题）：
 *   1. 原来断言写死「关页后 onlineCount === 0」，但开发机上常有人真开着页面，永远回不到 0；
 *   2. 换成"比基线多 1"也不行——在线人数是按**人**去重的（同一账号开两个标签页只算一人），
 *      而我们默认用 admin 登录，如果本机已经有一个 admin 会话，再开一页人数根本不变。
 * 所以这一套改用**现场注册的独立账号**：它不开页时为 0，开页 +1，关页回到原水位，无歧义。
 */
const STAMP = Date.now().toString().slice(-6);
const PROBE_USER = `e2e-presence${STAMP}`;

const apiLogin = async (creds) => {
  const r = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  const j = await r.json();
  return j?.data?.token;
};
const readPresenceNode = async () => {
  const token = await apiLogin(ADMIN);
  const r = await fetch(`${API_BASE}/teams/${TEAM_ID}/presence`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const j = await r.json();
  return j.data;
};

// 注册一个纯成员账号并入队，专门用来"开一个页面、关掉、看人数"（cleanup-residue.sql 按 e2e-% 回收）
let probeId = null;
{
  const reg = await fetch(`${API_BASE}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: PROBE_USER, password: "123456", email: `${PROBE_USER}@example.com` }),
  }).then((r) => r.json());
  if (reg?.code !== 200) throw new Error("注册探针账号失败: " + JSON.stringify(reg).slice(0, 160));
  const adminToken = await apiLogin(ADMIN);
  const found = await fetch(`${API_BASE}/user/search?keyword=${encodeURIComponent(PROBE_USER)}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  }).then((r) => r.json());
  probeId = (found?.data || []).find((u) => u.username === PROBE_USER)?.id;
  if (!probeId) throw new Error("找不到刚注册的探针账号");
  const invite = await fetch(`${API_BASE}/teams/invite`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${adminToken}` },
    body: JSON.stringify({ teamId: TEAM_ID, userId: probeId, role: "ROLE_MEMBER" }),
  }).then((r) => r.json());
  if (invite?.code !== 200) throw new Error("探针账号入队失败: " + JSON.stringify(invite).slice(0, 160));
}

const baseline = await readPresenceNode();
const baselineCount = baseline?.onlineCount ?? 0;
console.log(`基线：开页面前已有 ${baselineCount} 人在线（别人的会话不算我们漏的）`);

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(() => {
  const done = { status: "done", v: 2, at: new Date().toISOString() };
  localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
});
const page = await context.newPage();
page.setDefaultTimeout(25000);

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
// 用探针账号登录：它在开页前一定不在线，所以"开页 +1、关页回原样"是无歧义的
await page.getByPlaceholder("请输入用户名").fill(PROBE_USER);
await page.getByPlaceholder("请输入密码").fill("123456");
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

// 等 WebSocket 真连上：不信固定 3 秒，直接等自己的 id 出现在在线名单里（最多 25 秒）。
// 连跑十几套时前端首屏编译会明显变慢，写死等待要么白等、要么假红。
let withSocket = await apiPresence();
const connectStartedAt = Date.now();
for (let i = 0; i < 25; i++) {
  if ((withSocket?.onlineUserIds || []).includes(probeId)) break;
  await page.waitForTimeout(1000);
  withSocket = await apiPresence();
}
const connectMs = Date.now() - connectStartedAt;
const onlineIds = withSocket?.onlineUserIds || [];
check(
  "浏览器连着 WebSocket 时，这一页真的被算进在线名单",
  onlineIds.includes(probeId),
  `ids=${JSON.stringify(onlineIds)}（自己=${probeId}，基线 ${baselineCount} 人）, 连上耗时 ${connectMs}ms`
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
//
// 判定用"自己的 id 是否还在名单里"，不比总人数：连跑十几套时，
// 前面那些套件的浏览器会话正在陆续超时下线，总数会一边加一边减
// ——比总数就会得到"我加了一人、别人掉了一人、总数没变"的假红（v2.22.2 真踩过）。
await p2.waitForTimeout(500);
const startedAt = Date.now();
const openIds = withSocket?.onlineUserIds || [];
let after = null;
for (let i = 0; i < 20; i++) {
  after = await readAfter();
  if (!(after?.onlineUserIds || []).includes(probeId)) break;
  await p2.waitForTimeout(2000);
}
const elapsed = Math.round((Date.now() - startedAt) / 100) / 10;
check(
  "关掉页面后自己从在线名单里消失，不留假在线",
  openIds.includes(probeId) && !(after?.onlineUserIds || []).includes(probeId),
  `ids=${JSON.stringify(after?.onlineUserIds)}（自己=${probeId} 已离开），耗时 ${elapsed}s（心跳周期 10s）`
);
await ctx2.close();
await probe.close();

console.log(`\n=== ${passed}/${passed + failed} 通过 ===`);
if (failed > 0) process.exitCode = 1;
