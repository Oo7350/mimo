/**
 * 主线自证用例（v2.19.0）：一个全新账号，从零把整条主链走完。
 *
 * 团队 → 项目 → 迭代 → 任务 → 开始处理 → 提交验收 → 验收通过 → 标记完成 → 报告
 *
 * 全部通过真实浏览器操作（不调业务 API 抄近路），只为了回答一个问题：
 * "第一次用这个系统的人，能不能只靠界面把活干完？"任何一步断掉都算失败。
 *
 * 用法：node e2e/main-chain.mjs [baseUrl]
 * 依赖：后端 :8080、前端 :5173；PLAYWRIGHT_MODULE / PLAYWRIGHT_BROWSERS_PATH 指向已有安装。
 */
import { createRequire } from "node:module";

const require_ = createRequire(import.meta.url);

function loadPlaywright() {
  const candidates = [];
  if (process.env.PLAYWRIGHT_MODULE) candidates.push(process.env.PLAYWRIGHT_MODULE);
  candidates.push("playwright");
  for (const c of candidates) {
    try {
      return require_(c);
    } catch {
      /* 继续试下一个 */
    }
  }
  console.error("找不到 playwright：设 PLAYWRIGHT_MODULE=<dir>/node_modules/playwright");
  process.exit(2);
}

const { chromium } = loadPlaywright();
const BASE = process.argv[2] || "http://localhost:5173";
const ADMIN = { username: "admin", password: "123456" };
const STAMP = Date.now().toString().slice(-6);
const NEWBIE = { username: `chain${STAMP}`, password: "123456", email: `chain${STAMP}@mimo.local` };
const TEAM = { name: `链路团队${STAMP}`, desc: "主线自证用例创建的团队" };
const PROJECT = { name: `链路项目${STAMP}`, key: `C${STAMP.slice(-4)}` };
const SPRINT = { name: `Sprint-${STAMP}`, goal: "把一条任务从待办推到完成" };
const ISSUE = { title: `链路任务${STAMP}` };

const results = [];
const created = { userId: null, teamId: null, projectId: null, sprintId: null, issueId: null };

function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

async function api(method, url, body, token) {
  const res = await fetch(`${BASE}/api${url}`, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text, status: res.status };
  }
}

async function login(creds) {
  const json = await api("POST", "/auth/login", creds);
  if (!json?.data?.token) throw new Error("登录失败: " + JSON.stringify(json).slice(0, 160));
  return json.data.token;
}

async function newPage(browser, creds) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  // 关掉新手引导：真实 key 是 localStorage.mimo_tour_state（按主题记状态 + 版本号）。
  // 之前写 sessionStorage 的假 key 完全不起作用——sprint 页的引导会自动启动，遮罩让
  // pointer-events 拦下点击，在用例里就表现为"按钮点不动"。
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
  await page.getByPlaceholder("请输入用户名").fill(creds.username);
  await page.getByPlaceholder("请输入密码").fill(creds.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
  return { context, page };
}

/** 点一个元素并等它消失（对话框关闭） */
async function clickAndSettle(page, locator) {
  await locator.click();
  await page.waitForTimeout(1200);
}

