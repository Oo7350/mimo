/**
 * 配色体检（暗色模式 / 亮色模式）——把"颜色逻辑有问题"变成可复现的数字。
 *
 * 起因：KNOWN-021「硬编码颜色残留」。真正麻烦的不是"代码里有个 #fff"，
 * 而是**暗色模式下仍然出现浅色块、文字和底色撞在一起**。靠肉眼看页面容易漏，
 * 所以这里直接量浏览器算出来的样式：
 *
 *   1. 浅色块残留（仅暗色模式判）：元素自身可见面积够大（>800px²）、且"实际底色"
 *      亮度 > 0.55（近白）——说明暗色主题下还画了一块浅色区域。
 *   2. 文字对比度不足：元素文字色与"实际底色"的对比度 < 3:1（WCAG AA 大字号下限，
 *      小字号要求 4.5，这里用 3 是"明显有问题"的下限，避免误报一堆次要文案）。
 *
 * 跑法：
 *   node e2e/theme-audit.mjs http://localhost:5173            # 两种模式都查
 *   node e2e/theme-audit.mjs http://localhost:5173 dark       # 只查暗色
 *   node e2e/theme-audit.mjs http://localhost:5173 light
 *
 * 退出码：发现"浅色块残留"就返回 1（这个属于硬伤），只有对比度问题返回 0 并提示。
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const requirePlaywright = () => {
  const mod = process.env.PLAYWRIGHT_MODULE || "playwright";
  return require(mod);
};

const BASE = process.argv[2] || "http://localhost:5173";
const ONLY = (process.argv[3] || "both").toLowerCase();
const ADMIN = { username: "admin", password: "123456" };

/** 重点页面：把"用户真的会打开"的页面都过一遍 */
const PAGES = [
  ["工作台", "/"],
  ["项目列表", "/projects"],
  ["项目概览", "/projects/1"],
  ["看板", "/projects/1/board"],
  ["迭代", "/projects/1/sprints"],
  ["迭代详情", "/projects/1/analytics"],
  ["报告", "/projects/1/reports"],
  ["工作流", "/projects/1/workflow"],
  ["日历", "/calendar"],
  ["个人设置", "/profile"],
  ["团队列表", "/teams"],
  ["团队详情", "/teams/1"],
  ["等级管理", "/admin/levels"],
  ["角色管理", "/admin/roles"],
];

function record(scope, name, ok, detail) {
  console.log(`${ok ? "PASS" : "FAIL"}  [${scope}] ${name} — ${detail}`);
  return ok;
}

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("请输入用户名").fill(ADMIN.username);
  await page.getByPlaceholder("请输入密码").fill(ADMIN.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
}

