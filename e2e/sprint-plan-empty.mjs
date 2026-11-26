/**
 * 迭代规划面板的空态用例（v2.22.1，BUG-122）。
 *
 * 背景：候选为空有三种完全不同的原因，而原来界面只写「Backlog 暂无可规划工作项」——
 * 一句话打发三种情况，用户不知道该干什么：
 *   ① 项目里一项工作项都没有    → 该去建
 *   ② 未完成的都排进迭代了      → 该去干活，不是去建
 *   ③ 全都做完了                → 该建下一批
 * 这套用例证明接口能分清这三种，并且界面把原因和出路都说出来了。
 *
 * 跑法：node e2e/sprint-plan-empty.mjs http://localhost:5173
 * 依赖：admin 账号、团队 1。自己建团队 1 下的项目 + 迭代 + 卡，跑完删项目。
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const requirePlaywright = () => {
  const mod = process.env.PLAYWRIGHT_MODULE || "playwright";
  return require(mod);
};

const BASE = process.argv[2] || "http://localhost:5173";
const API = process.env.MIMO_API || "http://localhost:8080/api";
const ADMIN = { username: "admin", password: "123456" };
const STAMP = Date.now().toString().slice(-6);
const PROJECT_NAME = `e2e空态项目${STAMP}`;
const PROJECT_KEY = `E2E${STAMP}`;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}  — ${detail}`);
}

async function api(method, path, body, token) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json().catch(() => null);
}

async function login(creds) {
  const json = await api("POST", "/auth/login", creds);
  if (!json?.data?.token) throw new Error("登录失败: " + JSON.stringify(json).slice(0, 160));
  return json.data.token;
}

async function newPage(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript(() => {
    try {
      const done = { status: "done", v: 2, at: new Date().toISOString() };
      localStorage.setItem(
        "mimo_tour_state",
        JSON.stringify({ welcome: done, board: done, collab: done })
      );
    } catch {
      /* 忽略 */
    }
  });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("请输入用户名").fill(ADMIN.username);
  await page.getByPlaceholder("请输入密码").fill(ADMIN.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
  return { context, page };
}

const isoDate = (offsetDays) => {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.toISOString().slice(0, 10);
};

async function planOf(token, projectId, sprintId) {
  const res = await api("POST", "/ai/suggest-sprint-plan", { projectId, sprintId }, token);
  if (res?.code !== 200) throw new Error("规划接口失败: " + JSON.stringify(res).slice(0, 200));
  return res.data;
}

