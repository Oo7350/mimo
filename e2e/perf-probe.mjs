/**
 * 性能探针（v2.22.4）
 *
 * 干什么：对核心读接口打 N 次，给出 p50 / p95 / max，按 p95 倒序列表。
 * 为什么要它：在此之前"性能"这件事只有推理没有数字——开发库常年是 3 个用户 20 个工作项，
 * 大数量下哪个接口先塌，谁也不知道。配套数据用 `tools/seed-load.sql` 造
 * （100 用户 / 5 项目 / 500 工作项 / 5000 活动日志），压完 `tools/cleanup-load.sql` 删掉。
 *
 * 用法：
 *   node e2e/perf-probe.mjs                       # 默认 5 轮，量全部接口
 *   node e2e/perf-probe.mjs --iters 20            # 多轮取 p95 更稳
 *   node e2e/perf-probe.mjs --only board,export   # 只量名字匹配的
 *   node e2e/perf-probe.mjs --budget 800          # 任一 p95 超 800ms 就退出码 1（默认不设门槛）
 *
 * 环境变量：MIMO_API（默认 http://localhost:8080/api）、MIMO_PERF_USER / MIMO_PERF_PASSWORD
 * 说明：这是**探针**不是用例——**慢不报错**（不设门槛），但"接口没跑通 / 登录失败 / --only 没匹配"
 * 会退出码非 0。第一版连跑挂都退出 0，等于在 CI 里插了一个装样子的步骤（BUG-130 顺带发现的）。
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
void require; // 不需要浏览器；保留是为了和别的探针长得一样

const API = process.env.MIMO_API || "http://localhost:8080/api";
const USER = process.env.MIMO_PERF_USER || "admin";
const PASS = process.env.MIMO_PERF_PASSWORD || "123456";

const argv = process.argv.slice(2);
const argOf = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback;
};
const ITERS = Number(argOf("iters", "5"));
const BUDGET = Number(argOf("budget", "0"));
const ONLY = argOf("only", "");

const stamp = Date.now();

async function api(path, token, options = {}) {
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });
  const text = await res.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  return { status: res.status, body };
}

async function login() {
  const r = await api("/auth/login", null, {
    method: "POST",
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (r.status !== 200 || !r.body?.data?.token) {
    console.error(`登录失败：HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    process.exit(2);
  }
  return r.body.data.token;
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];

async function measure(label, path, token) {
  const times = [];
  let status = 0;
  let size = 0;
  for (let i = 0; i < ITERS; i += 1) {
    const t0 = performance.now();
    const r = await api(path, token);
    const t1 = performance.now();
    status = r.status;
    size = JSON.stringify(r.body ?? null).length;
    if (r.status !== 200) break;
    times.push(Math.round(t1 - t0));
  }
  if (!times.length) return { label, path, status, ok: false, p50: 0, p95: 0, max: 0, size };
  const sorted = [...times].sort((a, b) => a - b);
  return {
    label,
    path,
    status,
    ok: true,
    p50: pct(sorted, 50),
    p95: pct(sorted, 95),
    max: sorted[sorted.length - 1],
    size,
  };
}

const token = await login();

// 找一套压测数据；没有就退回到演示数据，至少能跑出个基线
const teams = await api("/teams/my", token);
const teamList = teams.body?.data || [];
const loadTeam = teamList.find((t) => t.name === "压测团队") || teamList[0];
const projects = loadTeam ? await api(`/projects/team/${loadTeam.id}`, token) : { body: { data: [] } };
const projectList = projects.body?.data || [];
const loadProject = projectList.find((p) => String(p.key).startsWith("LOAD")) || projectList[0];
const projectId = loadProject?.id || 1;
const teamId = loadTeam?.id || 1;

const issuesProbe = await api(`/board/${projectId}`, token);
const someIssueId =
  issuesProbe.body?.data?.columns?.flatMap?.((c) => c.issues || [])?.[0]?.id || null;
const someSprintId = (await api(`/sprints/project/${projectId}`, token)).body?.data?.[0]?.id || null;

const targets = [
  { label: "看板（列+全部卡片）", path: `/board/${projectId}` },
  { label: "全员任务流（分页 8 人）", path: "/workbench/team?memberPage=1&pageSize=8" },
  { label: "我的工作台", path: "/workbench/my" },
  { label: "团队成员列表", path: `/teams/${teamId}/members` },
  { label: "在线人数", path: `/teams/${teamId}/presence` },
  { label: "项目列表（团队）", path: `/projects/team/${teamId}` },
  { label: "项目统计卡片", path: `/reports/stats/${projectId}` },
  { label: "速率图（velocity）", path: `/analytics/project/${projectId}/velocity` },
  { label: "累积流图（CFD）", path: `/analytics/project/${projectId}/cfd` },
  { label: "甘特图", path: `/gantt/project/${projectId}` },
  { label: "工作流配置", path: `/workflow/project/${projectId}/type/STORY` },
  { label: "用户搜索（100 命中）", path: "/user/search?keyword=load" },
  { label: "未读通知数", path: "/notifications/unread-count" },
  ...(someIssueId
    ? [
        { label: "工作项详情", path: `/issues/${someIssueId}` },
        { label: "评论列表", path: `/comments/issue/${someIssueId}` },
        { label: "附件列表", path: `/attachments/issue/${someIssueId}` },
      ]
    : []),
  ...(someSprintId
    ? [
        { label: "燃尽图", path: `/sprints/${someSprintId}/burndown` },
        { label: "迭代概览", path: `/sprints/${someSprintId}/stats` },
      ]
    : []),
];

const picked = ONLY ? targets.filter((t) => t.label.includes(ONLY) || t.path.includes(ONLY)) : targets;
if (!picked.length) {
  console.error(`--only "${ONLY}" 没匹配到任何接口`);
  process.exit(2);
}

console.log(`\n性能探针 · ${API}`);
console.log(`数据：团队=${loadTeam?.name || "?"}（${teamId}） 项目=${loadProject?.name || "?"}（${projectId}） 轮次=${ITERS}`);
console.log(`提示：要大数量基线先跑 tools/seed-load.sql；跑完用 tools/cleanup-load.sql 清掉。\n`);

const rows = [];
for (const t of picked) rows.push(await measure(t.label, t.path, token));
rows.sort((a, b) => b.p95 - a.p95);

const pad = (s, n) => String(s).padEnd(n);
console.log(`${pad("接口", 26)} ${pad("p50", 7)} ${pad("p95", 7)} ${pad("max", 7)} ${pad("响应体", 9)} 状态`);
console.log("-".repeat(72));
for (const r of rows) {
  const kb = `${Math.round(r.size / 1024)}KB`;
  console.log(
    `${pad(r.label, 26)} ${pad(`${r.p50}ms`, 7)} ${pad(`${r.p95}ms`, 7)} ${pad(`${r.max}ms`, 7)} ${pad(kb, 9)} ${r.ok ? r.status : `HTTP ${r.status}（没跑到）`}`,
  );
}

const slowest = rows[0];
if (slowest) console.log(`\n最慢：${slowest.label} p95=${slowest.p95}ms`);

// 量级自检：没灌压测数据也能跑，但那时的数字只是"演示库基线"，必须在输出里说清楚，
// 否则日志里一排 5ms 会被当成"性能很好"的证据
if (!loadTeam || loadTeam.name !== "压测团队") {
  console.log(
    `\n[注意] 没找到「压测团队」，本次退回到演示数据（团队 ${teamId} / 项目 ${projectId}）。` +
      `这组数字只代表空库基线，别拿它下结论。要真量级先跑 tools/seed-load.sql。`,
  );
}

// 探针不对"慢"报错，但对"没跑起来"必须报错：
// 一个永远退出 0 的步骤，跑挂了也没人知道——那正是 KNOWN-024 的老毛病
const broken = rows.filter((r) => !r.ok);
if (broken.length) {
  console.error(
    `\n[失败] ${broken.length} 个接口没跑通：` +
      broken.map((r) => `${r.label}（HTTP ${r.status}）`).join("、"),
  );
  process.exit(1);
}

if (BUDGET > 0) {
  const over = rows.filter((r) => r.ok && r.p95 > BUDGET);
  if (over.length) {
    console.log(`\n超出预算 ${BUDGET}ms：${over.map((r) => `${r.label}(${r.p95}ms)`).join("、")}`);
    process.exit(1);
  }
  console.log(`\n全部在预算 ${BUDGET}ms 以内`);
}
void stamp;
