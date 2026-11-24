/**
 * 一次性小工具：找"排版丑"的客观证据。
 *
 * 判两类：
 *   1. 横向溢出：整页出现横向滚动条（documentElement.scrollWidth > clientWidth）
 *   2. 文字被裁：文字放不下、又没有省略号（scrollWidth > clientWidth 且 text-overflow 不是 ellipsis）
 *
 * 跑法：node e2e/probe-layout.mjs http://localhost:5173
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const BASE = process.argv[2] || "http://localhost:5173";
const PAGES = [
  ["工作台", "/"],
  ["项目列表", "/projects"],
  ["项目概览", "/projects/1"],
  ["看板", "/projects/1/board"],
  ["迭代", "/projects/1/sprints"],
  ["甘特", "/projects/1/gantt"],
  ["迭代分析", "/projects/1/analytics"],
  ["报告", "/projects/1/reports"],
  ["工作流", "/projects/1/workflow"],
  ["日历", "/calendar"],
  ["个人设置", "/profile"],
  ["团队详情", "/teams/1"],
  ["等级管理", "/admin/levels"],
  ["审批", "/admin/approvals"],
  ["审计日志", "/admin/audit-logs"],
];

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(() => {
  const done = { status: "done", v: 2, at: new Date().toISOString() };
  localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
});
const page = await context.newPage();
page.setDefaultTimeout(25000);

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
await page.getByPlaceholder("请输入用户名").fill("admin");
await page.getByPlaceholder("请输入密码").fill("123456");
await page.getByRole("button", { name: /登\s*录/ }).click();
await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });

let totalHScroll = 0;
let totalClipped = 0;

for (const [label, path] of PAGES) {
  try {
    await page.goto(`${BASE}${path}`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(1500);
    const r = await page.evaluate(() => {
      const de = document.documentElement;
      const hScroll = de.scrollWidth - de.clientWidth;

      const clipped = [];
      for (const el of document.querySelectorAll("body *")) {
        const hasText = Array.from(el.childNodes).some(
          (n) => n.nodeType === 3 && n.textContent.trim().length > 2
        );
        if (!hasText) continue;
        const cs = getComputedStyle(el);
        if (cs.overflow !== "hidden") continue;
        if (cs.textOverflow === "ellipsis") continue;
        const cut = el.scrollWidth - el.clientWidth;
        if (cut > 2) {
          const cls = typeof el.className === "string" ? el.className.split(/\s+/)[0] : "";
          clipped.push({
            sel: `${el.tagName.toLowerCase()}.${cls}`,
            cut,
            text: (el.textContent || "").trim().slice(0, 18),
          });
        }
      }
      return { hScroll, clipped };
    });

    totalHScroll += Math.max(0, r.hScroll);
    totalClipped += r.clipped.length;
    const flag = r.hScroll > 1 || r.clipped.length ? "!" : " ";
    console.log(
      `${flag} ${label.padEnd(6, "　")} 横向溢出=${String(Math.max(0, r.hScroll)).padStart(3)}px 文字被裁=${String(
        r.clipped.length
      ).padStart(2)}`
    );
    for (const c of r.clipped.slice(0, 4)) {
      console.log(`      ${c.sel} 裁掉 ${c.cut}px 「${c.text}」`);
    }
  } catch (e) {
    console.log(`  ${label} 检查失败：${e.message}`);
  }
}

console.log(`\n合计：横向溢出 ${totalHScroll}px，文字被裁 ${totalClipped} 处`);
await browser.close();
