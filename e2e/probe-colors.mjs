/**
 * 一次性小工具：盯住两个具体问题
 *   1. el-alert 描述文字的实际颜色 + 我们那条规则到底有没有进样式表
 *   2. 页面上小于 12px 的文字都有哪些（判断"字太小"的分布）
 *
 * 跑法：node e2e/probe-colors.mjs http://localhost:5173 levels
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const BASE = process.argv[2] || "http://localhost:5173";
const PAGE = process.argv[3] || "levels";

const PATHS = { levels: "/admin/levels", board: "/projects/1/board", dash: "/" };

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(() => {
  const done = { status: "done", v: 2, at: new Date().toISOString() };
  localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
});
const page = await context.newPage();

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
await page.getByPlaceholder("请输入用户名").fill("admin");
await page.getByPlaceholder("请输入密码").fill("123456");
await page.getByRole("button", { name: /登\s*录/ }).click();
await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });

await page.goto(`${BASE}${PATHS[PAGE] || PAGE}`, { waitUntil: "domcontentloaded" });
await page.waitForTimeout(1800);

const report = await page.evaluate(() => {
  const out = { alert: null, rules: [], tiny: [] };

  const desc = document.querySelector(".el-alert__description");
  if (desc) {
    const cs = getComputedStyle(desc);
    out.alert = { tag: desc.tagName, cls: desc.className, color: cs.color };
  }

  // 我们写的那条规则在不在样式表里
  for (const sheet of document.styleSheets) {
    let rules;
    try {
      rules = sheet.cssRules;
    } catch {
      continue;
    }
    for (const r of rules) {
      if (r.selectorText && r.selectorText.includes("el-alert__description")) {
        out.rules.push({ selector: r.selectorText, css: r.style.cssText.slice(0, 90) });
      }
    }
  }

  // 小于 12px 的文字
  const seen = new Set();
  for (const el of document.querySelectorAll("body *")) {
    const hasText = Array.from(el.childNodes).some(
      (n) => n.nodeType === 3 && n.textContent.trim().length > 1
    );
    if (!hasText) continue;
    const cs = getComputedStyle(el);
    const px = parseFloat(cs.fontSize);
    if (px >= 12) continue;
    const cls = typeof el.className === "string" ? el.className.split(/\s+/)[0] : "";
    const key = `${cls}|${cs.fontSize}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.tiny.push({
      sel: `${el.tagName.toLowerCase()}.${cls}`,
      size: cs.fontSize,
      text: (el.textContent || "").trim().slice(0, 18),
    });
  }
  return out;
});

console.log("alert:", JSON.stringify(report.alert));
console.log("\n匹配到 el-alert__description 的规则：");
for (const r of report.rules) console.log(`  ${r.selector} { ${r.css} }`);
console.log("\n小于 12px 的文字：");
for (const t of report.tiny) console.log(`  ${t.size} ${t.sel} 「${t.text}」`);

await browser.close();
