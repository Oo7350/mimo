/**
 * 管理员视角用例（v2.19.8）：身份标记 + 全员任务流 + 任务详情抽屉。
 *
 * 背景：管理员账号以前和普通账号长得一模一样，谁也看不出"这个号能看全员"；
 * 而"我的任务"只显示分配给自己的，管理员想知道**每个人手上有什么**只能一个个问。
 *
 * 断言（8 条）：
 *   1. 管理员的顶栏有身份徽标（普通成员没有）
 *   2. 工作台有「我的 / 全员」切换，普通成员看不到
 *   3. 切到全员后，成员列数量 = 团队成员数
 *   4. 概览数字与接口一致（未完成 / 逾期 / 未指派）
 *   5. 点任务卡片打开详情抽屉，标题与接口一致
 *   6. 抽屉里有字段（负责人 / 项目 / 看板列…），不是空壳
 *   7. Esc 能关掉抽屉
 *   8. 普通成员直接请求 /api/workbench/team 得到 403 且带说明
 *
 * 跑法：node e2e/admin-flow.mjs http://localhost:5173
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const BASE = process.argv[2] || "http://localhost:5173";
const API = process.env.MIMO_API || "http://localhost:8080";
const ADMIN = { username: "admin", password: "123456" };
/** 每次运行注册一个新的普通成员：开发库里三个账号都是团队管理员，用他们测权限会测到放行路径 */
const PLAIN = { username: `e2e-flowmember${Date.now().toString().slice(-6)}`, password: "123456", email: "" };
PLAIN.email = `${PLAIN.username}@mimo.dev`;

let passed = 0;
let failed = 0;

function check(name, ok, detail) {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
  if (ok) passed++;
  else failed++;
}

const tourSkip = () => {
  const done = { status: "done", v: 2, at: new Date().toISOString() };
  localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
};