/** 页面内执行的体检函数（注意：会被序列化，不能引用外部变量） */
function auditInPage(MODE) {
  const parseColor = (input) => {
    if (!input) return null;
    const s = String(input).trim();
    if (s === "transparent") return [0, 0, 0, 0];
    let m = s.match(/^rgba?\(([^)]+)\)$/i);
    if (m) {
      const parts = m[1].split(/[,\s/]+/).filter(Boolean).map(Number);
      return [parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 1];
    }
    m = s.match(/^#([0-9a-f]{3,8})$/i);
    if (m) {
      const h = m[1];
      const to = (i) => parseInt(h.slice(i, i + 2), 16);
      if (h.length === 3) {
        return [
          parseInt(h[0] + h[0], 16),
          parseInt(h[1] + h[1], 16),
          parseInt(h[2] + h[2], 16),
          1,
        ];
      }
      return [to(0), to(2), to(4), h.length === 8 ? to(6) / 255 : 1];
    }
    if (s === "white") return [255, 255, 255, 1];
    if (s === "black") return [0, 0, 0, 1];
    return null;
  };

  const relLum = ([r, g, b]) => {
    const f = (v) => {
      const x = v / 255;
      return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };

  const contrast = (a, b) => {
    const la = relLum(a) + 0.05;
    const lb = relLum(b) + 0.05;
    return la > lb ? la / lb : lb / la;
  };

  /** 往上找"看得见的底色"：自己没画底色就问父级，直到不透明或到 html */
  const effectiveBg = (el) => {
    let node = el;
    while (node && node.nodeType === 1) {
      const cs = getComputedStyle(node);
      const c = parseColor(cs.backgroundColor);
      if (c && c[3] > 0.85) return c;
      if (c && c[3] > 0.05) {
        // 半透明底：和父级再叠一次（近似，够判断"是不是浅色块"）
        const parent = node.parentElement;
        const pc = parent ? effectiveBg(parent) : [255, 255, 255, 1];
        const a = c[3];
        return [
          c[0] * a + pc[0] * (1 - a),
          c[1] * a + pc[1] * (1 - a),
          c[2] * a + pc[2] * (1 - a),
          1,
        ];
      }
      node = node.parentElement;
    }
    return [255, 255, 255, 1];
  };

  const describe = (el) => {
    const cls = (el.className && typeof el.className === "string" ? el.className : "")
      .split(/\s+/)
      .filter((c) => c && !c.startsWith("el-") === false ? true : Boolean(c))
      .slice(0, 3)
      .join(".");
    const tag = el.tagName.toLowerCase();
    return cls ? `${tag}.${cls}` : tag;
  };

  const lightSurfaces = [];
  const lowContrast = [];
  const tinyText = [];
  const all = document.querySelectorAll("body *");

  for (const el of all) {
    const cs = getComputedStyle(el);
    if (cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) < 0.1) continue;
    const rect = el.getBoundingClientRect();
    if (rect.width < 24 || rect.height < 12) continue;
    if (rect.bottom < 0 || rect.top > window.innerHeight * 4) continue; // 只看首屏附近
    const area = rect.width * rect.height;
    const bg = effectiveBg(el);

    const ownBg = parseColor(cs.backgroundColor);
    const looksLikeSurface = ownBg && ownBg[3] > 0.5;

    // 渐变背景取不到 backgroundColor（读到的是 transparent/默认色），
    // 硬算会得出"白字压在白底上 1:1"这种假警报，所以带渐变的元素直接跳过对比度判定。
    // 渐变/图片底取不到 backgroundColor，硬算会得出"白字压在白底 1:1"这类假警报。
    // 判定范围：自身 + 向上 5 层祖先，外加祖先里那些 hero/bg/gradient 装饰层（它们通常和文字同级）。
    const hasGradient = (node) => {
      let n = node;
      for (let i = 0; i < 5 && n && n.nodeType === 1; i++, n = n.parentElement) {
        if (getComputedStyle(n).backgroundImage !== "none") return true;
        for (const child of n.children) {
          const cls = typeof child.className === "string" ? child.className : "";
          if (!/hero|bg|gradient/i.test(cls)) continue;
          if (getComputedStyle(child).backgroundImage !== "none") return true;
        }
      }
      return false;
    };
    const gradientBg = hasGradient(el);

    // hero/banner 这类整块装饰背景的容器，文字压在上面，但底色画在同级图层上，
    // 逐元素算出来必然是"白字压白底"的假警报，这些区域只做人工检查。
    const inDecorated = (() => {
      let n = el;
      for (let i = 0; i < 6 && n && n.nodeType === 1; i++, n = n.parentElement) {
        const cls = typeof n.className === "string" ? n.className : "";
        if (/hero|banner|cover|gradient/i.test(cls)) return true;
      }
      return false;
    })();

    // 1) 浅色块残留：自己画了一块近白底，且面积不小
    //    只在暗色模式下判——亮色主题本来就该是浅色。
    if (MODE === "dark" && looksLikeSurface && relLum(bg) > 0.55 && area > 800) {
      lightSurfaces.push({
        sel: describe(el),
        bg: cs.backgroundColor,
        area: Math.round(area),
      });
    }

    // 1.5) 字号过小：小于 12px 的中文正文在 1440 宽下已经开始费眼
    const hasDirectText = Array.from(el.childNodes).some(
      (n) => n.nodeType === 3 && n.textContent.trim().length > 1
    );
    if (hasDirectText && parseFloat(cs.fontSize) < 12) {
      tinyText.push({
        sel: describe(el),
        size: cs.fontSize,
        text: (el.textContent || "").trim().slice(0, 14),
      });
    }

    // 2) 对比度：只看有直接文字的元素
    const hasText = Array.from(el.childNodes).some(
      (n) => n.nodeType === 3 && n.textContent.trim().length > 0
    );
    if (hasText && !gradientBg && !inDecorated) {
      const fg = parseColor(cs.color);
      if (fg) {
        // 分段控件（`我的 / 全员` 那种滑动开关）的选中项：底色画在**兄弟节点**的滑动指示块上，
        // 往上找祖先只能找到容器灰底，于是白字会被算成压灰底（1.18:1）——两层叠放的测量盲区。
        // 这里按真实叠放关系取指示块的底色再算，别用"跳过"糊过去。
        let bgForText = bg;
        if (el.closest(".admin-scope__option.is-active")) {
          const indicator = el.closest(".admin-scope")?.querySelector(".admin-scope__indicator");
          const indicatorBg = indicator ? parseColor(getComputedStyle(indicator).backgroundColor) : null;
          if (indicatorBg) bgForText = indicatorBg;
        }
        const ratio = contrast(fg, bgForText);
        if (ratio < 3) {
          lowContrast.push({
            sel: describe(el),
            color: cs.color,
            bg: `rgb(${bgForText.slice(0, 3).map((v) => Math.round(v)).join(",")})`,
            ratio: Math.round(ratio * 100) / 100,
            text: (el.textContent || "").trim().slice(0, 16),
          });
        }
      }
    }
  }

  const uniq = (arr) => {
    const seen = new Set();
    return arr.filter((x) => {
      const k = x.sel + x.color + x.bg;
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  };

  return {
    lightSurfaces: uniq(lightSurfaces).sort((a, b) => b.area - a.area).slice(0, 8),
    lowContrast: uniq(lowContrast).sort((a, b) => a.ratio - b.ratio).slice(0, 8),
    lightCount: uniq(lightSurfaces).length,
    contrastCount: uniq(lowContrast).length,
    tinyText: uniq(tinyText).slice(0, 6),
    tinyCount: uniq(tinyText).length,
  };
}

async function runMode(browser, mode) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript((m) => {
    try {
      localStorage.setItem("mimo-theme", m);
      const done = { status: "done", v: 2, at: new Date().toISOString() };
      localStorage.setItem("mimo_tour_state", JSON.stringify({ welcome: done, board: done, collab: done }));
    } catch {
      /* 忽略 */
    }
  }, mode);

  const page = await context.newPage();
  page.setDefaultTimeout(25000);
  await login(page);

  const themed = await page.evaluate(() => document.documentElement.getAttribute("data-theme"));
  record(mode, `主题确实切到 ${mode}`, themed === mode, `data-theme=${themed}`);

  let totalLight = 0;
  let totalTiny = 0;
  let totalContrast = 0;
  const details = [];

  for (const [label, path] of PAGES) {
    try {
      await page.goto(`${BASE}${path}`, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(1400); // 等接口回来 + 图表渲染
      const r = await page.evaluate(auditInPage, mode);
      totalTiny += r.tinyCount;
      totalLight += r.lightCount;
      totalContrast += r.contrastCount;
      const line = `${label.padEnd(6, "　")} 浅色块=${String(r.lightCount).padStart(2)} 低对比=${String(
        r.contrastCount
      ).padStart(2)} 小字=${String(r.tinyCount).padStart(2)}`;
      console.log(`  ${line}`);
      if (r.lightSurfaces.length) {
        console.log(
          "     浅色块： " +
            r.lightSurfaces
              .map((x) => `${x.sel}(${x.bg}, ${x.area}px²)`)
              .join("\n              ")
        );
      }
      if (r.lowContrast.length) {
        console.log(
          "     低对比： " +
            r.lowContrast
              .map((x) => `${x.sel} ${x.ratio}:1 「${x.text}」 ${x.color} on ${x.bg}`)
              .join("\n              ")
        );
      }
      details.push({ label, path, ...r });
    } catch (e) {
      console.log(`  ${label} 体检失败：${e.message}`);
    }
  }

  await context.close();
  return { mode, totalLight, totalContrast, totalTiny, details };
}

async function main() {
  const { chromium } = requirePlaywright();
  const browser = await chromium.launch({ headless: true });
  const results = [];
  try {
    if (ONLY === "both" || ONLY === "dark") results.push(await runMode(browser, "dark"));
    if (ONLY === "both" || ONLY === "light") results.push(await runMode(browser, "light"));
  } finally {
    await browser.close();
  }

  console.log("\n=== 汇总 ===");
  let hardFail = false;
  for (const r of results) {
    console.log(
      `${r.mode}: 浅色块 ${r.totalLight} 处，低对比文本 ${r.totalContrast} 处，小于 12px 的文字 ${r.totalTiny} 处（${r.details.length} 个页面）`
    );
    if (r.mode === "dark" && r.totalLight > 0) hardFail = true;
  }
  if (hardFail) {
    console.log("暗色模式下仍有浅色块残留 —— 见上面逐页明细。");
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error("体检执行异常:", e);
  process.exitCode = 1;
});
