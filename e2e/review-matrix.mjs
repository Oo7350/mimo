/**
 * 多角色验收矩阵（KNOWN-017 剩余部分）。
 *
 * 起因：主线自证只证明了"一个人也能走完一遍"。但验收这件事的规则是**按角色分**的——
 * 谁能提交、谁不能验收自己的提交、普通成员能不能点「通过」、团队管理员算不算管理员。
 * 这些规则以前没有任何用例覆盖，而界面上又把这些按钮一律显示出来，
 * 于是出现"点得下去、点了才被拒"的观感问题。
 *
 * 这个用例把后端规则和界面显示一起钉住：
 *   A. 不是负责人 → 不能提交
 *   B. 负责人提交 → PENDING
 *   C. 提交人自己 → 不能验收自己
 *   D. 普通成员（非报告人、非管理员）→ 不能验收
 *   E. 报告人（admin）→ 能验收通过，工作项变 DONE
 *   F. 已处理的记录 → 不能再验一次
 *   G. 打回必须填原因 / 打回后状态回退 + 进「被驳回」
 *   H. 团队管理员（RBAC team.manage，非系统管理员）→ 能验收
 *   I. 界面上：提交人看不到「通过/打回」，只会被告知在等谁
 *   J. 界面上：能验收的人看得到「通过/打回」
 *
 * 跑法：node e2e/review-matrix.mjs http://localhost:5173
 * 清理：会建「e2e团队…/e2e项目…」，跑完执行 mysql < e2e/cleanup-residue.sql 即可收干净。
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const requirePlaywright = () => require(process.env.PLAYWRIGHT_MODULE || "playwright");

const BASE = process.argv[2] || "http://localhost:5173";
const API = process.env.MIMO_API || "http://localhost:8080/api";
const STAMP = Date.now().toString().slice(-6);

const ADMIN = { username: "admin", password: "123456", id: 1 };
const DEV = { username: "zhangsan", password: "123456", id: 2 }; // 任务负责人
const PLAIN = { username: "lisi", password: "123456", id: 3 }; // 先当普通成员，后面升团队管理员

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}\n        ${detail}`);
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
  const json = await res.json().catch(() => null);
  return { httpStatus: res.status, ...(json || {}) };
}

async function login(user) {
  const json = await api("POST", "/auth/login", { username: user.username, password: user.password });
  if (!json?.data?.token) throw new Error(`登录失败 ${user.username}`);
  return json.data.token;
}

async function newPage(browser, user) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 950 } });
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
  await page.getByPlaceholder("请输入用户名").fill(user.username);
  await page.getByPlaceholder("请输入密码").fill(user.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
  return { context, page };
}

async function main() {
  const tAdmin = await login(ADMIN);
  const tDev = await login(DEV);
  const tPlain = await login(PLAIN);

  // ---------- 准备：团队 + 项目 + 三名成员 ----------
  const team = await api("POST", "/teams", { name: `e2e团队验收${STAMP}`, description: "验收矩阵用例" }, tAdmin);
  if (team.code !== 200) throw new Error("建团队失败: " + JSON.stringify(team).slice(0, 200));
  const teamId = team.data.id;

  for (const user of [DEV, PLAIN]) {
    const inv = await api("POST", "/teams/invite", { teamId, userId: user.id }, tAdmin);
    if (inv.code !== 200) throw new Error(`邀请 ${user.username} 失败: ${inv.message}`);
  }

  const project = await api(
    "POST",
    "/projects",
    { name: `e2e项目验收${STAMP}`, key: `R${STAMP.slice(-5)}`, teamId, description: "验收矩阵用例" },
    tAdmin
  );
  if (project.code !== 200) throw new Error("建项目失败: " + JSON.stringify(project).slice(0, 200));
  const projectId = project.data.id;

  const board = await api("GET", `/board/${projectId}`, null, tAdmin);
  const columns = board.data?.columns || board.data || [];
  const firstColumnId = columns[0]?.id;

  /** 建一张"报告人=admin、负责人=zhangsan"的任务 */
  async function createIssue(title) {
    const res = await api(
      "POST",
      "/issues",
      {
        projectId,
        title,
        type: "TASK",
        priority: "MEDIUM",
        columnId: firstColumnId,
        assigneeId: DEV.id,
      },
      tAdmin
    );
    if (res.code !== 200) throw new Error("建任务失败: " + JSON.stringify(res).slice(0, 200));
    return res.data;
  }

  const issueA = await createIssue(`验收矩阵A-${STAMP}`);
  console.log(`准备完成：team=${teamId} project=${projectId} issue=${issueA.id}\n`);

  let reviewA = null;

  try {
    // ---------- A. 不是负责人不能提交 ----------
    {
      const r = await api("POST", `/issues/${issueA.id}/reviews`, { completionNote: "我来提交" }, tPlain);
      record(
        "A. 非负责人提交验收被拒（只有负责人能提交）",
        r.code === 403 && /负责人/.test(r.message || ""),
        `code=${r.code} message=${r.message}`
      );
    }

    // ---------- B. 负责人提交 ----------
    {
      const r = await api(
        "POST",
        `/issues/${issueA.id}/reviews`,
        { completionNote: "做完了，附上自测记录", evidenceUrls: ["https://example.com/test"] },
        tDev
      );
      reviewA = r.data;
      record(
        "B. 负责人提交验收 → PENDING",
        r.code === 200 && reviewA?.status === "PENDING" && reviewA?.submitterId === DEV.id,
        `code=${r.code} reviewId=${reviewA?.id} status=${reviewA?.status}`
      );
    }

    // ---------- C. 提交人不能验收自己 ----------
    {
      const r = await api("PUT", `/issue-reviews/${reviewA.id}/approve`, { note: "我自己过" }, tDev);
      record(
        "C. 提交人验收自己的提交被拒",
        r.code === 403 && /自己提交/.test(r.message || ""),
        `code=${r.code} message=${r.message}`
      );
    }

    // ---------- D. 普通成员不能验收 ----------
    {
      const r = await api("PUT", `/issue-reviews/${reviewA.id}/approve`, { note: "我批了" }, tPlain);
      record(
        "D. 普通成员（非报告人/非管理员）验收被拒",
        r.code === 403 && /项目管理员或报告人/.test(r.message || ""),
        `code=${r.code} message=${r.message}`
      );
    }

    // ---------- I / J. 界面上：该看不到的看不到，该看得到的看得到 ----------
    {
      const { chromium } = requirePlaywright();
      const browser = await chromium.launch({ headless: true });
      try {
        // I. 提交人：没有按钮，但有"在等谁"的说明
        const dev = await newPage(browser, DEV);
        await dev.page.goto(`${BASE}/issues/${issueA.id}`, { waitUntil: "domcontentloaded" });
        await dev.page.waitForTimeout(2500);
        const submitterButtons = await dev.page.locator(".review-card__actions button").count();
        const waiting = await dev.page.locator(".review-panel__waiting").first().innerText().catch(() => "");
        record(
          "I. 提交人在界面上看不到「通过 / 打回」，只被告知在等谁",
          submitterButtons === 0 && /自己提交/.test(waiting),
          `通过按钮=${submitterButtons} 提示=${waiting || "(没有)"}`
        );
        await dev.context.close();

        // J. 报告人：看得到「通过 / 打回」
        const admin = await newPage(browser, ADMIN);
        await admin.page.goto(`${BASE}/issues/${issueA.id}`, { waitUntil: "domcontentloaded" });
        await admin.page.waitForTimeout(2500);
        const adminApprove = await admin.page.locator(".review-card__actions button").filter({ hasText: "通过" }).count();
        const adminReject = await admin.page.locator(".review-card__actions button").filter({ hasText: "打回" }).count();
        if (adminApprove === 0) {
          const panelText = await admin.page
            .locator(".review-panel")
            .innerText()
            .catch(() => "(review-panel 没渲染)");
          console.log("        [调试] 面板文字: " + panelText.replace(/\s+/g, " ").slice(0, 220));
        }
        record(
          "J. 报告人/管理员在界面上看得到「通过 / 打回」",
          adminApprove >= 1 && adminReject >= 1,
          `通过按钮=${adminApprove} 打回按钮=${adminReject}`
        );
        await admin.context.close();
      } finally {
        await browser.close();
      }
    }

    // ---------- E. 两道闸门各管一段：工作流管"能不能这样走"，验收门禁管"能不能收工" ----------
    // 设计如此：验收通过不会自动把卡搬到完成列（看板那侧有 review gate 守着），
    // 详情页「下一步」会提示"验收过了，最后一步：标记完成"。所以这里三头都要验。
    {
      const doneColumn = columns[columns.length - 1];
      const inProgress = columns[1];
      const waitingReview = columns.length >= 4 ? columns[2] : columns[columns.length - 2];

      // 工作流闸门：待办 直接跳 已完成 不合规则，应当被工作流挡（而不是验收门禁）
      const jump = await api(
        "PUT",
        "/board/issue/move",
        { issueId: issueA.id, targetColumnId: doneColumn.id, sortOrder: 0 },
        tDev
      );
      record(
        "E1. 跳步被工作流挡（待办 → 已完成）",
        jump.code === 400 && /工作流/.test(jump.message || ""),
        `code=${jump.code} message=${jump.message}`
      );

      // 正常走到「待验收」，再试进完成列：这次该是验收门禁挡
      await api("PUT", "/board/issue/move", { issueId: issueA.id, targetColumnId: inProgress.id, sortOrder: 0 }, tDev);
      await api("PUT", "/board/issue/move", { issueId: issueA.id, targetColumnId: waitingReview.id, sortOrder: 0 }, tDev);
      const early = await api(
        "PUT",
        "/board/issue/move",
        { issueId: issueA.id, targetColumnId: doneColumn.id, sortOrder: 0 },
        tDev
      );
      record(
        "E2. 验收没通过时不能进完成列（review gate 拦住）",
        early.code === 409 && /验收/.test(early.message || ""),
        `code=${early.code} message=${early.message}`
      );

      const approved = await api("PUT", `/issue-reviews/${reviewA.id}/approve`, { note: "自测记录齐全" }, tAdmin);
      const moved = await api(
        "PUT",
        "/board/issue/move",
        { issueId: issueA.id, targetColumnId: doneColumn.id, sortOrder: 0 },
        tDev
      );
      const issue = await api("GET", `/issues/${issueA.id}`, null, tAdmin);
      record(
        "E3. 验收通过后可以进完成列，工作项变 DONE",
        approved.data?.status === "APPROVED" && moved.code === 200 && issue.data?.status === "DONE",
        `review=${approved.data?.status} move=${moved.code} issueStatus=${issue.data?.status}`
      );
    }

    // ---------- F. 已处理的记录不能再验 ----------
    {
      const r = await api("PUT", `/issue-reviews/${reviewA.id}/approve`, { note: "再来一次" }, tAdmin);
      record(
        "F. 已处理的验收记录不能再操作",
        r.code === 400 && /已处理/.test(r.message || ""),
        `code=${r.code} message=${r.message}`
      );
    }

    // ---------- G. 打回：必须填原因 / 状态回退 / 进「被驳回」 ----------
    {
      const issueB = await createIssue(`验收矩阵B-${STAMP}`);
      const submitted = await api(
        "POST",
        `/issues/${issueB.id}/reviews`,
        { completionNote: "做完了，但没写测试" },
        tDev
      );
      const reviewB = submitted.data;

      const noReason = await api("PUT", `/issue-reviews/${reviewB.id}/reject`, { note: "" }, tAdmin);
      const rejected = await api(
        "PUT",
        `/issue-reviews/${reviewB.id}/reject`,
        { note: "缺回归测试，补一下" },
        tAdmin
      );
      const issueAfter = await api("GET", `/issues/${issueB.id}`, null, tAdmin);
      const workbench = await api("GET", "/workbench/my", null, tDev);
      const rejectedList = workbench.data?.rejectedItems || [];
      const appeared = rejectedList.some((x) => x.issueId === issueB.id);

      record(
        "G. 打回必须填原因；打回后状态回退且进入提交人的「被驳回」",
        noReason.code === 400 &&
          /原因/.test(noReason.message || "") &&
          rejected.data?.status === "REJECTED" &&
          issueAfter.data?.status !== "DONE" &&
          appeared,
        `空原因=${noReason.code}「${noReason.message}」; 打回=${rejected.data?.status}; ` +
          `issueStatus=${issueAfter.data?.status}; 工作台被驳回=${appeared}`
      );
    }

    // ---------- H. 团队管理员（非系统管理员）能验收 ----------
    {
      // 给 lisi 授 PROJECT_MANAGER（含 team.manage）在 TEAM 作用域上
      const roles = await api("GET", "/role", null, tAdmin);
      const role = (roles.data || []).find((x) => x.code === "PROJECT_MANAGER");
      if (!role) throw new Error("找不到 PROJECT_MANAGER 角色");
      const assign = await api(
        "POST",
        "/role/assign",
        { userId: PLAIN.id, roleId: role.id, scopeType: "TEAM", scopeId: teamId },
        tAdmin
      );
      const issueC = await createIssue(`验收矩阵C-${STAMP}`);
      await api("POST", `/issues/${issueC.id}/reviews`, { completionNote: "C 号任务完成" }, tDev);
      const reviewsC = await api("GET", `/issues/${issueC.id}/reviews`, null, tPlain);
      const pendingC = (reviewsC.data || []).find((x) => x.status === "PENDING");
      const r = await api("PUT", `/issue-reviews/${pendingC.id}/approve`, { note: "团队管理员验收" }, tPlain);

      record(
        "H. 团队管理员（非系统管理员）可以验收",
        assign.code === 200 && r.code === 200 && r.data?.status === "APPROVED",
        `授权=${assign.code} 验收=${r.code} ${r.data?.status || r.message}`
      );
    }
  } finally {
    console.log(
      `\n提示：本次数据（项目 ${projectId} / 团队 ${teamId}）可用 ` +
        `mysql -umimo -pmimo123 mimo < e2e/cleanup-residue.sql 清理（按 e2e项目/e2e团队 前缀匹配）。`
    );
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
