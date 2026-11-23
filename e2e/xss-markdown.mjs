/**
 * Markdown 渲染 XSS 用例（真实浏览器，不需要后端 / 不需要前端 dev server）。
 *
 * 对应 bug-log KNOWN-011（`renderMarkdown()` / 邮件正文直接进 `v-html`）。
 *
 * 做法：
 *   1. 读出仓库里真实的 `frontend/src/utils/markdown.ts`，用 esbuild 转成 JS 后**原样执行**
 *      —— 不是抄一份副本进用例，改了源码这里会跟着变；
 *   2. 把渲染结果用 `innerHTML` 塞进真实 DOM（这就是 `v-html` 的行为），
 *      用 `window.__xssFired` 哨兵判断**脚本是否真的执行了**，而不是只 grep 字符串；
 *   3. 同一条用例跑两个版本：
 *      - **修复前基线** = `git show HEAD:frontend/src/utils/markdown.ts`（正则黑名单版），
 *        只作对照、不参与退出码 —— 它本来就该挂，挂了才说明用例有诊断力；
 *      - **当前工作区源码** = 改了之后应该是 10/10，全绿才算修复完成。
 *
 * 用法: node e2e/xss-markdown.mjs
 * 退出码：当前源码全部断言通过 0，任一失败 1（可直接接 CI）。
 */
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require_ = createRequire(import.meta.url);
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(HERE, "..");
const FE = path.join(ROOT, "frontend");

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
  process.exit(2);
}

const { chromium } = loadPlaywright();
const feRequire = createRequire(path.join(FE, "package.json"));
const esbuild = feRequire("esbuild");

const MARKED_UMD = path.join(FE, "node_modules", "marked", "lib", "marked.umd.js");
const PURIFY_UMD = path.join(FE, "node_modules", "dompurify", "dist", "purify.min.js");
const MARKDOWN_REL = "frontend/src/utils/markdown.ts";
const MARKDOWN_TS = path.join(ROOT, MARKDOWN_REL);

for (const p of [MARKED_UMD, PURIFY_UMD, MARKDOWN_TS]) {
  if (!fs.existsSync(p)) {
    console.error(`缺少依赖文件：${p}`);
    process.exit(2);
  }
}

