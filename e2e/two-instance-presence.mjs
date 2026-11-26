/**
 * 双实例验证（KNOWN-002）：在线状态是否跨实例共享、实时消息能否跨实例送达。
 *
 * 前提：
 *   - 两个后端都指向同一个 MySQL 和同一个 Redis（Redis 在本机 6379）
 *   - A 实例 :8080，B 实例 :8081
 *
 * 验证三件事：
 *   1. 浏览器连 B 实例的 WebSocket → 问 A 实例的 presence 接口，应该看到这个人（跨实例在线）；
 *   2. 通过 A 实例发团队聊天 → 连在 B 实例上的 STOMP 客户端能收到（跨实例广播）；
 *   3. 用 --local 跑同样的事（Redis 断开/进程内模式）→ 第 1 条应当**看不到**，证明测的是真共享而不是巧合。
 *
 * 用法：
 *   node e2e/two-instance-presence.mjs
 *   node e2e/two-instance-presence.mjs --local     # 只在进程内模式下跑基线
 */
import { createRequire } from "node:module";
import http from "node:http";

const FRONTEND_MODULES = "C:/Users/小晟/Desktop/AItest/mimo/frontend/node_modules";
const require = createRequire(import.meta.url);

let Client, SockJS;
try {
  ({ Client } = require(`${FRONTEND_MODULES}/@stomp/stompjs`));
  SockJS = require(`${FRONTEND_MODULES}/sockjs-client`);
} catch (error) {
  console.error("缺少 STOMP 客户端依赖：", error.message);
  process.exit(2);
}

// sockjs-client 在 Node 里需要全局 WebSocket 兜底（它默认用浏览器实现）
if (typeof globalThis.WebSocket === "undefined") {
  try {
    globalThis.WebSocket = require(`${FRONTEND_MODULES}/ws`);
  } catch {
    /* 前端依赖里没有 ws 也允许：SockJS 可以走 xhr-streaming 传输 */
  }
}

const API_A = process.env.API_A || "http://localhost:8080/api";
const API_B = process.env.API_B || "http://localhost:8081/api";
const WS_B = process.env.WS_B || "http://localhost:8081/api/ws";
const LOCAL_ONLY = process.argv.includes("--local");

const passed = [];
const failed = [];

function check(name, ok, detail = "") {
  if (ok) {
    passed.push(name);
    console.log(`PASS  ${name}${detail ? " — " + detail : ""}`);
  } else {
    failed.push(name);
    console.log(`FAIL  ${name}${detail ? " — " + detail : ""}`);
  }
}

function request(base, path, { method = "GET", token, body } = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(base + path);
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname + url.search,
        method,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(data ? { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) } : {}),
        },
      },
      (res) => {
        let raw = "";
        res.on("data", (chunk) => (raw += chunk));
        res.on("end", () => {
          try {
            resolve(JSON.parse(raw));
          } catch {
            resolve({ raw });
          }
        });
      },
    );
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}

async function login(base, username, password) {
  const res = await request(base, "/auth/login", { method: "POST", body: { username, password } });
  if (!res || !res.data || !res.data.token) {
    throw new Error(`登录失败（${base}）：${JSON.stringify(res).slice(0, 200)}`);
  }
  return res.data;
}

