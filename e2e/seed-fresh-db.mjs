/**
 * 空库预热（CI / 全新环境）——把"一个人都没有的库"变成"e2e 能跑的状态"。
 *
 * 为什么需要：迁移脚本只建表、插权限与角色，**不插用户**；而 e2e 全家都假定
 * 「admin/123456、团队 1、项目 1、三列看板」。本机库是几个月长出来的，
 * 空库上直接跑 run-all 会从第一步就红——这正是 KNOWN-024（CI 没在真实 runner 上跑过）
 * 底下压着的坑。
 *
 * 它做的事（全部走真实 HTTP 接口，不碰 SQL，幂等）：
 *   1. 确认 admin 能登录（由后端 `mimo.bootstrap.admin-username` 在启动时创建）；
 *   2. 团队 1 不存在就建一个「Mimo 团队」，并把 admin 设为团队管理员；
 *   3. 项目 1 不存在就建一个「Mimo 演示项目」（SCRUM 模板，默认三列自动生成）；
 *   4. 打印最终 id，方便和 e2e 里的硬编码对上。
 *
 * 用法：
 *   node e2e/seed-fresh-db.mjs                       # 默认 http://localhost:8080/api
 *   MIMO_API=http://127.0.0.1:8080/api node e2e/seed-fresh-db.mjs
 *
 * 注意：它**不删**任何东西。已经有数据时只做"补缺"，所以可以重复跑。
 */

const API = process.env.MIMO_API || "http://localhost:8080/api";
const ADMIN = {
  username: process.env.MIMO_SEED_ADMIN_USERNAME || "admin",
  password: process.env.MIMO_SEED_ADMIN_PASSWORD || "123456",
};

async function api(method, path, body, token) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return { http: res.status, json };
}

function fail(message, detail) {
  console.error(`✗ ${message}`);
  if (detail !== undefined) console.error("  " + JSON.stringify(detail).slice(0, 400));
  process.exitCode = 1;
  process.exit(1);
}

async function main() {
  console.log(`空库预热：API=${API}`);

  // ---------- 1. admin 能不能登录 ----------
  const login = await api("POST", "/auth/login", ADMIN);
  if (login.json?.code !== 200 || !login.json?.data?.token) {
    fail(
      `登录失败（${ADMIN.username}）。空库请用 MIMO_BOOTSTRAP_ADMIN_USERNAME=${ADMIN.username} ` +
        `MIMO_BOOTSTRAP_ADMIN_PASSWORD=… 启动后端，让它先把管理员建出来`,
      login.json
    );
  }
  const token = login.json.data.token;
  const adminId = login.json.data.userInfo?.id;
  console.log(`✓ 管理员已就绪：${ADMIN.username}（id=${adminId}）`);

  // ---------- 2. 团队 ----------
  const teams = await api("GET", "/teams/my", null, token);
  if (teams.json?.code !== 200) fail("拉团队列表失败", teams.json);
  let team = (teams.json.data || [])[0];
  if (!team) {
    const created = await api("POST", "/teams", { name: "Mimo 团队", description: "e2e 预热团队" }, token);
    if (created.json?.code !== 200) fail("建团队失败", created.json);
    team = created.json.data;
    console.log(`+ 建了团队「${team.name}」（id=${team.id}）`);
  } else {
    console.log(`✓ 团队已存在：「${team.name}」（id=${team.id}）`);
  }
  if (team.id !== 1) {
    console.warn(
      `⚠ 团队 id 是 ${team.id} 而不是 1：e2e 里大量用例硬编码 teamId=1，` +
        `空库第一次跑才会是 1。这个库之前建过团队，要么用干净库，要么改用例。`
    );
  }

  // ---------- 3. 项目 ----------
  const projects = await api("GET", `/projects/team/${team.id}`, null, token);
  if (projects.json?.code !== 200) fail("拉项目列表失败", projects.json);
  let project = (projects.json.data || [])[0];
  if (!project) {
    const created = await api(
      "POST",
      "/projects",
      { name: "Mimo 演示项目", key: "MIMO", teamId: team.id, template: "SCRUM" },
      token
    );
    if (created.json?.code !== 200) fail("建演示项目失败", created.json);
    project = created.json.data;
    console.log(`+ 建了项目「${project.name}」（id=${project.id}）`);
  } else {
    console.log(`✓ 项目已存在：「${project.name}」（id=${project.id}）`);
  }

  // ---------- 4. 看板列与工作流（首次访问时自动生成，这里只是把它们踩出来） ----------
  const board = await api("GET", `/board/${project.id}`, null, token);
  if (board.json?.code !== 200) fail("读看板失败", board.json);
  const columns = (board.json.data?.columns || []).map((c) => `${c.id}:${c.name}`);
  console.log(`✓ 看板列：${columns.join(" / ")}`);

  const workflow = await api("GET", `/workflow/project/${project.id}/type/STORY`, null, token);
  const nodeCount = workflow.json?.data?.config?.nodes?.length ?? 0;
  console.log(`✓ STORY 工作流节点 ${nodeCount} 个（首次访问按看板列自动生成）`);

  console.log(`\n预热完成：teamId=${team.id}，projectId=${project.id}`);
  console.log("接下来：node e2e/run-all.mjs --probes");
}

main().catch((e) => {
  console.error("预热异常:", e);
  process.exitCode = 1;
});