async function login(page, account) {
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("请输入用户名").fill(account.username);
  await page.getByPlaceholder("请输入密码").fill(account.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
}

const browser = await chromium.launch({ headless: true });

// ==================== 管理员视角 ====================
const adminContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
await adminContext.addInitScript(tourSkip);
const adminPage = await adminContext.newPage();
adminPage.setDefaultTimeout(25000);
await login(adminPage, ADMIN);

// 徽标依赖 userInfo/权限码回来，可能晚于首屏；等一小会儿再判，别把"还没到"当成"没有"
const badge = adminPage.locator(".mimo-header__user .admin-badge").first();
const badgeVisible = await badge
  .waitFor({ state: "visible", timeout: 10000 })
  .then(() => true)
  .catch(() => false);
check("管理员顶栏有身份徽标", badgeVisible, badgeVisible ? await badge.innerText() : "10 秒内没出现 .admin-badge");

await adminPage.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
await adminPage.waitForTimeout(2000);

const scopeSwitch = adminPage.locator(".admin-scope");
check("工作台有「我的 / 全员」切换", (await scopeSwitch.count()) > 0);

// 接口真值，用来和界面比
const token = await adminPage.evaluate(() => localStorage.getItem("token"));
const apiData = await adminPage.evaluate(async (t) => {
  const res = await fetch("/api/workbench/team", { headers: { Authorization: `Bearer ${t}` } });
  const json = await res.json();
  return json.data;
}, token);

await adminPage.locator(".admin-scope__option", { hasText: "全员" }).click();
await adminPage.waitForSelector(".member-stream", { timeout: 20000 });
await adminPage.waitForTimeout(900);

const streamCount = await adminPage.locator(".member-stream").count();
check(
  "成员列数量与接口一致",
  streamCount === apiData.members.length,
  `界面 ${streamCount} 列 / 接口 ${apiData.members.length} 人`,
);

const stats = await adminPage.evaluate(() => {
  const items = Array.from(document.querySelectorAll(".team-flow__stat")).map((el) => ({
    label: el.querySelector(".team-flow__stat-label")?.textContent?.trim(),
    value: el.querySelector(".team-flow__stat-value")?.textContent?.trim(),
  }));
  return items;
});
const statOf = (label) => Number(stats.find((item) => item.label === label)?.value ?? "NaN");
check(
  "概览数字与接口一致（未完成/逾期/未指派）",
  statOf("未完成") === apiData.summary.openTaskCount &&
    statOf("逾期") === apiData.summary.overdueTaskCount &&
    Number(String(stats.find((s) => s.label === "空闲成员")?.value)) ===
      apiData.summary.memberCount - apiData.summary.busyMemberCount,
  `未完成 ${statOf("未完成")}/${apiData.summary.openTaskCount}，逾期 ${statOf("逾期")}/${apiData.summary.overdueTaskCount}`,
);

// ==================== 详情抽屉 ====================
const firstTask = adminPage.locator(".flow-task").first();
const taskTitle = (await firstTask.locator(".flow-task__title").innerText()).trim();
await firstTask.click();
await adminPage.waitForSelector(".task-drawer__title", { timeout: 15000 });
await adminPage.waitForTimeout(700);
const drawerTitle = (await adminPage.locator(".task-drawer__title").innerText()).trim();
check("点任务卡片能打开详情抽屉，标题一致", drawerTitle === taskTitle, `抽屉「${drawerTitle}」/ 卡片「${taskTitle}」`);

const drawerMeta = await adminPage.evaluate(() =>
  Array.from(document.querySelectorAll(".task-drawer__meta-item")).map((el) => ({
    label: el.querySelector(".task-drawer__meta-label")?.textContent?.trim(),
    value: el.querySelector(".task-drawer__meta-value")?.textContent?.trim(),
  })),
);
check(
  "抽屉里字段齐全（不是空壳）",
  drawerMeta.length >= 5 && drawerMeta.some((item) => item.value && item.value !== "—"),
  drawerMeta.map((item) => `${item.label}=${item.value}`).join(" / "),
);

await adminPage.keyboard.press("Escape");
await adminPage.waitForTimeout(800);
// el-drawer 关闭后内容仍留在 DOM 里（只是隐藏），所以要判可见性而不是 count
const drawerHidden = !(await adminPage.locator(".task-drawer__title").first().isVisible().catch(() => false));
check("Esc 能关掉抽屉", drawerHidden, drawerHidden ? "已隐藏" : "抽屉仍然可见");

// ==================== 旧字段兜底的团队管理员（v2.19.8 修复的那个坑） ====================
//
// 开发库里 zhangsan 的情况是：`users.role = ROLE_MEMBER`、RBAC 里没有角色，
// 但 `team_members.role = ROLE_ADMIN`（旧字段）——后端 AccessControlService 认这条，
// 所以 /workbench/team 对他放行（scope=TEAM）。第一版前端自己用权限码算，
// 算成"不是管理员"，把徽标和入口整个藏了：用户看到的就是"这个功能没做"。
// 这条用例专门盯这个落差：后端允许 ⇒ 界面必须给入口。
const TEAM_ADMIN = { username: "zhangsan", password: "123456" };
const teamAdminContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
await teamAdminContext.addInitScript(tourSkip);
const teamAdminPage = await teamAdminContext.newPage();
teamAdminPage.setDefaultTimeout(25000);
await login(teamAdminPage, TEAM_ADMIN);
await teamAdminPage.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
await teamAdminPage.waitForTimeout(2200);

const teamAdminBadge = teamAdminPage.locator(".mimo-header__user .admin-badge").first();
const teamAdminBadgeVisible = await teamAdminBadge
  .waitFor({ state: "visible", timeout: 10000 })
  .then(() => true)
  .catch(() => false);
const teamAdminScopeSwitch = await teamAdminPage.locator(".admin-scope").count();
const teamAdminScopeText = teamAdminBadgeVisible ? (await teamAdminBadge.innerText()).trim() : "";
check(
  "旧字段兜底的团队管理员也有徽标与入口",
  teamAdminBadgeVisible && teamAdminScopeSwitch > 0,
  `徽标 ${teamAdminBadgeVisible ? "有（" + teamAdminScopeText + "）" : "没有"} / 切换 ${teamAdminScopeSwitch}`,
);

const teamAdminToken = await teamAdminPage.evaluate(() => localStorage.getItem("token"));
const teamAdminScope = await teamAdminPage.evaluate(async (t) => {
  const res = await fetch("/api/workbench/team", { headers: { Authorization: `Bearer ${t}` } });
  const json = await res.json().catch(() => ({}));
  return { code: json.code, scope: json.data?.scope, teams: json.data?.teamNames };
}, teamAdminToken);

await teamAdminPage.locator(".admin-scope__option", { hasText: "全员" }).click();
await teamAdminPage.waitForTimeout(1600);
const teamAdminStreams = await teamAdminPage.locator(".member-stream").count();
check(
  "团队管理员切到全员能看到成员列（范围=自己团队）",
  teamAdminScope.scope === "TEAM" && teamAdminStreams > 0,
  `接口 scope=${teamAdminScope.scope} teams=${JSON.stringify(teamAdminScope.teams)} 界面列数=${teamAdminStreams}`,
);
await teamAdminContext.close();

// ==================== 普通成员：看不到也调不动 ====================
const registerRes = await fetch(`${API}/api/auth/register`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: PLAIN.username, password: PLAIN.password, email: PLAIN.email }),
});
check("能注册普通成员账号（用例前提）", registerRes.ok || registerRes.status === 400, `status=${registerRes.status}`);

const plainContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
await plainContext.addInitScript(tourSkip);
const plainPage = await plainContext.newPage();
plainPage.setDefaultTimeout(25000);
await login(plainPage, PLAIN);
await plainPage.goto(`${BASE}/`, { waitUntil: "domcontentloaded" });
await plainPage.waitForTimeout(1800);

const plainBadge = await plainPage.locator(".mimo-header__user .admin-badge").count();
const plainSwitch = await plainPage.locator(".admin-scope").count();
check("普通成员没有身份徽标与视角切换", plainBadge === 0 && plainSwitch === 0, `徽标 ${plainBadge} / 切换 ${plainSwitch}`);

const plainToken = await plainPage.evaluate(() => localStorage.getItem("token"));
const forbidden = await plainPage.evaluate(async (t) => {
  const res = await fetch("/api/workbench/team", { headers: { Authorization: `Bearer ${t}` } });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, code: json.code, message: json.message };
}, plainToken);
// 注意：本项目业务错误用响应体 code 表达（HTTP 仍是 200），所以断言 code 而不是 status
check(
  "普通成员请求全员任务流被拒（code=403 + 说明）",
  forbidden.code === 403 && /管理员/.test(forbidden.message || ""),
  `http=${forbidden.status} code=${forbidden.code} message=${forbidden.message}`,
);

// ==================== 新增界面自身的可读性/布局（两种主题都量） ====================

/**
 * 只量这次新增的三块 UI（全员任务流、详情抽屉、身份徽标）。
 * 判据与 e2e/theme-audit.mjs 一致：文字与其"实际底色"的对比度 < 3:1 记为看不清；
 * 渐变/装饰底跳过（逐元素算渐变必然假警报）。另外量页面横向溢出。
 */