/** 用 STOMP over SockJS 连上某个实例，返回 { client, connected } */
function connectStomp(url, token, onMessage) {
  return new Promise((resolve, reject) => {
    const client = new Client({
      webSocketFactory: () => new SockJS(url),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      debug: () => {},
      onConnect: () => resolve({ client, onMessage }),
      onStompError: (frame) => reject(new Error("STOMP 错误：" + frame.headers.message)),
      onWebSocketError: (e) => reject(new Error("WebSocket 错误：" + (e?.message || e))),
    });
    client.activate();
    setTimeout(() => reject(new Error("STOMP 连接超时（10 秒）")), 10000);
  });
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 服务端 ChatEvent 的形状是 {type, teamId, message:{content,...}} */
function contentOf(event) {
  if (!event) return "";
  return String(event.message?.content ?? event.content ?? JSON.stringify(event));
}

async function main() {
  const admin = await login(API_A, "admin", "123456");
  const lisi = await login(API_B, "lisi", "123456");
  const tokenA = admin.token;
  const tokenB = lisi.token;

  const meB = await request(API_B, "/user/profile", { token: tokenB });
  const userB = meB.data;
  console.log(`账号：B 实例登录 ${userB.username}(id=${userB.id})`);

  const teams = await request(API_A, "/teams/my", { token: tokenA });
  const teamId = (teams.data && teams.data[0] && teams.data[0].id) || 1;

  // 基线：还没连 WebSocket 时，A 看不清是不是在线（不需要断言具体值）
  const before = await request(API_A, `/teams/${teamId}/presence`, { token: tokenA });
  console.log(
    `连之前：A 看到在线 ${before.data.onlineCount} 人，存储=${before.data.presenceStore}，跨实例=${before.data.presenceShared}`,
  );
  check(
    "接口把在线状态来源说明白了（presenceStore / presenceShared / presenceNote）",
    typeof before.data.presenceStore === "string" &&
      typeof before.data.presenceShared === "boolean" &&
      typeof before.data.presenceNote === "string",
    `store=${before.data.presenceStore} shared=${before.data.presenceShared}`,
  );

  // B 实例上建立 STOMP 连接（模拟"人连在另一台机器上"）
  let received = null;
  const conn = await connectStomp(WS_B, tokenB, (payload) => {
    received = payload;
  });
  conn.client.subscribe(`/topic/team-chat/${teamId}`, (frame) => {
    received = JSON.parse(frame.body);
  });
  await sleep(1200);

  const afterB = await request(API_A, `/teams/${teamId}/presence`, { token: tokenA });
  const seesB = (afterB.data.onlineUserIds || []).includes(Number(userB.id));
  console.log(
    `连上 B 之后：A 看到在线 ${afterB.data.onlineCount} 人，包含 ${userB.username} = ${seesB}`,
  );

  if (LOCAL_ONLY) {
    // 进程内模式的基线：A 看不见连在 B 上的这个人，这正是改造前的行为
    check("进程内模式：A 实例看不到连在 B 实例上的人（改造前的样子）", !seesB,
      `onlineUserIds=${JSON.stringify(afterB.data.onlineUserIds)}`);
  } else {
    check("Redis 模式：连在 B 实例上的用户，A 实例的在线列表里也有（跨实例共享）", seesB,
      `onlineUserIds=${JSON.stringify(afterB.data.onlineUserIds)}`);
  }

  // 跨实例消息：通过 A 实例发一条团队聊天，看连在 B 上的客户端能不能收到
  const sent = await request(API_A, "/chat/send", {
    method: "POST",
    token: tokenA,
    body: { teamId, content: `跨实例验证 ${new Date().toISOString()}`, type: "TEXT" },
  });
  check("A 实例发团队聊天接口返回成功", sent.code === 200 || sent.code === 0, `code=${sent.code}`);

  await sleep(1500);
  const gotIt = contentOf(received).includes("跨实例验证");
  if (LOCAL_ONLY) {
    check("进程内模式：连在 B 上的客户端收不到 A 发的消息（改造前的样子）", !gotIt,
      gotIt ? `收到=${contentOf(received)}` : "未收到（符合预期）");
  } else {
    check("Redis 模式：连在 B 上的客户端收到了 A 实例发出的消息（跨实例广播）", gotIt,
      gotIt ? `收到=${contentOf(received)}` : `未收到（原始帧=${JSON.stringify(received)}）`);
  }

  // 断开后 A 是否在合理时间内不再认为他在线（Redis 靠 DISCONNECT 立即清）
  conn.client.deactivate();
  await sleep(1500);
  const afterClose = await request(API_A, `/teams/${teamId}/presence`, { token: tokenA });
  const stillSees = (afterClose.data.onlineUserIds || []).includes(Number(userB.id));
  check("断开后 A 实例不再把该用户算作在线", !stillSees,
    `onlineCount=${afterClose.data.onlineCount}`);

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
