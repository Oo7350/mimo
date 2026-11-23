/**
 * 功能链路闭合用例（v2.18.3 · 计划书外的新一批「接断点」）
 *
 * 背景：用户反馈「功能很散、浮于形式」。查下来不是功能没做，是**页与页之间那一步没接**：
 *   1) 工作台快捷入口 5 个里 4 个 route 都是 '/projects'，「看板视图」永远不会到看板（KNOWN-014）；
 *   2) 通知邮件的链接是 `/issues/{id}`，前端却没有这条路由 —— 点开落到登录页/工作台；
 *   3) 站内通知点击只认 relatedType==='ISSUE'，审批类通知点了回工作台；
 *   4) 侧边栏「设置」用 users.role === 'ROLE_ADMIN' 判断，只在 RBAC 里有角色的管理员看不到。
 *
 * 这个用例就盯这四条，全部走真实浏览器点击 + URL 断言。
 *
 * 用法：node e2e/chain-links.mjs [baseUrl]
 * 依赖：后端 :8080、前端 :5173 已启动；PLAYWRIGHT_BROWSERS_PATH 指向已安装浏览器；
 *       playwright 模块用 PLAYWRIGHT_MODULE 指向已有安装（不必往仓库里塞依赖）。
 */
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require_ = createRequire(import.meta.url);

function loadPlaywright() {
  const candidates = [];
  if (process.env.PLAYWRIGHT_MODULE) candidates.push(process.env.PLAYWRIGHT_MODULE);
  candidates.push("playwright");
  const tried = [];
  for (const c of candidates) {
    try {
      return require_(c);
    } catch (e) {
      tried.push(`  - ${c} → ${e.code || e.message}`);
    }
  }
  console.error("找不到 playwright，试过：\n" + tried.join("\n"));
  console.error("解决：设 PLAYWRIGHT_MODULE=<dir>/node_modules/playwright");
  process.exit(2);
}

const { chromium } = loadPlaywright();

const HERE = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] || "http://localhost:5173";
const ADMIN = { username: "admin", password: "123456" };
const MEMBER = { username: "lisi", password: "123456" };

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

/** 直接打接口做数据准备/断言（浏览器里的点击才是被测对象） */
async function apiLogin(creds) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  const json = await res.json();
  if (!json?.data?.token) throw new Error("登录失败: " + JSON.stringify(json).slice(0, 200));
  return json.data.token;
}