async function main() {
  const token = await login(ADMIN);
  const { chromium } = requirePlaywright();
  const browser = await chromium.launch({ headless: true });
  let projectId = null;

  try {
    const created = await api(
      "POST",
      "/projects",
      { name: PROJECT_NAME, key: PROJECT_KEY, teamId: 1, template: "SCRUM" },
      token
    );
    if (created?.code !== 200) throw new Error("建项目失败: " + JSON.stringify(created).slice(0, 200));
    projectId = created.data.id;

    const sprint = await api(
      "POST",
      "/sprints",
      { projectId, name: `Sprint ${STAMP}`, startDate: isoDate(1), endDate: isoDate(14) },
      token
    );
    if (sprint?.code !== 200) throw new Error("建迭代失败: " + JSON.stringify(sprint).slice(0, 200));
    const sprintId = sprint.data.id;

    // ---------- 1. 项目里一项工作项都没有：原因是 NO_ISSUES，并且指路去建 ----------
    const empty = await planOf(token, projectId, sprintId);
    record(
      "空项目：说清「还没有任何工作项」并指路去建",
      empty.emptyReason === "NO_ISSUES" &&
        /还没有任何工作项/.test(empty.summary || "") &&
        (empty.risks || []).some((r) => /先在项目看板里建/.test(r)),
      `reason=${empty.emptyReason}，summary=「${(empty.summary || "").slice(0, 40)}」`
    );

    // ---------- 2. 建两张卡后：有候选，emptyReason 应为空 ----------
    const issueIds = [];
    for (const title of [`空态候选A${STAMP}`, `空态候选B${STAMP}`]) {
      const issue = await api(
        "POST",
        "/issues",
        { projectId, type: "TASK", title, status: "TODO", priority: "MEDIUM" },
        token
      );
      if (issue?.code !== 200) throw new Error("建卡失败: " + JSON.stringify(issue).slice(0, 160));
      issueIds.push(issue.data.id);
    }
    const withCandidates = await planOf(token, projectId, sprintId);
    record(
      "有候选时不会冒出空态原因（正常情况没被影响）",
      // null 会被 Jackson 直接省掉（NON_NULL），所以这里是 undefined 而不是 null
      (withCandidates.emptyReason ?? null) === null &&
        (withCandidates.candidates || []).length === 2,
      `reason=${withCandidates.emptyReason}，候选 ${(withCandidates.candidates || []).length} 项`
    );

    // ---------- 3. 全部排进迭代：原因是 ALL_SCHEDULED（不是"去建工作项"） ----------
    const applied = await api("POST", `/sprints/${sprintId}/apply-plan`, { issueIds }, token);
    if (applied?.code !== 200) throw new Error("加入迭代失败: " + JSON.stringify(applied).slice(0, 160));
    const allScheduled = await planOf(token, projectId, sprintId);
    record(
      "都排进迭代了：说清「都已经排进迭代」，出路是做完手上的",
      allScheduled.emptyReason === "ALL_SCHEDULED" &&
        /都已经排进迭代/.test(allScheduled.summary || "") &&
        (allScheduled.risks || []).some((r) => /先把手上的迭代做完/.test(r)),
      `reason=${allScheduled.emptyReason}，summary=「${(allScheduled.summary || "").slice(0, 45)}」`
    );

    // ---------- 4. 全部完成：原因是 ALL_DONE ----------
    for (const id of issueIds) {
      const done = await api("PUT", "/issues", { id, status: "DONE" }, token);
      if (done?.code !== 200) throw new Error("标记完成失败: " + JSON.stringify(done).slice(0, 160));
    }
    const allDone = await planOf(token, projectId, sprintId);
    record(
      "全做完了：说清「都已全部完成」，出路是建下一批",
      allDone.emptyReason === "ALL_DONE" &&
        /都已全部完成/.test(allDone.summary || "") &&
        (allDone.risks || []).some((r) => /先建下一批工作项/.test(r)),
      `reason=${allDone.emptyReason}，summary=「${(allDone.summary || "").slice(0, 45)}」`
    );

    // ---------- 5. 界面上把原因和出路都显示出来（不是只说"没有"） ----------
    {
      const { context, page } = await newPage(browser);
      try {
        await page.goto(`${BASE}/projects/${projectId}/sprints`, { waitUntil: "domcontentloaded" });
        await page.locator(".sprint-card").first().waitFor({ state: "visible", timeout: 20000 });
        await page.locator("button", { hasText: "AI 规划" }).first().click();
        await page.locator("[data-plan-empty]").waitFor({ state: "visible", timeout: 15000 });
        const text = await page.locator("[data-plan-empty]").innerText();
        record(
          "界面空态写的是后端算出来的原因 + 下一步，并发药到「去项目看板建工作项」",
          /都已全部完成/.test(text) && /下一步：/.test(text) && /先建下一批工作项/.test(text),
          `界面文案=「${text.replace(/\s+/g, " ").slice(0, 90)}」`
        );
        const buttonVisible = await page
          .locator("[data-plan-empty] button", { hasText: "去项目看板建工作项" })
          .isVisible();
        record("空态给了可以直接点的出路按钮", buttonVisible, `按钮可见=${buttonVisible}`);
      } finally {
        await context.close();
      }
    }
  } finally {
    if (projectId) {
      const del = await api("DELETE", `/projects/${projectId}`, null, token);
      if (del?.code !== 200) console.warn(`⚠ 清理项目 ${projectId} 未成功: ${JSON.stringify(del).slice(0, 120)}`);
    }
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} 通过 ===`);
  if (failed.length) {
    console.log("失败项：\n" + failed.map((f) => ` - ${f.name}: ${f.detail}`).join("\n"));
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error("用例执行异常:", e);
  process.exitCode = 1;
});
