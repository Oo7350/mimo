/**
 * 工作流画布用例（v2.22.0）：节点是流程状态、看板列由它投影、连线就是规则、前提条件真的管用。
 *
 * 要证的几件事（都是"界面说的和后台做的一致"）：
 *   1. 设计器是画布：节点能拖、能从右侧圆点拖出一条线；
 *   2. 新节点可以先不绑列，保存时后端补一列，并且**明确告诉你补了哪一列**；
 *   3. 给连线加「验收标准已全部勾完」之后，没勾完的卡真的拖不进「已完成」，提示里说清还差几项；
 *   4. 勾完之后同样的拖拽就过了 —— 否则规则只是添堵。
 *
 * 跑法：node e2e/workflow-canvas.mjs http://localhost:5173
 * 依赖：项目 id=1 上有 STORY 工作流（demo 库是 scrum 模板：待办↔进行中↔已完成）、admin 账号。
 * 收尾：把 STORY 工作流恢复成跑之前的样子，并删掉本次自动建的列（不留垃圾）。
 */
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const requirePlaywright = () => {
  const mod = process.env.PLAYWRIGHT_MODULE || "playwright";
  return require(mod);
};

const BASE = process.argv[2] || "http://localhost:5173";
const API = process.env.MIMO_API || "http://localhost:8080/api";
const ADMIN = { username: "admin", password: "123456" };
const STAMP = Date.now().toString().slice(-6);
const NODE_NAME = `上线${STAMP}`;
const TITLE = `验收标准用例${STAMP}`;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}  — ${detail}`);
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
  return res.json().catch(() => null);
}

async function login(creds) {
  const json = await api("POST", "/auth/login", creds);
  if (!json?.data?.token) throw new Error("登录失败: " + JSON.stringify(json).slice(0, 160));
  return json.data.token;
}

async function newPage(browser) {
  const context = await browser.newContext({ viewport: { width: 1500, height: 950 } });
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
  await page.getByPlaceholder("请输入用户名").fill(ADMIN.username);
  await page.getByPlaceholder("请输入密码").fill(ADMIN.password);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });
  return { context, page };
}

/** vuedraggable(SortableJS) 只认鼠标事件序列，locator.dragTo 不触发 */
async function dragCard(page, title, targetColumn) {
  const card = page.locator(`.issue-card-wrapper:has-text("${title}")`).first();
  await card.waitFor({ state: "visible", timeout: 15000 });
  const box = await card.boundingBox();
  const tbox = await targetColumn.boundingBox();
  if (!box || !tbox) throw new Error("拿不到卡片/列的位置");
  await page.mouse.move(box.x + box.width / 2, box.y + 14);
  await page.mouse.down();
  await page.waitForTimeout(150);
  await page.mouse.move(box.x + box.width / 2 + 12, box.y + 30, { steps: 8 });
  await page.waitForTimeout(150);
  await page.mouse.move(tbox.x + tbox.width / 2, tbox.y + tbox.height / 2, { steps: 20 });
  await page.waitForTimeout(250);
  await page.mouse.up();
  await page.waitForTimeout(2200);
}

async function main() {
  const token = await login(ADMIN);
  const wfBefore = await api("GET", "/workflow/project/1/type/STORY", null, token);
  const originalConfig = wfBefore?.data?.config;
  if (!originalConfig) throw new Error("项目 1 没有 STORY 工作流，先跑一次看板让默认工作流生成");
  const boardBefore = await api("GET", "/board/1", null, token);
  const columnsBefore = (boardBefore?.data?.columns || []).map((c) => c.id);
  const columnIdOf = (name) =>
    (boardBefore?.data?.columns || []).find((c) => c.name === name)?.id;
  // 放外层：后面几段断言都要用（曾经写在 try 里，后面直接 ReferenceError）
  const inProgressCol = columnIdOf("进行中") || 2;
  const doneCol = columnIdOf("已完成") || 3;

  const { chromium } = requirePlaywright();
  const browser = await chromium.launch({ headless: true });
  let createdColumn = null;
  let issueId = null;

  try {
    const { context, page } = await newPage(browser);
    try {
      await page.goto(`${BASE}/projects/1/workflow`, { waitUntil: "domcontentloaded" });
      await page.locator(".workflow-designer__overview").waitFor({ state: "visible", timeout: 20000 });
      await page.locator(".workflow-designer__alert button", { hasText: "编辑" }).first().click();
      await page.locator("[data-workflow-canvas]").waitFor({ state: "visible", timeout: 10000 });
      await page.waitForTimeout(400);

      // ---------- 1. 画布：节点在画布上，连线画出来了 ----------
      const cards = await page.locator("[data-workflow-canvas] .workflow-designer__card").count();
      const edges = await page.locator("[data-workflow-canvas] [data-edge]").count();
      record(
        "设计器是画布：节点卡片 + 连线都画出来了",
        cards >= 3 && edges >= 1,
        `卡片 ${cards} 个、连线 ${edges} 条`
      );

      // ---------- 2. 拖节点会动（真的按坐标摆，不是排成一列） ----------
      const firstCard = page.locator("[data-workflow-canvas] .workflow-designer__card").first();
      const grip = firstCard.locator("[data-drag-handle]");
      const beforeBox = await firstCard.boundingBox();
      const gripBox = await grip.boundingBox();
      await page.mouse.move(gripBox.x + gripBox.width / 2, gripBox.y + gripBox.height / 2);
      await page.mouse.down();
      await page.mouse.move(gripBox.x + gripBox.width / 2, gripBox.y + gripBox.height / 2 + 130, { steps: 14 });
      await page.mouse.up();
      await page.waitForTimeout(200);
      const afterBox = await firstCard.boundingBox();
      record(
        "节点可以拖动摆位置（坐标真的变了）",
        Math.abs(afterBox.y - beforeBox.y) > 40,
        `y: ${Math.round(beforeBox.y)} → ${Math.round(afterBox.y)}`
      );

      // ---------- 3. 加一个「先不绑列」的新节点 ----------
      await page.locator("[data-add-node]").click();
      await page.waitForTimeout(300);
      const cardsAfterAdd = await page.locator("[data-workflow-canvas] .workflow-designer__card").count();
      const hasUnlanded = await page
        .locator("[data-workflow-canvas] .workflow-designer__card", { hasText: "保存后自动建列" })
        .count();
      // 给它一个能认出来的名字（名字会成为看板列名）
      const newCard = page.locator("[data-workflow-canvas] .workflow-designer__card").last();
      const nameInput = newCard.locator("input.workflow-designer__card-name-input");
      await nameInput.fill(NODE_NAME);
      record(
        "新节点可以先不绑看板列（卡片上写明保存后自动建列）",
        cardsAfterAdd === cards + 1 && hasUnlanded >= 1,
        `卡片 ${cards} → ${cardsAfterAdd}，未落地标记 ${hasUnlanded} 个`
      );

      // ---------- 4. 从「已完成」拖一条线到新节点 ----------
      const doneCard = page.locator("[data-workflow-canvas] .workflow-designer__card", {
        hasText: "已完成",
      }).first();
      const donePort = doneCard.locator("[data-port]");
      const portBox = await donePort.boundingBox();
      const targetBox = await newCard.boundingBox();
      await page.mouse.move(portBox.x + portBox.width / 2, portBox.y + portBox.height / 2);
      await page.mouse.down();
      await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y + 30, { steps: 18 });
      await page.waitForTimeout(150);
      await page.mouse.up();
      await page.waitForTimeout(400);
      const edgesAfterLink = await page.locator("[data-workflow-canvas] [data-edge]").count();
      record(
        "从节点右侧圆点拖到另一个节点就连出一条规则",
        edgesAfterLink === edges + 1,
        `连线 ${edges} → ${edgesAfterLink}`
      );

      // ---------- 5. 给「进行中 → 已完成」加前提：验收标准全部勾完 ----------
      // 连线是 SVG path，点它的 bbox 中心不一定压在描边上，直接派发 click 更稳
      const cfgNow = (await api("GET", "/workflow/project/1/type/STORY", null, token))?.data?.config;
      const wanted = (cfgNow?.transitions || []).findIndex(
        (t) => t.fromColumnId === inProgressCol && t.toColumnId === doneCol
      );
      if (wanted < 0) throw new Error("现有工作流里没有「进行中 → 已完成」这条线，用例前提不满足");
      await page.locator(`[data-workflow-canvas] [data-edge="${wanted}"]`).dispatchEvent("click");
      await page.locator("[data-edge-panel]").waitFor({ state: "visible", timeout: 5000 });
      // 第二个下拉是「什么情况下才能做」，选第一条（验收标准已全部勾完）
      const prereqSelect = page.locator("[data-edge-panel] .el-select").nth(1);
      await prereqSelect.click();
      await page.locator(".el-select-dropdown__item").filter({ hasText: "验收标准已全部勾完" }).first().click();
      await page.keyboard.press("Escape");
      record(
        "点连线能给它加前提条件（面板里两栏：谁可以做 / 什么情况下才能做）",
        (await page.locator("[data-edge-panel]").innerText()).includes("什么情况下才能做"),
        "连线设置面板可见"
      );

      // ---------- 6. 保存：自动建列 + 提示 ----------
      await page.locator("button", { hasText: "保存工作流" }).first().click();
      await page.waitForTimeout(2500);
      const toast = (await page.locator(".el-message").allInnerTexts()).join(" | ");
      const wfAfter = await api("GET", "/workflow/project/1/type/STORY", null, token);
      const savedNode = (wfAfter?.data?.config?.nodes || []).find((n) => n.name === NODE_NAME);
      createdColumn = savedNode?.columnId || null;
      const savedEdge = (wfAfter?.data?.config?.transitions || []).find(
        (t) => t.fromColumnId === inProgressCol && t.toColumnId === doneCol
      );
      record(
        "保存时为自由节点补了看板列，并且明确告诉用户补了哪一列",
        !!createdColumn && toast.includes("自动创建了看板列") && toast.includes(NODE_NAME),
        `新列 id=${createdColumn}，提示=「${toast.slice(0, 80)}」`
      );
      record(
        "前提条件存下来了（进行中 → 已完成 带 ACCEPTANCE_DONE）",
        (savedEdge?.prerequisites || []).includes("ACCEPTANCE_DONE"),
        `prerequisites=${JSON.stringify(savedEdge?.prerequisites || [])}`
      );
      const boardAfter = await api("GET", "/board/1", null, token);
      record(
        "看板上真的多了这一列（不是只写在配置里）",
        newVisible(boardAfter, NODE_NAME) && !columnsBefore.includes(createdColumn),
        `看板列：${(boardAfter?.data?.columns || []).map((c) => c.name).join("/").slice(0, 80)}`
      );
    } finally {
      await context.close();
    }

    // ---------- 7. 验收标准没勾完：拖不进「已完成」 ----------
    const created = await api(
      "POST",
      "/issues",
      {
        projectId: 1,
        type: "STORY",
        title: TITLE,
        status: "IN_PROGRESS",
        columnId: columnIdOf("进行中") || 2,
        priority: "MEDIUM",
        acceptanceCriteria: [
          { id: "ac-1", text: "能点开", done: false },
          { id: "ac-2", text: "能保存", done: false },
        ],
      },
      token
    );
    if (created?.code !== 200) throw new Error("建卡失败: " + JSON.stringify(created).slice(0, 200));
    issueId = created.data.id;

    {
      const { context, page } = await newPage(browser);
      try {
        await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
        await page.locator(".board__column").first().waitFor({ state: "visible", timeout: 20000 });
        await page.waitForTimeout(1200);
        const doneColumn = page.locator(".board__column-body").nth(2);
        await dragCard(page, TITLE, doneColumn);
        const text = (await page.locator(".el-message").allInnerTexts()).join(" | ");
        const after = await api("GET", `/issues/${issueId}`, null, token);
        record(
          "验收标准没勾完时拖不进「已完成」（卡片留在原列）",
          /验收标准还有 2 项没勾完/.test(text) && after?.data?.columnId !== (doneCol || 3),
          `提示=「${text.slice(0, 80)}」, columnId=${after?.data?.columnId}`
        );
      } finally {
        await context.close();
      }
    }

    // ---------- 8. 勾完之后同样的拖拽就过了 ----------
    // 逐条勾掉：接口是 PUT /issues/{id}/acceptance-criteria/{criteriaId}（不是整体覆盖）
    for (const ac of [
      { id: "ac-1", text: "能点开" },
      { id: "ac-2", text: "能保存" },
    ]) {
      const patched = await api(
        "PUT",
        `/issues/${issueId}/acceptance-criteria/${ac.id}`,
        { ...ac, done: true },
        token
      );
      if (patched?.code !== 200) {
        throw new Error("勾验收标准失败: " + JSON.stringify(patched).slice(0, 160));
      }
    }
    {
      const { context, page } = await newPage(browser);
      try {
        await page.goto(`${BASE}/projects/1/board`, { waitUntil: "domcontentloaded" });
        await page.locator(".board__column").first().waitFor({ state: "visible", timeout: 20000 });
        await page.waitForTimeout(1200);
        await dragCard(page, TITLE, page.locator(".board__column-body").nth(2));
        const after = await api("GET", `/issues/${issueId}`, null, token);
        record(
          "验收标准勾完之后，同样的拖拽顺利通过",
          after?.data?.columnId === (doneCol || 3),
          `columnId=${after?.data?.columnId}`
        );
      } finally {
        await context.close();
      }
    }
  } finally {
    // 收尾：恢复工作流、删掉自动建的列和本轮的卡，别把 demo 项目留成"必须勾完验收标准才能完成"
    if (originalConfig) {
      await api(
        "POST",
        "/workflow",
        { projectId: 1, issueType: "STORY", name: wfBefore.data.name, config: originalConfig },
        token
      );
    }
    if (createdColumn) {
      const del = await api("DELETE", `/board/column/${createdColumn}`, null, token);
      if (del?.code !== 200) console.warn(`⚠ 自动建的列 ${createdColumn} 未删除: ${JSON.stringify(del).slice(0, 120)}`);
    }
    if (issueId) {
      const del = await api("DELETE", `/issues/${issueId}`, null, token);
      if (del?.code !== 200) console.warn(`⚠ 清理未成功 issue=${issueId}`);
    }
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} 通过 ===`);
  if (failed.length) {
    console.log("失败项：\n" + failed.map((f) => ` - ${f.name}: ${f.detail}`).join("\n"));
    process.exitCode = 1;
  }
}

function newVisible(boardJson, name) {
  return (boardJson?.data?.columns || []).some((c) => c.name === name);
}

main().catch((e) => {
  console.error("用例执行异常:", e);
  process.exitCode = 1;
});