async function main() {
  // 先注册一个全新账号（通过接口，因为注册页不是本用例的考察点）
  const reg = await api("POST", "/auth/register", NEWBIE);
  if (reg?.code !== 200) throw new Error("注册失败: " + JSON.stringify(reg).slice(0, 200));
  const adminToken = await login(ADMIN);
  const newbieToken = await login(NEWBIE);
  const me = await api("GET", "/user/profile", null, newbieToken);
  created.userId = me?.data?.id;
  console.log(`准备：新账号 ${NEWBIE.username}(id=${created.userId}) 已注册\n`);

  const browser = await chromium.launch({ headless: true });

  try {
    // ==================== 1. 建团队 ====================
    {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/teams`, { waitUntil: "domcontentloaded" });
        await page.getByRole("button", { name: /创建团队|创建第一个团队/ }).first().click();
        await page.getByPlaceholder("如：前端开发组").fill(TEAM.name);
        await page.getByRole("button", { name: "创建", exact: true }).click();
        await page.waitForTimeout(1500);
        const shown = await page.locator(`text=${TEAM.name}`).count();
        const teams = await api("GET", "/teams/my", null, newbieToken);
        const team = (teams?.data || []).find((t) => t.name === TEAM.name);
        created.teamId = team?.id || null;
        record(
          "新账号能在界面上建出第一个团队",
          shown > 0 && !!created.teamId,
          `列表可见=${shown > 0}，teamId=${created.teamId}`
        );
      } catch (e) {
        record("新建账号建团队", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ==================== 2. 团队里建项目 ====================
    if (created.teamId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/teams/${created.teamId}`, { waitUntil: "domcontentloaded" });
        await page.getByRole("button", { name: /创建项目/ }).first().click();
        const dialog = page.locator(".el-dialog", { hasText: "创建项目" }).first();
        await dialog.waitFor({ state: "visible" });
        const inputs = dialog.locator("input");
        await inputs.nth(0).fill(PROJECT.name);
        await inputs.nth(1).fill(PROJECT.key);
        await clickAndSettle(page, dialog.getByRole("button", { name: /确定|创建/ }).first());
        const projects = await api("GET", `/projects/team/${created.teamId}`, null, newbieToken);
        const list = Array.isArray(projects?.data) ? projects.data : projects?.data?.records || [];
        const project = list.find((p) => p.name === PROJECT.name);
        created.projectId = project?.id || null;
        record(
          "团队管理员能在团队页直接建出项目",
          !!created.projectId,
          created.projectId ? `projectId=${created.projectId}` : "接口里查不到刚建的项目"
        );
      } catch (e) {
        record("团队里建项目", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("团队里建项目", false, "上一步没有团队，跳过");
    }

    // ==================== 3. 建 Sprint ====================
    if (created.projectId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/sprints`, {
          waitUntil: "domcontentloaded",
        });
        await page.getByRole("button", { name: /新建 Sprint|创建 Sprint/ }).first().click();
        const dialog = page.locator(".el-dialog", { hasText: "新建 Sprint" }).first();
        await dialog.waitFor({ state: "visible" });
        await dialog.getByPlaceholder("例如：Sprint 12").fill(SPRINT.name);
        await dialog.getByPlaceholder("本次迭代要达成的目标").fill(SPRINT.goal);
        // 起止时间是必填：不选就点"创建"，以前是静默 return（页面上什么都不发生），
        // 现在会给"请选择 Sprint 的起止时间"——这里按正常路径把日期选上
        await dialog.getByPlaceholder("开始日期").click();
        const cells = page.locator(".el-date-range-picker .el-date-table td.available");
        await cells.first().waitFor({ state: "visible", timeout: 15000 });
        await cells.first().click();
        await cells.nth(4).click();
        await page.waitForTimeout(600);
        await clickAndSettle(page, dialog.getByRole("button", { name: "创建", exact: true }));
        const sprints = await api("GET", `/sprints/project/${created.projectId}`, null, newbieToken);
        const list = Array.isArray(sprints?.data) ? sprints.data : [];
        const sprint = list.find((s) => s.name === SPRINT.name);
        created.sprintId = sprint?.id || null;
        record(
          "项目里能建出迭代（Sprint）",
          !!created.sprintId,
          created.sprintId ? `sprintId=${created.sprintId}` : "接口里查不到刚建的 Sprint"
        );
      } catch (e) {
        record("建 Sprint", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("建 Sprint", false, "上一步没有项目，跳过");
    }

    // ==================== 4. 看板建任务 ====================
    if (created.projectId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/board`, {
          waitUntil: "domcontentloaded",
        });
        await page.locator("#create-task-btn").click();
        const dialog = page.locator(".el-dialog", { hasText: "新建" }).first();
        await dialog.waitFor({ state: "visible" });
        await dialog.locator("input").first().fill(ISSUE.title);
        await clickAndSettle(page, dialog.getByRole("button", { name: /确定|保存|创建/ }).first());
        const queried = await api(
          "POST",
          "/issues/query",
          { page: 1, size: 20, projectId: created.projectId },
          newbieToken
        );
        const issues = Array.isArray(queried?.data) ? queried.data : queried?.data?.records || [];
        const issue = issues.find((i) => i.title === ISSUE.title);
        created.issueId = issue?.id || null;
        record(
          "看板上能建出任务，并且卡片出现在待办",
          !!created.issueId,
          created.issueId ? `issueId=${created.issueId}` : "接口里查不到刚建的任务"
        );
      } catch (e) {
        record("看板建任务", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("看板建任务", false, "上一步没有项目，跳过");
    }

    // ==================== 5. 待办 → 进行中（用「下一步」）====================
    if (created.issueId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/board?issue=${created.issueId}`, {
          waitUntil: "domcontentloaded",
        });
        await page.locator(".next-step").waitFor({ state: "visible" });
        await page.locator(".next-step__actions button").first().click();
        await page.waitForTimeout(2500);
        const after = await api("GET", `/issues/${created.issueId}`, null, newbieToken);
        record(
          "任务从待办推进到进行中",
          after?.data?.status === "IN_PROGRESS",
          `status=${after?.data?.status}`
        );
      } catch (e) {
        record("待办 → 进行中", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("待办 → 进行中", false, "没有任务，跳过");
    }

    // ==================== 6. 提交验收 ====================
    if (created.issueId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/board?issue=${created.issueId}`, {
          waitUntil: "domcontentloaded",
        });
        await page.locator(".next-step").waitFor({ state: "visible" });
        await page.locator(".next-step__actions button", { hasText: "提交验收" }).first().click();
        const dialog = page.locator(".el-dialog", { hasText: "提交任务验收" }).first();
        await dialog.waitFor({ state: "visible" });
        // 填一句验收说明再提交
        const textarea = dialog.locator("textarea").first();
        if (await textarea.count()) await textarea.fill("自证用例：功能已完成，请验收");
        const submit = dialog.getByRole("button", { name: /提交|确定/ }).last();
        await clickAndSettle(page, submit);
        const reviews = await api(
          "GET",
          `/issues/${created.issueId}/reviews`,
          null,
          newbieToken
        );
        const list = Array.isArray(reviews?.data) ? reviews.data : reviews?.data?.records || [];
        const pending = list.find((r) => r.status === "PENDING");
        record("能把任务提交到「待验收」", !!pending, pending ? `reviewId=${pending.id}` : "查不到待验收记录");
      } catch (e) {
        record("提交验收", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("提交验收", false, "没有任务，跳过");
    }

    // ==================== 6.5 单人团队的死锁要被说清楚，然后邀请第二个人 ====================
    // 真实顺序：一个人把活干完提交，然后发现团队里没人能验收（自己不能验收自己）。
    // 界面必须把这件事说明白，否则用户只会看到"待验收"发呆。
    if (created.issueId && created.teamId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/board?issue=${created.issueId}`, {
          waitUntil: "domcontentloaded",
        });
        await page.locator(".next-step").waitFor({ state: "visible" });
        const blocked = page.locator(".next-step-blocked").first();
        await blocked.waitFor({ state: "visible", timeout: 12000 });
        const text = await blocked.innerText();
        record(
          "待验收但团队里没人能验收时，界面明说了原因",
          /只有你自己|除你之外/.test(text),
          text.replace(/\s+/g, " ").slice(0, 60)
        );
      } catch (e) {
        record("单人团队死锁提示", false, e.message);
      } finally {
        await context.close();
      }

      const { context: c2, page: p2 } = await newPage(browser, NEWBIE);
      try {
        await p2.goto(`${BASE}/teams/${created.teamId}`, { waitUntil: "domcontentloaded" });
        await p2.getByRole("button", { name: /邀请成员/ }).first().click();
        const inviteDialog = p2.locator(".el-dialog", { hasText: "邀请成员" }).first();
        await inviteDialog.waitFor({ state: "visible" });
        // filterable+remote 的 el-select：placeholder 不在 input 上，得点 input 再输入
        await inviteDialog.locator(".el-select").first().click();
        await inviteDialog.locator(".el-select input").first().fill("admin");
        await p2.waitForTimeout(1800);
        const option = p2.locator(".el-select-dropdown__item", { hasText: /admin/ }).first();
        await option.waitFor({ state: "visible", timeout: 15000 });
        await option.click();
        await inviteDialog.getByRole("button", { name: "邀请", exact: true }).click();
        await p2.waitForTimeout(2000);
        const members = await api("GET", `/teams/${created.teamId}/members`, null, newbieToken);
        const list = Array.isArray(members?.data) ? members.data : [];
        record(
          "从界面邀请第二个成员，团队里就有能验收的人了",
          list.length >= 2,
          `成员数=${list.length}：${list.map((m) => m.username).join(",")}`
        );
      } catch (e) {
        record("邀请第二个成员", false, e.message);
      } finally {
        await c2.close();
      }
    } else {
      record("单人团队死锁提示 / 邀请成员", false, "没有任务或团队，跳过");
    }

    // ==================== 7. 管理员验收通过（工作台入口）====================
    if (created.issueId) {
      const { context, page } = await newPage(browser, ADMIN);
      try {
        await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
        // 工作台「待我处理」里应该有这条验收；没有就说明验收人根本没被通知到
        const row = page.locator(".dash-row", { hasText: ISSUE.title }).first();
        await row.waitFor({ state: "visible", timeout: 15000 });
        await row.click();
        await page.waitForURL(/\/board\?issue=/, { timeout: 15000 });
        await page.locator(".el-dialog").first().waitFor({ state: "visible" });
        // 切到「验收」面板并通过（通过按钮会弹一个可填说明的输入框）
        await page.getByRole("tab", { name: "验收", exact: true }).click();
        await page.waitForTimeout(800);
        await page.locator(".el-tab-pane:visible").getByRole("button", { name: "通过", exact: true }).first().click();
        const prompt = page.locator(".el-message-box").first();
        await prompt.waitFor({ state: "visible", timeout: 10000 });
        const noteInput = prompt.locator("input, textarea").first();
        if (await noteInput.count()) await noteInput.fill("甲方验收通过");
        await prompt.locator("button", { hasText: /^通过$/ }).first().click();
        await page.waitForTimeout(2000);
        const reviews = await api("GET", `/issues/${created.issueId}/reviews`, null, adminToken);
        const list = Array.isArray(reviews?.data) ? reviews.data : reviews?.data?.records || [];
        const approved = list.find((r) => r.status === "APPROVED");
        record(
          "管理员从工作台一键进入并验收通过",
          !!approved,
          approved ? `reviewId=${approved.id} 状态=APPROVED` : "验收状态没有变成 APPROVED"
        );
      } catch (e) {
        record("管理员验收通过", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("管理员验收通过", false, "没有任务，跳过");
    }

    // ==================== 8. 标记完成 ====================
    if (created.issueId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/board?issue=${created.issueId}`, {
          waitUntil: "domcontentloaded",
        });
        await page.locator(".next-step").waitFor({ state: "visible" });
        const markDone = page.locator(".next-step__actions button", { hasText: /标记完成|完成/ }).first();
        await markDone.click();
        await page.waitForTimeout(2500);
        const after = await api("GET", `/issues/${created.issueId}`, null, newbieToken);
        record("验收通过后能一键标记完成", after?.data?.status === "DONE", `status=${after?.data?.status}`);
      } catch (e) {
        record("标记完成", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("标记完成", false, "没有任务，跳过");
    }

    // ==================== 9. 看报告 ====================
    if (created.projectId) {
      const { context, page } = await newPage(browser, NEWBIE);
      try {
        await page.goto(`${BASE}/projects/${created.projectId}/reports`, {
          waitUntil: "domcontentloaded",
        });
        // 等到报告页真的渲染出来再判断，避免在挂载前读到空 body 误判"没有入口"
        const generate = page.getByRole("button", { name: /生成报告/ }).first();
        await generate.waitFor({ state: "visible", timeout: 20000 });
        const reportList = await api("GET", `/reports?projectId=${created.projectId}`, null, newbieToken);
        const list = Array.isArray(reportList?.data) ? reportList.data : [];
        record(
          "完成之后能回到项目报告，页面有「生成报告」入口",
          (await generate.count()) > 0,
          `报告数=${list.length}，生成入口可见`
        );
      } catch (e) {
        record("看报告", false, e.message);
      } finally {
        await context.close();
      }
    } else {
      record("看报告", false, "没有项目，跳过");
    }
  } finally {
    // 清理：先用新账号自己删（项目 owner / 团队管理员），删不掉再让 admin 兜底
    const cleanup = [];
    if (created.issueId) {
      const r = await api("DELETE", `/issues/${created.issueId}`, null, newbieToken);
      cleanup.push(`issue=${r?.code}`);
    }
    if (created.projectId) {
      const r = await api("DELETE", `/projects/${created.projectId}`, null, newbieToken);
      cleanup.push(`project=${r?.code}`);
    }
    if (created.teamId) {
      const r = await api("DELETE", `/teams/${created.teamId}`, null, newbieToken);
      cleanup.push(`team=${r?.code}`);
    }
    const leftovers = cleanup.filter((c) => !c.endsWith("=200"));
    if (leftovers.length) {
      console.warn(`⚠ 清理未成功：${leftovers.join(" ")}（team=${created.teamId} user=${created.userId}）`);
    }
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} 通过 ===`);
  console.log(
    `\n清理信息：issue=${created.issueId} project=${created.projectId} team=${created.teamId} user=${created.userId}（${NEWBIE.username}）`
  );
  if (failed.length) {
    console.log("失败项（就是主线上的断点）：\n" + failed.map((f) => ` - ${f.name}: ${f.detail}`).join("\n"));
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error("用例执行异常:", e);
  process.exitCode = 1;
});
