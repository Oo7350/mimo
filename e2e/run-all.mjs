/**
 * 一条命令跑完全部 e2e 套件。
 *
 * 为什么有它：以前每次回归要手敲 7~12 条命令，还得记住每个脚本吃的参数不一样
 * （浏览器套件吃前端地址，`authz.mjs` 吃的是 API 地址），漏跑一套自己都不知道。
 * 对应用 bug-log 里的 KNOWN-024「e2e 未接 CI」。
 *
 * 用法：
 *   node e2e/run-all.mjs                                   # 跑全部套件
 *   node e2e/run-all.mjs --probes                           # 连版面/配色探针一起跑
 *   node e2e/run-all.mjs --only admin-flow,review-matrix    # 只跑指定几套
 *   node e2e/run-all.mjs --base http://localhost:5173 --api http://localhost:8080/api
 *
 * 退出码：全部通过 = 0，有失败 = 1（可以直接当 CI 的判据）。
 *
 * 依赖：后端 :8080、前端 :5173 已启动；PLAYWRIGHT_MODULE / PLAYWRIGHT_BROWSERS_PATH 指向装好的 playwright。
 * 注意：套件里有一部分会注册临时账号 / 造测试数据，跑完请执行 e2e/cleanup-residue.sql。
 */
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const opt = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

const BASE = opt("--base", "http://localhost:5173");
const API = opt("--api", "http://localhost:8080/api");
const only = opt("--only", "");
const withProbes = flag("--probes");

/**
 * 套件清单。
 * 顺序有意义：主链路在前（它挂了，后面的意义就不大），接口级越权在最后（最慢也最独立）。
 */
const SUITES = [
  { file: "main-chain.mjs", name: "主链路", arg: BASE, kind: "suite" },
  { file: "core-flows.mjs", name: "核心流程", arg: BASE, kind: "suite" },
  { file: "review-matrix.mjs", name: "验收矩阵", arg: BASE, kind: "suite" },
  { file: "next-step.mjs", name: "下一步建议", arg: BASE, kind: "suite" },
  { file: "chain-links.mjs", name: "链路口径", arg: BASE, kind: "suite" },
  { file: "workflow-guard.mjs", name: "工作流守卫", arg: BASE, kind: "suite" },
  { file: "admin-flow.mjs", name: "管理员视角", arg: BASE, kind: "suite" },
  { file: "presence.mjs", name: "在线状态", arg: BASE, kind: "suite" },
  // 在线人数的"口径"是否写在界面上（KNOWN-002）：用拦截接口覆盖"仅本实例/跨实例"两种口径
  { file: "presence-source-label.mjs", name: "在线口径文案", arg: BASE, kind: "suite" },
  { file: "tour.mjs", name: "新手引导", arg: BASE, kind: "suite" },
  { file: "ai-degrade.mjs", name: "AI 降级", arg: BASE, kind: "suite" },
  { file: "xss-markdown.mjs", name: "Markdown XSS", arg: BASE, kind: "suite" },
  { file: "authz.mjs", name: "接口越权", arg: API, kind: "suite" },
  { file: "probe-text.mjs", name: "版面探针", arg: BASE, kind: "probe" },
  { file: "probe-layout.mjs", name: "布局探针", arg: BASE, kind: "probe" },
  { file: "theme-audit.mjs", name: "配色审计", arg: BASE, kind: "probe" },
];

const selected = SUITES.filter((s) => {
  if (s.kind === "probe" && !withProbes) return false;
  if (!only) return true;
  return only.split(",").map((x) => x.trim()).some((x) => x === s.file || x === s.file.replace(".mjs", ""));
});

if (!selected.length) {
  console.error(`没有匹配到套件（--only ${only}）`);
  process.exit(1);
}

/** 套件各自打印风格不同，这里把两种都认了：`=== 19/19 通过 ===` 和 `通过 25/25` */
function parseCounts(text) {
  const patterns = [/通过\s*(\d+)\s*\/\s*(\d+)/g, /(\d+)\s*\/\s*(\d+)\s*通过/g];
  let passed = 0;
  let total = 0;
  for (const re of patterns) {
    let m;
    while ((m = re.exec(text)) !== null) {
      passed += Number(m[1]);
      total += Number(m[2]);
    }
    if (total > 0) break;
  }
  return { passed, total };
}

function run(suite) {
  return new Promise((resolve) => {
    const started = Date.now();
    const child = spawn(process.execPath, [join(HERE, suite.file), suite.arg], {
      cwd: join(HERE, ".."),
      env: process.env,
      // 输出要抓回来统计，所以这里必须 pipe —— 沙箱下若报 EPERM，说明不允许捕获子进程输出
      stdio: ["ignore", "pipe", "pipe"],
    });
    let out = "";
    child.stdout.on("data", (d) => {
      out += d.toString();
    });
    child.stderr.on("data", (d) => {
      out += d.toString();
    });
    child.on("close", (code) => {
      resolve({ code: code ?? 1, out, ms: Date.now() - started });
    });
    child.on("error", (e) => {
      resolve({ code: 1, out: `启动失败：${e.message}`, ms: Date.now() - started });
    });
  });
}

const results = [];
console.log(`跑 ${selected.length} 项：${selected.map((s) => s.name).join(" / ")}`);
console.log(`前端 ${BASE}${withProbes ? `｜探针已启用` : ""}｜接口 ${API}\n`);

for (const suite of selected) {
  process.stdout.write(`▶ ${suite.name}（${suite.file}）... `);
  const r = await run(suite);
  const counts = parseCounts(r.out);
  const ok = r.code === 0;
  console.log(`${ok ? "通过" : "失败"} ${counts.total ? counts.passed + "/" + counts.total : ""}（${Math.round(r.ms / 1000)}s）`);
  if (!ok) {
    // 失败时把尾巴打出来，别让人再去翻日志
    const tail = r.out.trim().split("\n").slice(-25).join("\n");
    console.log("  ── 输出尾部 ──");
    console.log(
      tail
        .split("\n")
        .map((l) => "  " + l)
        .join("\n")
    );
  }
  results.push({ ...suite, ...r, ...counts, ok });
}

const suiteResults = results.filter((r) => r.kind === "suite");
const probeResults = results.filter((r) => r.kind === "probe");
const failed = results.filter((r) => !r.ok);
const totalChecks = suiteResults.reduce((n, r) => n + r.total, 0);
const passedChecks = suiteResults.reduce((n, r) => n + r.passed, 0);

console.log("\n=== 汇总 ===");
console.log(`套件：${suiteResults.filter((r) => r.ok).length}/${suiteResults.length} 通过，检查点 ${passedChecks}/${totalChecks}`);
if (probeResults.length) {
  console.log(`探针：${probeResults.filter((r) => r.ok).length}/${probeResults.length} 通过`);
}
if (failed.length) {
  console.log(`失败项：${failed.map((r) => r.name).join("、")}`);
  process.exitCode = 1;
} else {
  console.log("全部通过");
}

// 造过数据就提醒一句，别让残留越积越多
if (!only || only.includes("admin-flow") || only.includes("authz") || only.includes("core-flows")) {
  console.log("提示：本机若跑过 admin-flow / core-flows / authz，执行 e2e/cleanup-residue.sql 清残留账号。");
}