/** 把 markdown.ts 转成能在页面里 `new Function` 跑的 JS */
function toRunnableJs(tsSource, label) {
  const js = esbuild
    .transformSync(tsSource, { loader: "ts", target: "es2020" })
    .code.replace(/import\s*\{[^}]*\}\s*from\s*["']marked["'];?/, "const { marked } = window.marked;")
    .replace(/import\s+DOMPurify\s+from\s*["']dompurify["'];?/, "const DOMPurify = window.DOMPurify;")
    .replace(/^export\s+(?=(?:async\s+)?(?:function|const|let|var|class))/gm, "");
  const leftover = js.match(/^\s*import\s.+$/m);
  if (leftover) {
    console.error(`[${label}] markdown.ts 里有用例没处理的 import，先补 stub：${leftover[0].trim()}`);
    process.exit(2);
  }
  return js;
}

/** 取 git HEAD 里的旧版本 —— 修复前的对照基线 */
function readHeadVersion() {
  const gits = [process.env.GIT_EXE, "git", "C:/Users/小晟/Desktop/MIMO/ucrt64/bin/git.exe"].filter(Boolean);
  for (const g of gits) {
    try {
      const src = execFileSync(g, ["-C", ROOT, "show", `HEAD:${MARKDOWN_REL}`], { encoding: "utf8" });
      if (src && src.includes("renderMarkdown")) {
        const sha = execFileSync(g, ["-C", ROOT, "rev-parse", "--short", "HEAD"], { encoding: "utf8" }).trim();
        return { src, sha };
      }
    } catch {
      /* 试下一个 */
    }
  }
  return null;
}

const CURRENT_SRC = fs.readFileSync(MARKDOWN_TS, "utf8");

/** 读依赖版本（dompurify/marked 的 exports 不暴露 package.json，直接读文件） */
function pkgVersion(name) {
  try {
    return JSON.parse(fs.readFileSync(path.join(FE, "node_modules", name, "package.json"), "utf8")).version;
  } catch {
    return "?";
  }
}

/**
 * 载荷清单。
 * benign=true 的是回归项：换 sanitize 实现不能把正常 Markdown 弄坏。
 */
const CORPUS = [
  { id: "1 事件处理器（不带引号）", md: "<img src=x onerror=window.__xssFired=true>" },
  { id: "2 事件处理器（等号两侧带空格）", md: "<img src=x onerror = window.__xssFired=true >" },
  { id: "3 svg 自闭合 onload", md: "<svg/onload=window.__xssFired=true>" },
  { id: "4 details/ontoggle", md: "<details open ontoggle=window.__xssFired=true>展开</details>" },
  { id: "5 img title 引号注入", md: '![a"onerror="window.__xssFired=true](x)' },
  { id: "6 mXSS 解析器混淆", md: "<math><mtext><table><mglyph><style><img src=x onerror=window.__xssFired=true>" },
  { id: "7 实体编码 javascript: 链接", md: "[点我](jav&#x61;script:window.__xssFired=true)" },
  { id: "8 嵌套 script 标签", md: "<scr<script>ipt>window.__xssFired=true</scr</script>ipt>" },
  { id: "9 style 内联表达式", md: '<style>div{background:url("javascript:window.__xssFired=true")}</style>' },
  { id: "10 正常 Markdown（必须保持可用）", md: "**加粗** 与 `code`\n\n- 列表项\n\n[正常链接](https://example.com)", benign: true },
];

// playwright 版本与本地已装浏览器版本不一致时，默认会报 "Executable doesn't exist"。
// 退一步：在本机 ms-playwright 目录里找任意一个能用的 chromium 顶上，省掉一次几百 MB 的下载。
function findLocalChromium() {
  const base = path.join(process.env.LOCALAPPDATA || "", "ms-playwright");
  const found = [];
  const walk = (dir, depth) => {
    if (depth > 3) return;
    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p, depth + 1);
      else if (e.name === "chrome.exe" || e.name === "chrome-headless-shell.exe") found.push(p);
    }
  };
  walk(base, 0);
  found.sort((a, b) => Number(b.includes("headless")) - Number(a.includes("headless")));
  return found[0] || null;
}

async function launchChromium() {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) {
    return chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE });
  }
  try {
    return await chromium.launch({ headless: true });
  } catch (e) {
    const fallback = /Executable doesn't exist/.test(String(e)) ? findLocalChromium() : null;
    if (!fallback) throw e;
    console.log(`[info] 用本机已有浏览器顶上：${fallback}`);
    return chromium.launch({ headless: true, executablePath: fallback });
  }
}

