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
node e2e/core-flows.mjs http://localhost:5173
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
- 越权场景（普通成员访问他人项目应 403）
- 导出 PDF / 导入 Excel
