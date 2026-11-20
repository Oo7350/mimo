/**
 * 越权访问用例（接口级，无需浏览器）。
 *
 * 对应 bug-log KNOWN-017 里"越权场景未验证"这一项，回归 BUG-076：
 * `GET /api/wiki/tree?projectId=1` 曾经只校验"项目存在"，任何登录用户换个 projectId
 * 就能列出别人项目的知识库目录（标题、作者、更新时间）。
 *
 * 做法：注册一个不属于任何团队的外人，逐个打项目/团队/Sprint/任务/管理端读接口，
 * 只有被 401/403 拦住才算通过；任何返回 200 + code=200 的都是泄漏。
 *
 * 用法: node e2e/authz.mjs [apiBaseUrl]
 * 只读，不修改任何业务数据（会注册一个 probe-* 临时账号）。
 */

const API = (process.argv[2] || "http://localhost:8080/api").replace(/\/$/, "");

const results = [];
function rec(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
}

async function call(method, path, { token, params, body } = {}) {
  const url = new URL(API + path);
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
    }
  }
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    /* 非 JSON 响应（理论上不该出现） */
  }
  return { status: res.status, json, text };
}

async function login(username, password = "123456") {
  const r = await call("POST", "/auth/login", { body: { username, password } });
  if (r.json?.code !== 200) throw new Error(`登录失败 ${username}: ${r.text.slice(0, 120)}`);
  return r.json.data.token;
}

async function registerOutsider() {
  const name = "probe-" + Math.random().toString(36).slice(2, 8);
  const r = await call("POST", "/auth/register", {
    body: { username: name, password: "123456", email: `${name}@example.com`, nickname: "越权探针" },
  });
  if (r.json?.code !== 200) throw new Error(`注册失败: ${r.text.slice(0, 120)}`);
  return { name, token: await login(name) };
}

try {
  const admin = await login("admin");
  const outsider = await registerOutsider();
  console.log(`外人账号: ${outsider.name}\n`);

  // 找到真实的项目 / 团队 / Sprint / 任务 id
  const my = (await call("GET", "/projects/my", { token: admin })).json.data;
  const projectId = my[0].id;
  const teams = (await call("GET", "/teams/my", { token: admin })).json.data;
  const teamId = teams[0].id;
  const sprints = (await call("GET", `/sprints/project/${projectId}`, { token: admin })).json.data;
  const sprintId = sprints[0]?.id;
  const board = (await call("GET", `/board/${projectId}`, { token: admin })).json.data;
  const issueId = (board.columns || []).flatMap((c) => c.issues || [])[0]?.id;

  const targets = [
    ["项目详情", "GET", `/projects/${projectId}`, null],
    ["项目看板", "GET", `/board/${projectId}`, null],
    ["项目 Sprint 列表", "GET", `/sprints/project/${projectId}`, null],
    ["项目统计", "GET", `/reports/stats/${projectId}`, null],
    ["甘特图", "GET", `/gantt/project/${projectId}`, null],
    ["交付分析 Velocity", "GET", `/analytics/project/${projectId}/velocity`, null],
    ["累积流图 CFD", "GET", `/analytics/project/${projectId}/cfd`, null],
    ["Wiki 目录", "GET", "/wiki/tree", { projectId }],
    ["团队详情", "GET", `/teams/${teamId}`, null],
    ["团队成员", "GET", `/teams/${teamId}/members`, null],
    ["团队聊天历史", "GET", `/chat/history/${teamId}`, null],
    ["审计日志（管理员）", "GET", "/audit-logs", { page: 1, size: 5 }],
    ["Webhook 列表（管理员）", "GET", "/webhooks", null],
    ["邮件发送日志（管理员）", "GET", "/email-send-logs", null],
    ["数据导出", "GET", "/data-port/issues/export", { projectId, format: "json" }],
  ];
  if (sprintId) {
    targets.push(["Sprint 详情", "GET", `/sprints/${sprintId}`, null]);
    targets.push(["Sprint 燃尽图", "GET", `/sprints/${sprintId}/burndown`, null]);
    targets.push(["Sprint 健康度", "GET", `/sprints/${sprintId}/health`, null]);
  }
  if (issueId) {
    targets.push(["任务详情", "GET", `/issues/${issueId}`, null]);
    targets.push(["任务评论", "GET", `/comments/issue/${issueId}`, null]);
    targets.push(["任务附件", "GET", `/attachments/issue/${issueId}`, null]);
    targets.push(["任务验收记录", "GET", `/issues/${issueId}/reviews`, null]);
    targets.push(["任务版本", "GET", `/issues/${issueId}/versions`, null]);
  }

  const leaks = [];
  for (const [label, method, path, params] of targets) {
    const r = await call(method, path, { token: outsider.token, params });
    const blocked = r.status === 401 || r.status === 403 || r.json?.code === 401 || r.json?.code === 403;
    const leaked = r.status === 200 && r.json?.code === 200;
    if (leaked) leaks.push(label);
    rec(`越权拦截：${label}`, blocked, `HTTP ${r.status} code=${r.json?.code}`);
  }

  // 另外两项：成员自己访问不该被拦（防止"一刀切"式修复），以及缺参数要 400 不是 500
  const memberTree = await call("GET", "/wiki/tree", { token: admin, params: { projectId } });
  rec("成员读 Wiki 目录正常", memberTree.json?.code === 200, `code=${memberTree.json?.code}`);

  const missingParam = await call("GET", "/wiki/search", { token: admin, params: { projectId } });
  rec(
    "缺少必需参数返回 400",
    missingParam.status === 400 && missingParam.json?.code === 400,
    `HTTP ${missingParam.status} ${missingParam.json?.message || ""}`
  );

  console.log(`\n泄漏 ${leaks.length} 项${leaks.length ? "：" + leaks.join("、") : ""}`);
  const passed = results.filter((r) => r.ok).length;
  console.log(`通过 ${passed}/${results.length}`);
  process.exit(passed === results.length ? 0 : 1);
} catch (e) {
  console.error("用例执行失败:", e.message);
  process.exit(2);
}