const results = [];
function rec(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? " — " + detail : ""}`);
}

const browser = await launchChromium();
const page = await (await browser.newContext({ viewport: { width: 1200, height: 800 } })).newPage();

/** 渲染一批载荷，返回"是否真的执行了 / DOM 里留下什么" */
async function runPipeline(js) {
  return page.evaluate(async ({ js, corpus }) => {
    const DANGER_TAGS = ["SCRIPT", "IFRAME", "OBJECT", "EMBED", "STYLE", "META", "LINK", "BASE", "FORM"];
    // 旧版本有独立的 sanitize()，新版本合并进了 renderMarkdown，做存在性判断
    const mod = new Function(js + "\n;return { renderMarkdown, sanitize: typeof sanitize !== 'undefined' ? sanitize : undefined };")( );
    const out = [];
    for (const item of corpus) {
      const html = mod.renderMarkdown(item.md);
      const host = document.getElementById("host");
      host.innerHTML = "";
      window.__xssFired = false;
      host.innerHTML = html; // ← 与 Vue 的 v-html 完全一致的注入方式
      await new Promise((r) => setTimeout(r, 180)); // 等 img 的 error 事件真的触发

      const fired = window.__xssFired === true;
      const danger = [];
      host.querySelectorAll("*").forEach((el) => {
        for (const attr of Array.from(el.attributes)) {
          const n = attr.name.toLowerCase();
          if (n.startsWith("on")) danger.push(`<${el.tagName.toLowerCase()} ${n}>`);
          if (
            (n === "href" || n === "src" || n === "xlink:href" || n === "formaction") &&
            /^\s*(javascript|data:text\/html)/i.test(attr.value)
          ) {
            danger.push(`<${el.tagName.toLowerCase()} ${n}=${attr.value.slice(0, 28)}…>`);
          }
        }
        if (DANGER_TAGS.includes(el.tagName)) danger.push(`<${el.tagName.toLowerCase()}>`);
      });

      out.push({
        id: item.id,
        md: item.md,
        html,
        benign: !!item.benign,
        fired,
        danger: Array.from(new Set(danger)),
        keepsFormatting: item.benign
          ? /<strong>/.test(html) && /<code>/.test(html) && /<li>/.test(html) && /href="https:\/\/example\.com"/.test(html)
          : null,
      });
    }
    return out;
  }, { js, corpus: CORPUS });
}

const judge = (r) =>
  r.benign
    ? { ok: r.keepsFormatting === true && !r.fired, why: r.keepsFormatting ? "格式保留" : "格式被破坏" }
    : {
        ok: !r.fired && r.danger.length === 0,
        why: [r.fired ? "**脚本已执行**" : "", ...r.danger].filter(Boolean).join(" / ") || "已挡住",
      };

let baseline = null;
let current = null;

try {
  // 空白页即可：本用例只验证渲染 + DOM 行为，不需要真站点
  await page.setContent("<!doctype html><html><body><div id=host></div></body></html>");
  await page.addScriptTag({ path: MARKED_UMD });
  await page.addScriptTag({ path: PURIFY_UMD });

  // ---- A. 修复前基线（git HEAD），只作对照，不参与退出码 ----
  const head = readHeadVersion();
  if (head) {
    baseline = await runPipeline(toRunnableJs(head.src, "baseline"));
    console.log(`\n=== A. 修复前基线（git HEAD ${head.sha}，正则黑名单版）—— 对照用，不计入退出码 ===`);
    for (const r of baseline) {
      const j = judge(r);
      console.log(`${j.ok ? "PASS" : "FAIL"}  ${r.id}${j.why ? " — " + j.why : ""}`);
    }
    const caught = baseline.filter((r) => judge(r).ok).length;
    const executed = baseline.filter((r) => r.fired).map((r) => r.id);
    console.log(
      `基线：挡住 ${caught}/${baseline.length}；真实执行 ${executed.length} 条` +
        (executed.length ? `（${executed.join("、")}）` : "")
    );
  } else {
    console.log("\n[info] 取不到 git HEAD 的旧版本，跳过修复前基线对照");
  }

  // ---- B. 当前工作区源码 —— 这才是断言 ----
  current = await runPipeline(toRunnableJs(CURRENT_SRC, "current"));
  console.log("\n=== B. 当前工作区源码 ===");
  for (const r of current) {
    const j = judge(r);
    rec(r.id, j.ok, j.why);
  }
} catch (e) {
  rec("用例执行", false, String(e).slice(0, 200));
} finally {
  await browser.close();
}

// ---- 证据落盘（表格直接可贴 bug-log）----
try {
  const row = (r) => `| ${r.id} | ${r.fired ? "**已执行**" : "否"} | ${r.danger.join(" ") || "无"} |`;
  const lines = [
    "# Markdown XSS 取证（e2e/xss-markdown.mjs 自动生成）",
    "",
    `生成时间：${new Date().toISOString()}`,
    `被测源码：${MARKDOWN_REL}（esbuild 转译后原样执行，非副本）`,
    `运行时：Chromium（Playwright）· marked ${pkgVersion("marked")} · dompurify ${pkgVersion("dompurify")}`,
    "",
  ];
  if (baseline) {
    const caught = baseline.filter((r) => judge(r).ok).length;
    const executed = baseline.filter((r) => r.fired).length;
    lines.push(
      "## A. 修复前基线（git HEAD 的正则黑名单版）",
      "",
      `挡住 ${caught}/${baseline.length}，其中 ${executed} 条**在真实浏览器里执行了**。`,
      "",
      "| 载荷 | 是否真的执行 | DOM 里留下的危险物 |",
      "|---|---|---|",
      ...baseline.filter((r) => !r.benign).map(row),
      ""
    );
  }
  if (current) {
    lines.push(
      "## B. 当前工作区源码",
      "",
      "| 载荷 | 是否真的执行 | DOM 里留下的危险物 |",
      "|---|---|---|",
      ...current.filter((r) => !r.benign).map(row),
      "",
      `正常 Markdown 渲染保留：${current.find((r) => r.benign)?.keepsFormatting ? "是" : "否"}`,
      ""
    );
  }
  fs.writeFileSync(path.join(HERE, "xss-report.md"), lines.join("\n"), "utf8");
  console.log("\n证据已写入 e2e/xss-report.md");
} catch (e) {
  console.log(`[warn] 报告写入失败：${String(e).slice(0, 120)}`);
}

const gating = results.filter((r) => !r.baselineOnly);
const passed = gating.filter((r) => r.ok).length;
console.log(`\n通过 ${passed}/${gating.length}`);
process.exit(passed === gating.length ? 0 : 1);