async function api(token, method, url, body) {
  const res = await fetch(`${BASE}/api${url}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function loginUI(page, creds) {
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("请输入用户名").fill(creds.username);
  await page.getByPlaceholder("请输入密码").fill(creds.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
}

/** 每个场景一个干净上下文：登录态互不影响，也不会因为残留 token 被守卫弹回工作台 */
async function freshPage(browser, creds) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  // 关掉新手引导自动弹窗：引导遮罩会拦截点击（driver-popover 覆盖全屏），
  // 这里不是要测引导，用例只关心跳转
  await context.addInitScript(() => {
    try {
      sessionStorage.setItem("mimo_tour_session_welcome", "1");
      sessionStorage.setItem("mimo_tour_session_board", "1");
    } catch {}
  });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  await loginUI(page, creds);
  return { context, page };
}

async function main() {
  // ---- 准备：找一条真实存在的工作项 ----
  const adminToken = await apiLogin(ADMIN);
  const myProjects = await api(adminToken, "GET", "/projects/my");
  const project = myProjects?.data?.[0];
  if (!project) throw new Error("库里没有项目，无法执行用例（先跑一次演示数据初始化）");
  const issuePage = await api(adminToken, "POST", "/issues/query", {
    page: 1,
    size: 10,
    projectId: project.id,
  });
  const issues = Array.isArray(issuePage?.data) ? issuePage.data : issuePage?.data?.records || [];
  if (!issues.length) throw new Error("项目下没有工作项，无法执行用例");
  const issue = issues[0];
  console.log(`准备完毕：project=${project.id} issue=${issue.id}(${issue.issueKey})\n`);

  const browser = await chromium.launch({ headless: true });
  let createdCommentId = null;

  try {
    // ============ 用例 1：工作台快捷入口真的到得了看板 ============
    {
      const { context, page } = await freshPage(browser, ADMIN);
      try {
        await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
        const btn = page.locator(".dash-sc", { hasText: "看板视图" });
        await btn.waitFor({ state: "visible" });
        // 等数据到齐（按钮的 title 里会带上是哪个项目），否则点到的还是兜底路由
        await page
          .locator(".dash-sc[title*='打开']", { hasText: "看板视图" })
          .waitFor({ state: "visible", timeout: 15000 });
        const hint = await btn.getAttribute("title");
        await btn.click();
        await page.waitForURL(/\/projects\/\d+\/board/, { timeout: 15000 });
        record("工作台「看板视图」进入的是看板，而不是项目列表", true,
          `${page.url().replace(BASE, "")}（title=${hint}）`);
      } catch (e) {
        record("工作台「看板视图」进入的是看板，而不是项目列表", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ============ 用例 2：/issues/:id 落地页（邮件/站内通知/外部链接统一入口） ============
    {
      const { context, page } = await freshPage(browser, ADMIN);
      try {
        await page.goto(`${BASE}/issues/${issue.id}`, { waitUntil: "domcontentloaded" });
        await page.waitForURL(new RegExp(`/projects/${project.id}/board\\?issue=${issue.id}`), {
          timeout: 20000,
        });
        // 落地后详情应当真的打开（URL 参数被看板消费），而不是停在看板不弹窗
        const dialog = page.locator(".el-dialog").first();
        await dialog.waitFor({ state: "visible", timeout: 15000 });
        record("/issues/{id} 把外部链接接到看板并打开详情", true, page.url().replace(BASE, ""));
      } catch (e) {
        record("/issues/{id} 把外部链接接到看板并打开详情", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ============ 用例 3：侧边栏「设置」按 RBAC 能力显示 ============
    // 关键对照：lisi 的 users.role 是 ROLE_MEMBER，但临时给他一条 RBAC SUPER_ADMIN。
    // 修复前菜单看 role 字段 → 看不到；修复后看权限码 → 看得到。这样这条用例才有诊断力，
    // 否则"普通成员看不到设置"在修复前后都成立，等于没测。
    {
      const SUPER_ADMIN_ROLE_ID = 1;
      const LISI_USER_ID = 3;
      let memberSeesBefore = null;
      let memberSeesAfterGrant = null;
      try {
        const m1 = await freshPage(browser, MEMBER);
        await m1.page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
        await m1.page.waitForTimeout(1500); // 等权限码拉回来
        const navBefore = await m1.page.locator(".mimo-sidebar").innerText();
        memberSeesBefore = navBefore.includes("角色权限") || navBefore.includes("Webhook");
        await m1.context.close();

        // 临时授予 RBAC SUPER_ADMIN（旧字段仍是 ROLE_MEMBER）
        const adminToken2 = await apiLogin(ADMIN);
        const grant = await api(adminToken2, "POST", "/role/assign", {
          userId: LISI_USER_ID,
          roleId: SUPER_ADMIN_ROLE_ID,
          scopeType: "GLOBAL",
        });
        if (grant?.code !== 200) throw new Error("授予 RBAC 角色失败: " + JSON.stringify(grant).slice(0, 160));

        const m2 = await freshPage(browser, MEMBER);
        await m2.page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
        await m2.page.waitForTimeout(1500);
        const navAfter = await m2.page.locator(".mimo-sidebar").innerText();
        memberSeesAfterGrant = navAfter.includes("角色权限");
        await m2.context.close();

        record(
          "侧边栏「设置」按 RBAC 能力显示（旧字段 MEMBER + RBAC 管理员 → 可见）",
          memberSeesBefore === false && memberSeesAfterGrant === true,
          `授权前可见=${memberSeesBefore}，临时授予 SUPER_ADMIN 后可见=${memberSeesAfterGrant}`
        );
      } catch (e) {
        record("侧边栏「设置」按 RBAC 能力显示（旧字段 MEMBER + RBAC 管理员 → 可见）", false, e.message);
      } finally {
        // 无论成败都撤销临时授权，避免污染开发库
        try {
          const t = await apiLogin(ADMIN);
          await api(
            t,
            "DELETE",
            `/role/assign?userId=${LISI_USER_ID}&roleId=${SUPER_ADMIN_ROLE_ID}&scopeType=GLOBAL`
          );
        } catch {
          /* 撤销失败会在报告里体现为数据残留，不影响用例判定 */
        }
      }
    }

    // ============ 用例 4：@提及 通知点击直达工作项 ============
    {
      // 用 lisi 的身份在任务下 @admin，制造一条真实的 MENTION 通知
      const memberToken = await apiLogin(MEMBER);
      const comment = await api(memberToken, "POST", "/comments", {
        issueId: issue.id,
        content: `@admin 链路用例：请查看 ${issue.issueKey}`,
      });
      createdCommentId = comment?.data?.id ?? null;

      const { context, page } = await freshPage(browser, ADMIN);
      try {
        await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
        await page.locator(".mimo-header__bell").click();
        const mention = page.locator(".notif-item", { hasText: "提到了你" }).first();
        await mention.waitFor({ state: "visible", timeout: 15000 });
        await mention.click();
        await page.waitForURL(
          new RegExp(`/projects/${project.id}/board\\?issue=${issue.id}`),
          { timeout: 20000 }
        );
        record("@提及通知点击后直达工作项详情", true, page.url().replace(BASE, ""));
      } catch (e) {
        record("@提及通知点击后直达工作项详情", false, e.message);
      } finally {
        await context.close();
      }
    }

    // ============ 用例 5：审批类通知点击后到审批页（而不是回工作台） ============
    // 修复前 handleNotifClick 只认 relatedType==='ISSUE'，其余一律 router.push('/')。
    {
      let approvalId = null;
      try {
        const memberToken = await apiLogin(MEMBER);
        const created = await api(memberToken, "POST", "/approvals", {
          teamId: project.teamId,
          projectId: project.id,
          targetType: "PROJECT_UPDATE",
          title: "链路用例：项目信息确认",
          description: "e2e 清理用例，PROJECT_UPDATE 不改动业务数据",
          dataJson: JSON.stringify({ name: project.name }),
        });
        approvalId = created?.data?.id ?? null;
        if (!approvalId) throw new Error("创建审批失败: " + JSON.stringify(created).slice(0, 160));

        const admToken = await apiLogin(ADMIN);
        const approve = await api(admToken, "PUT", `/approvals/${approvalId}/approve`);
        if (approve?.code !== 200) throw new Error("审批通过失败: " + JSON.stringify(approve).slice(0, 160));

        const { context, page } = await freshPage(browser, MEMBER);
        try {
          await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
          await page.locator(".mimo-header__bell").click();
          const notif = page.locator(".notif-item", { hasText: "审批已通过" }).first();
          await notif.waitFor({ state: "visible", timeout: 15000 });
          await notif.click();
          await page.waitForURL(new RegExp(`/admin/approvals\\?id=${approvalId}`), { timeout: 20000 });
          record("审批通知点击后到审批页并高亮该请求", true, page.url().replace(BASE, ""));
        } finally {
          await context.close();
        }
      } catch (e) {
        record("审批通知点击后到审批页并高亮该请求", false, e.message);
      }
      if (approvalId) {
        console.log(`（本次用例留下一条 APPROVED 审批记录 #${approvalId}，PROJECT_UPDATE 不改动业务数据）`);
      }
    }
  } finally {
    // ---- 清理用例产生的数据 ----
    try {
      if (createdCommentId) {
        const t = await apiLogin(ADMIN);
        await api(t, "DELETE", `/comments/${createdCommentId}`);
      }
    } catch {
      /* 清理失败不影响结论 */
    }
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} 通过 ===`);
  if (failed.length) {
    console.log("失败项：\n" + failed.map((f) => ` - ${f.name}: ${f.detail}`).join("\n"));
    process.exitCode = 1;
  }
  console.log(`报告目录：${HERE}`);
}

main().catch((e) => {
  console.error("用例执行异常:", e);
  process.exitCode = 1;
});
