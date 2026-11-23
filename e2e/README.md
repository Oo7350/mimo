# Mimo 端到端用例（核心链路）

对应 `bug-log.md` **KNOWN-017**：「核心业务链路缺少端到端验证」。
这里用真实浏览器把主链路点一遍，并对接口结果做断言——不是"页面能打开"，而是"操作真的生效"。

## 覆盖范围

| # | 用例 | 断言 |
|---|---|---|
| 1 | 创建任务 | `POST /api/issues` = 200，看板上出现新卡片 |
| 2 | 拖拽到「进行中」 | `PUT /api/board/issue/move` = 200，接口回读列名=进行中、status=IN_PROGRESS、计时已启动 |
| 3 | 发表评论 | `POST /api/comments` = 200，评论内容出现在面板里 |
| 4 | 上传附件 | `POST /api/attachments` = 200，文件名出现在附件列表 |
| 5 | 提交验收 | 验收接口 = 200 |
| 6 | 新建 Sprint | `POST /api/sprints` = 200 |

全流程任何 4xx/5xx 都会让对应用例失败。

## AI 降级用例（`ai-degrade.mjs`）

对应 `bug-log.md` **KNOWN-018**（无 Key 时的提示未验证）。后端没配 `DEEPSEEK_API_KEY` 时：

| # | 用例 | 断言 |
|---|---|---|
| 1 | AI 未配置提示条 | 打开面板即提示，不让用户点一次失败一次 |
| 2 | 对话返回友好说明 | 气泡里是中文说明，不含异常堆栈 / 上游原文 |
| 3 | 润色失败不改写正文 | 只弹错误提示，描述框内容不变、无预览卡 |

第 3 条是回归 `BUG-072`：曾经润色把「服务未配置」当成结果返回，用户点「应用」直接把描述覆盖掉。

## 越权用例（`authz.mjs`）

接口级、不需要浏览器。注册一个**不属于任何团队**的临时账号（`probe-*`），
拿它去打 23 个项目 / 团队 / Sprint / 任务 / 管理端读接口：

| 断言 | 期望 |
|---|---|
| 每个越权目标 | 401 或 403（返回 200 + code=200 即视为泄漏） |
| 成员自己读 Wiki 目录 | 200（防止"一刀切"式修复） |
| 缺少必需参数 | 400 + 具体原因（不能是 500） |

只读，不改任何业务数据（会注册临时账号）。对应 `bug-log.md` KNOWN-017 的越权项与 **BUG-076**。

## 引导用例（`tour.mjs`）

对应 `bug-log.md` 的 **BUG-078~081**。用真实浏览器把引导的完整生命周期走一遍：

| # | 用例 | 断言 |
|---|---|---|
| 1 | 进看板自动出现引导 | 有 popover、有「跳过引导」、进度是 `1 / 6` 这种多步（不是旧的 3 步） |
| 2 | 跳过后无残留遮罩 | popover/overlay 为 0，body 上没有 `driver-active` |
| 3 | 跳过不算完成 | `mimo_tour_state.board.status === 'skipped'` |
| 4 | 跳过后页面仍可点击 | 看板卡片能点开（以前这里会整站点不动） |
| 5 | 引导入口常驻 | 账号菜单里始终有「新手引导 · …」三项 |
| 6 | 能重播 | 从菜单启动看板引导，popover 正常出现 |
| 7 | Esc 退出后清理干净 | 无 popover、body 无 `driver-active` |
| 8 | 错页面启动只给提示 | 在首页点「看板实操」→ 不启动、提示"需要在项目看板页启动" |
| 9 | 走完记为完成 | 点完最后一步后 `status === 'done'` |

## Markdown / 邮件正文 XSS 用例（`xss-markdown.mjs`）

对应 `bug-log.md` **KNOWN-011**（修复记录见 **BUG-092**）。这条用例**不需要后端、也不需要前端 dev server**，
只起一个空白页的真实 Chromium，因此在这台没有 MySQL/Redis 的机器上也能跑。

做法是"验行为不验字符串"：

1. 读仓库里真实的 `frontend/src/utils/markdown.ts`，用 esbuild 转成 JS 后**原样执行**（改了源码这里跟着变，不是抄副本）；
2. 渲染结果用 `innerHTML` 注入 —— 与 Vue 的 `v-html` 完全一致；
3. 载荷里塞 `window.__xssFired=true`，注入后等 180ms 再读这个**哨兵**，判断脚本是"真的执行了"还是"只是字符串里出现过"；
4. 同一批载荷跑两个版本：`git show HEAD:…markdown.ts`（修复前基线，**只作对照、不计入退出码**）与当前工作区源码（**这才是断言**）。