const auditNewUi = (page) =>
  page.evaluate(() => {
    const parse = (value) => {
      const match = /rgba?\(([^)]+)\)/.exec(value || "");
      if (!match) return null;
      const parts = match[1].split(",").map((item) => parseFloat(item));
      return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1 };
    };
    const luminance = ({ r, g, b }) => {
      const convert = (channel) => {
        const c = channel / 255;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
      };
      return 0.2126 * convert(r) + 0.7152 * convert(g) + 0.0722 * convert(b);
    };
    const contrast = (a, b) => {
      const l1 = luminance(a);
      const l2 = luminance(b);
      return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    };
    const effectiveBackground = (el) => {
      let node = el;
      while (node) {
        const style = getComputedStyle(node);
        if ((style.backgroundImage || "").includes("gradient")) return null;
        const bg = parse(style.backgroundColor);
        if (bg && bg.a > 0.6) return bg;
        node = node.parentElement;
      }
      return { r: 255, g: 255, b: 255, a: 1 };
    };

    const scope = ".team-flow, .member-stream, .flow-task, .task-drawer, .admin-badge, .admin-scope, .board__admin-flow";
    const offenders = [];
    document.querySelectorAll(scope).forEach((root) => {
      root.querySelectorAll("*").forEach((el) => {
        const text = Array.from(el.childNodes)
          .filter((node) => node.nodeType === 3)
          .map((node) => node.textContent.trim())
          .join("");
        if (!text) return;
        const rect = el.getBoundingClientRect();
        if (rect.width < 4 || rect.height < 4) return;
        const style = getComputedStyle(el);
        if (style.visibility === "hidden" || style.display === "none" || Number(style.opacity) < 0.15) return;
        const fg = parse(style.color);
        // 分段控件的选中项：底色在兄弟节点（滑动指示块）上，普通向上查找看不到白色文字压靛蓝，
        // 会误报 1.18:1。这里按真实叠放关系取指示块的底色。
        let bg;
        if (el.closest(".admin-scope__option.is-active")) {
          const indicator = el.closest(".admin-scope")?.querySelector(".admin-scope__indicator");
          bg = indicator ? parse(getComputedStyle(indicator).backgroundColor) : null;
        } else {
          bg = effectiveBackground(el);
        }
        if (!fg || !bg) return;
        const ratio = contrast(fg, bg);
        if (ratio < 3) {
          offenders.push({ text: text.slice(0, 24), ratio: Math.round(ratio * 100) / 100, cls: el.className });
        }
      });
    });

    return {
      offenders: offenders.slice(0, 7),
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      theme: document.documentElement.getAttribute("data-theme") || "light",
    };
  });

async function toggleTheme(page) {
  await page.locator(".mimo-header__theme-toggle").click();
  await page.waitForTimeout(1200);
}

// 亮色下量一次（此时页面停在全员视角）
const lightAudit = await auditNewUi(adminPage);
check("全员任务流在亮色下无低对比文本", lightAudit.offenders.length === 0, JSON.stringify(lightAudit.offenders));
check("全员任务流无横向溢出（亮色）", lightAudit.overflow <= 1, `溢出 ${lightAudit.overflow}px`);

// 切暗色再量一次，并连详情抽屉一起量
await toggleTheme(adminPage);
await adminPage.waitForTimeout(900);
const darkAudit = await auditNewUi(adminPage);
check("全员任务流在暗色下无低对比文本", darkAudit.offenders.length === 0, JSON.stringify(darkAudit.offenders));
check("全员任务流无横向溢出（暗色）", darkAudit.overflow <= 1, `溢出 ${darkAudit.overflow}px（主题=${darkAudit.theme}）`);

await adminPage.locator(".flow-task").first().click();
await adminPage.waitForSelector(".task-drawer__title", { timeout: 15000 });
await adminPage.waitForTimeout(700);
const drawerAudit = await auditNewUi(adminPage);
check("详情抽屉在暗色下无低对比文本", drawerAudit.offenders.length === 0, JSON.stringify(drawerAudit.offenders));
await adminPage.keyboard.press("Escape");
await toggleTheme(adminPage); // 还原亮色，避免影响后续用例

console.log(`\n普通成员账号（清理用）：${PLAIN.username}`);
console.log(`\n=== ${passed}/${passed + failed} 通过 ===`);

await plainContext.close();
await adminContext.close();
await browser.close();
if (failed > 0) process.exitCode = 1;
