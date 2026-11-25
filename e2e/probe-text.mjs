/**
 * 版面探针：抬字号 / 调间距之后，量"有没有撑坏"。
 *
 * 和 theme-audit.mjs 分工：
 *   - theme-audit 量**颜色**（低对比、浅色块）
 *   - 本脚本量**版面**（小字、真裁切、高度不足、页面横向溢出）
 *
 * 为什么需要它：全局把 9–11.5px 抬到 12px 时，光看"小字数量归零"会漏掉被撑坏的容器。
 * 第一次做这轮（BUG-116）就踩了两个坑：
 *   1. 探测路径写成 `/sprints`（真实路由是 `/projects/1/sprints`），
 *      路由兜底把它重定向到 `/`，于是四个页面被量成同一个页面 —— 所以本脚本会打印
 *      实际 `location.pathname`，**和预期不一致就说明路由不对**。
 *   2. 团队详情「关联项目」的删除按钮 `opacity: 0` 但仍占 ~60px 宽度，
 *      字号一变大，项目名就被省略号吃掉 —— 探针报了"真裁切"才发现的。
 *
 * 合格线：小字 0、页面横向溢出 0、真裁切 0（长名字的正常省略号可接受，需人工确认）。
 *
 * 跑法：
 *   set PLAYWRIGHT_MODULE=...  (e2e 依赖所在的 node_modules/playwright)
 *   set PLAYWRIGHT_BROWSERS_PATH=...
 *   set NO_PROXY=localhost,127.0.0.1
 *   node e2e/probe-text.mjs http://localhost:5173
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);

const BASE = process.argv[2] || "http://localhost:5173";
const ADMIN = { username: "admin", password: "123456" };

/** 真实路由（不存在的一级路径会被路由兜底重定向，量出来是假的） */
const ROUTES = [
  ["/", "工作台"],
  ["/projects", "项目列表"],
  ["/projects/1", "项目概览"],
  ["/projects/1/board", "看板"],
  ["/projects/1/sprints", "迭代"],
  ["/projects/1/reports", "报告"],
  ["/projects/1/workflow", "工作流"],
  ["/calendar", "日历"],
  ["/profile", "个人设置"],
  ["/teams", "团队列表"],
  ["/teams/1", "团队详情"],
  ["/admin/levels", "等级管理"],
  ["/admin/roles", "角色管理"],
];

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
// 首次引导会盖住页面，探针里统一跳过
await ctx.addInitScript(() => {
  const done = { status: "done", v: 2, at: new Date().toISOString() };
  localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
});
const page = await ctx.newPage();
page.setDefaultTimeout(15000);

await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
await page.getByPlaceholder("请输入用户名").fill(ADMIN.username);
await page.getByPlaceholder("请输入密码").fill(ADMIN.password);
await page.getByRole("button", { name: /登\s*录/ }).click();
await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });

let tinyTotal = 0;
let clipTotal = 0;
let overflowPages = 0;
let mismatchedRoutes = 0;

for (const [path, name] of ROUTES) {
  try {
    await page.goto(BASE + path, { waitUntil: "domcontentloaded", timeout: 15000 });
  } catch (e) {
    console.log(`${name} 跳过（${e.name}）`);
    continue;
  }
  await page.waitForTimeout(1400);

  const r = await page.evaluate(() => {
    let tiny = 0;
    const clipped = [];
    document.querySelectorAll("body *").forEach((el) => {
      const hasText = Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
      if (!hasText) return;
      const cs = getComputedStyle(el);
      const rect = el.getBoundingClientRect();
      if (cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) < 0.15) return;
      if (rect.width < 3 || rect.height < 3) return;

      const size = parseFloat(cs.fontSize);
      const cls = String(el.className || "").trim().replace(/\s+/g, ".") || el.tagName.toLowerCase();
      if (size > 0 && size < 12) tiny++;

      const hidden = /hidden|clip/.test(cs.overflow + cs.overflowX + cs.overflowY);
      if (!hidden) return;
      const lineHeight = parseFloat(cs.lineHeight);
      const tooTall = !isNaN(lineHeight) && el.clientHeight > 0 && lineHeight > el.clientHeight + 1.5;
      const scrollOff = el.scrollHeight > el.clientHeight + 2 || el.scrollWidth > el.clientWidth + 2;
      if (tooTall || scrollOff) {
        clipped.push(`${cls} box=${el.clientWidth}x${el.clientHeight} 内容=${el.scrollWidth}x${el.scrollHeight} lh=${cs.lineHeight}`);
      }
    });
    return {
      path: location.pathname,
      tiny,
      clipped: [...new Set(clipped)].slice(0, 6),
      hOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    };
  });

  tinyTotal += r.tiny;
  clipTotal += r.clipped.length;
  if (r.hOverflow > 1) overflowPages++;
  // 路由兜底会把不存在的路径重定向到 /，量出来的就不是这个页面了
  const mismatch = r.path !== path && !(path === "/" && r.path === "/");
  if (mismatch) mismatchedRoutes++;
  console.log(`${name}(${r.path}${mismatch ? " ⚠️路由不符" : ""}) 小字=${r.tiny} 真裁切=${r.clipped.length} 溢出=${r.hOverflow}px`);
  r.clipped.forEach((x) => console.log("   裁切: " + x));
}

// 密集区域另量一次尺寸，确认 12px 装得下
await page.goto(BASE + "/calendar", { waitUntil: "domcontentloaded" });
await page.waitForTimeout(1600);
const dense = await page.evaluate(() => {
  const box = (el) =>
    el ? { w: Math.round(el.getBoundingClientRect().width), h: Math.round(el.getBoundingClientRect().height), fs: getComputedStyle(el).fontSize } : null;
  return {
    miniCalCell: box(document.querySelector(".mini-cal__cell")),
    miniCalWeekday: box(document.querySelector(".mini-cal__wd")),
    notifBadge: box(document.querySelector(".notif-badge")),
  };
});

console.log(`\n日历密集区实测: ${JSON.stringify(dense)}`);
console.log(`合计：小字=${tinyTotal} 真裁切=${clipTotal} 页面横向溢出=${overflowPages} 路由不符=${mismatchedRoutes}`);
if (tinyTotal > 0 || overflowPages > 0 || mismatchedRoutes > 0) process.exitCode = 1;

await browser.close();