| # | 载荷 | 修复前（正则黑名单） | 现在（DOMPurify） |
|---|---|---|---|
| 1 | `<img src=x onerror=…>`（不带引号） | 挡住 | 挡住 |
| 2 | `<img src=x onerror = …>`（等号带空格） | 挡住 | 挡住 |
| 3 | `<svg/onload=…>` | 挡住 | 挡住 |
| 4 | `<details open ontoggle=…>` | **真的执行了** | 挡住 |
| 5 | `![a"onerror="…](x)` | 挡住 | 挡住 |
| 6 | `<math><mtext><table><mglyph><style>…`（mXSS） | 留 `<style>` | 挡住 |
| 7 | `[点我](jav&#x61;script:…)` | 留 `javascript:` 链接 | 挡住 |
| 8 | `<scr<script>ipt>…` | 挡住 | 挡住 |
| 9 | `<style>…url("javascript:…")</style>` | 留 `<style>` | 挡住 |
| 10 | 正常 Markdown（粗体/代码/列表/链接） | 格式保留 | 格式保留（回归项） |

结果写 `e2e/xss-report.md`（已 gitignore）：修复前基线 6/10、当前源码 10/10。
`dompurify` 缺失时先 `cd frontend && npm i`。

## 怎么跑

前置：MySQL :3306、后端 :8080、前端 :5173 都已启动，且库里有项目 id=1（种子数据或自己造）。
前端 dev server 只监听 `localhost`（IPv6），用 `http://localhost:5173`，别用 `127.0.0.1`。

```bash
# 1) 装一次 Playwright —— 建议装在仓库外，避免污染 package.json
mkdir -p ../toolchain/e2e && cd ../toolchain/e2e
npm i playwright && npx playwright install chromium

# 2) 跑用例（回到仓库根目录）
export PLAYWRIGHT_BROWSERS_PATH=<浏览器安装目录>          # Windows: $env:PLAYWRIGHT_BROWSERS_PATH
export PLAYWRIGHT_MODULE=<上一步目录>/node_modules/playwright  # 让脚本从仓库外解析 playwright
node e2e/core-flows.mjs http://localhost:5173   # 核心业务链路 6 条（要前端 :5173）
node e2e/ai-degrade.mjs http://localhost:5173  # AI 未配置场景 3 条（要前端 :5173）
node e2e/authz.mjs http://localhost:8080/api   # 越权拦截 25 条（只要后端 :8080）
node e2e/tour.mjs http://localhost:5173        # 新手引导 9 条（要前端 :5173）
node e2e/xss-markdown.mjs                      # Markdown/邮件 XSS 10 条（只要浏览器，不用起服务）
```

Playwright 装在仓库外时靠 `PLAYWRIGHT_MODULE` 指过去；直接装在仓库里则不用设，脚本自己会找到。
结果打印 `PASS/FAIL` 并写 `e2e/suite-report.md`；失败截图在 `e2e/shots-suite/`（这些产物已 gitignore）。
全绿时退出码 0，任一失败退出码 1，可直接接 CI。

## 两个坑（写在这里省得下次再踩）

1. **driver.js 引导会锁死全站点击**：它给 `<body>` 加 `driver-active`，CSS 是 `* { pointer-events: none }`。
   只删遮罩 DOM 而不清这个类，后续所有点击/拖拽都会被吞掉（表现为 locator 明明解析到了却点不动）。
   用例里先点「跳过引导」，再兜底 `document.body.classList.remove("driver-active")`。
2. **Element Plus 的页签要 `dispatchEvent('click')`**：`getByRole('tab')` / `.click()` 在弹窗里点不生效，
   用 `.el-tabs__item` 文本定位 + `dispatchEvent("click")` 才切换（见 `clickTab()`）。
3. 拖拽用的是 vuedraggable(SortableJS)，`locator.dragTo()` 不触发，必须手工 `mouse.down/move/up`。

## 还没覆盖的（后续补）

- 变更审批（申请 → 审批 → 自动执行）
- Sprint 启动 / 完成 / 未完成任务迁移
- 导出 PDF / 导入 Excel
- 配置了真实 Key 后的 AI 正常返回路径（RAG 引用质量、AI Sprint 规划）
