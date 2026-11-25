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

## 主线自证用例（`main-chain.mjs`）

不是"每个功能各验一次"，而是**一条链必须首尾相接**：注册一个全新账号，然后只走界面，
从建团队一路走到看报告。链路中间任何一环断掉都会红 —— v2.19.0 的 BUG-099 / BUG-100
就是它首跑（5/9）时抓出来的。

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 新账号建第一个团队 | 团队列表里能看到，`GET /api/teams/my` 有它 |
| 2 | 团队页建项目 | `GET /api/projects/team/{id}` 里有它 |
| 3 | 项目页建迭代 | `GET /api/sprints/project/{id}` 里有它（要点开日期面板选起止） |
| 4 | 看板建任务 | 卡片出现在「待办」列 |
| 5 | 待办 → 进行中 | 接口回读 `status=IN_PROGRESS` |
| 6 | 提交验收 | 产生 `PENDING` 的 review |
| 7 | 单人团队的死锁有说明 | 详情页出现 `.next-step-blocked`（"团队里除你之外没有别人"） |
| 8 | 从界面邀请第二个成员 | 成员数 ≥ 2 |
| 9 | 管理员从工作台验收通过 | 「待我处理」里有这条 → 点进去通过 → `APPROVED` |
| 10 | 一键标记完成 | `status=DONE` |
| 11 | 项目报告页 | 有「生成报告」入口 |

```bash
node e2e/main-chain.mjs http://localhost:5173   # 11 条，要前端 :5173 + 后端 :8080
```

用例自己注册 `chain<时间戳>` 账号、自己清理（先用新账号自己的权限删项目/团队，删不掉会打警告）。
引导遮罩靠预先写入 `localStorage.mimo_tour_state` 关掉（见下面第 4 个坑）。

## 清库（`cleanup-residue.sql`）

用例会自己造数据。issue 级的都删得干净，但**注册出来的账号、它们建的团队/项目删不掉**——
删项目在本系统里要走审批流程，脚本没有"删掉我刚建的东西"的通道。跑多了开发库会累积
`probe-*` / `chain*` 账号和「链路团队/项目」（KNOWN-023）。

```bash
mysql -umimo -pmimo123 mimo < e2e/cleanup-residue.sql
```

只删带这些前缀/名字的用例数据，不碰 `admin`/`zhangsan`/`lisi` 和真实项目；可重复执行。
**生产库不要执行。**

v2.19.5 补了两件事：

- 删 `sprints` **之前**先删 `burndown_snapshots`（sprint 是物理删除，快照回头就成了孤儿）；
- 末尾加一段全局孤儿收尾：`notifications` 指向已不存在的工作项、`burndown_snapshots`
  指向不存在的 sprint、`delivery_daily_snapshots` 指向不存在的项目，一并清掉。
  结果查询也带了 `orphan_notifications` / `orphan_burndown` 两列，跑完应当都是 0。

## 工作流用例（`workflow-guard.mjs`）

证明"工作流不是装饰"，同时盯住"看不懂"的那两处（v2.19.1）：

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 设计器不点「编辑」就能看懂规则 | 出现只读概览，规则表 ≥1 行，表头含「谁可以这么做」 |
| 2 | 越界拖拽被拦 | 把 STORY 卡从「待办」拖到「已完成」→ 提示含"工作流不允许"，`columnId` 仍是 1 |
| 3 | 被拦时给方向 | 提示里含「当前可以从这里去：」+ 允许的目标列名 |
| 4 | 允许的移动照常成功 | 拖到「进行中」→ `columnId=2`、`status=IN_PROGRESS` |
| 5 | 节点责任人拦得住（v2.19.6） | 把「待办」责任人设为 admin，纯成员拖 → 提示含"负责"、`columnId` 仍是 1 |
| 6 | 提示点名到人 | 提示含「这一步由「admin」负责」，而不是含糊的"权限不足" |
| 7 | 责任人/管理员能推进 | admin 拖同一张卡 → `columnId=2` |
| 8 | 设计器能给节点配责任人 | 编辑态下节点数 = 责任人下拉数，占位文案含「责任人」 |

```bash
node e2e/workflow-guard.mjs http://localhost:5173
```

前置：项目 id=1 上配了 STORY 工作流（demo 库是 scrum 模板：待办↔进行中↔已完成）。
用例自己建卡、自己改工作流、结束时把配置改回去，不碰 demo 数据。

> 5–7 那条要**现场注册一个纯成员账号**（`e2e-wfowner*`，以 `ROLE_MEMBER` 拉进团队 1）来验证，
> 不能用 zhangsan / lisi：开发库里他们在 `team_members` 里也是 `ROLE_ADMIN`，
> 而"管理员永远能推进"是设计内的放行口，用他们测出来的永远是放行。
> 注册出的账号由 `cleanup-residue.sql` 按 `e2e-%` 前缀回收。

## 验收权限矩阵（`review-matrix.mjs`）

验收这件事的规则是**按角色分**的，主线用例只证明了"一个人能走完一遍"，所以单独有一份矩阵。
用例自己建团队/项目/任务（名字带 `e2e团队…/e2e项目…` 前缀），跑完用 `cleanup-residue.sql` 收掉。

| # | 检查点 | 断言 |
|---|--------|------|
| A | 非负责人不能提交 | `POST /issues/{id}/reviews` = 403「只有任务负责人可以提交验收」 |
| B | 负责人提交 | = 200，`status=PENDING`，`submitterId` = 负责人 |
| C | 提交人不能验收自己 | `PUT /issue-reviews/{id}/approve` = 403「不能验收自己提交的结果」 |
| D | 普通成员不能验收 | = 403「只有项目管理员或报告人可以验收」 |
| I | 界面上提交人看不到按钮 | 「通过」按钮数 = 0，且有"这是你自己提交的…"说明 |
| J | 界面上报告人看得到按钮 | 「通过」「打回」各 ≥1 |
| E1 | 跳步被工作流挡 | 待办 → 已完成 = 400「工作流不允许从「待办」转换到「已完成」」 |
| E2 | 没通过不能收工 | 待验收 → 已完成 = 409「最新验收轮次尚未通过…」 |
| E3 | 通过后能收工 | 验收 = APPROVED，再移动 = 200，`status=DONE` |
| F | 已处理记录不能再动 | 再 approve = 400「该验收记录已处理」 |
| G | 打回必须填原因 | 空原因 = 400；打回后 work item 回 `IN_PROGRESS`，并进提交人「被驳回」 |
| H | 团队管理员能验收 | 授 `PROJECT_MANAGER`（含 `team.manage`）在 TEAM 作用域后，普通成员身份的验收 = 200 |
| K | 工作台能就地通过 | `/dashboard` 的「待我处理」里点「通过」→ 记录变 `APPROVED`，该行从列表消失（v2.19.5） |

```bash
node e2e/review-matrix.mjs http://localhost:5173
```

前置：`admin` / `zhangsan` / `lisi` 三个账号存在（种子数据自带，密码都是 `123456`）。
界面检查用 `.review-card__actions button` 计数，不走 `getByRole`——详情页是弹窗，
a11y 树里这些按钮会被判定为 hidden，`getByRole` 数到 0（踩过）。

## 在线状态用例（`presence.mjs`，v2.19.7）

对应 `bug-log.md` **KNOWN-013**：「在线人数始终显示总成员数」。
这条用例盯的是"界面别编数字"：

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 浏览器连着 WebSocket 时算在线 | `GET /api/teams/1/presence` 的 `onlineCount >= 1` |
| 2 | 不会超过成员总数 | `onlineCount <= totalMembers` |
| 3 | 文案两个数都在 | 页面出现「N 人在线 · M 位成员」 |
| 4 | 关掉页面会掉线 | 关浏览器后最多等 40 秒，`onlineCount` 回落到 0（正常关闭走 DISCONNECT 帧，实测 0.1 秒；被杀进程只能等心跳，最迟一个 10 秒周期 + 调度延迟） |

第 4 条当初是**红的**：简单 broker 默认不发心跳，服务端要等 Tomcat 会话超时才认为人走了。
给 `enableSimpleBroker` 配了 10 秒心跳 + 调度器之后才通过——所以这条用例同时是心跳配置的回归保护。

## 版面探针（`probe-text.mjs`）

抬字号、改间距这类"一处改、多处"的改动，除了 `theme-audit` 量对比度，还要量**有没有撑坏**：

```bash
node e2e/probe-text.mjs http://localhost:5173
```

| 指标 | 判法 | 合格线 |
|---|---|---|
| 小字 | `font-size < 12px` 且有文字的元素 | 0 |
| 真裁切 | 容器 `overflow: hidden` 且 `scrollHeight/scrollWidth` 超出 2px | 0（长名字的正常省略号除外） |
| 高度不足 | `overflow: hidden` 且 `line-height > clientHeight + 1.5` | 0 |
| 页面横向溢出 | `documentElement.scrollWidth - clientWidth` | 0 |
| 路由真实性 | 探测后核对 `location.pathname` | 必须与预期一致（不存在的一级路径会被兜底重定向到 `/`，会造成"这页没问题"的假象） |

脚本对"小字 > 0 / 横向溢出 / 路由不符"三种情况**返回非 0**，可以直接进流水线；
"真裁切"只打印不判失败（长名字的省略号是正常设计，需人工确认是哪一个）。

最近一次结果（v2.19.9，抬高 106 处字号后）：13 个真实路由 **小字 0 / 真裁切 1（「示例项目：移动端改版」在 185px 卡片里的省略号）/ 横向溢出 0 / 路由不符 0**，
`theme-audit` 亮暗均 **小于 12px 的文字 0 处**；`probe-layout` 15 页横向溢出 0、文字被裁 0。

## 管理员视角用例（`admin-flow.mjs`，v2.19.8，19 条）

对应"管理员账号要和普通账号突出区别、能看到所有成员的任务流、点开能看细致的具体任务"。
它同时是**新 UI 自己的可读性回归网**（对比度那一条当场抓到了 BUG-111、后来又抓到空闲成员那行的 2.76:1）。

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 管理员顶栏有身份徽标 | `.mimo-header__user .admin-badge` 在 10 秒内可见 |
| 2 | 工作台有「我的 / 全员」切换 | `.admin-scope` 存在 |
| 2.5 | Hero 上有「全员任务流」按钮 | `.dash-hero__admin-entry` 可见；后续步骤**就点它**进入全员视角（入口没了用例就红） |
| 3 | 成员列数量与接口一致 | 界面 `.member-stream` 数 == `GET /api/workbench/team` 的 `members.length` |
| 4 | 概览数字与接口一致 | 未完成 / 逾期 / 空闲成员三格分别对得上 `summary` |
| 5 | 点卡片能开抽屉，标题一致 | 抽屉标题 == 被点卡片的标题 |
| 6 | 抽屉不是空壳 | 至少 5 个字段格且有非空值（负责人 / 项目 / 看板列…） |
| 7 | Esc 能关掉抽屉 | 按 Esc 后抽屉不可见（BUG-110 的回归） |
| 8 | 普通成员没有徽标与切换 | 现场注册一个纯成员账号，两处都为 0 |
| 8.5 | **旧字段兜底的团队管理员也有入口** | zhangsan（`users.role=ROLE_MEMBER` + `team_members.role=ROLE_ADMIN`）徽标与切换都在，切全员后成员列 > 0、接口 `scope=TEAM` |
| 8.8 | **管理员在别人负责的任务上不被按成员门槛拦**（BUG-115） | 全员流点成员任务 → 抽屉「在项目里打开」→ 看板弹窗里「提交验收」可点，且页面上没有「只有任务负责人（X）可以提交验收」。只验不点：点了会写一条待验收记录 |
| 9 | 普通成员调接口被拒 | 响应体 `code === 403` 且说明里含"管理员" |
| 10–15 | 亮/暗两套主题下新界面 | 全员视图与抽屉**无低对比文本**（< 3:1）、页面**无横向溢出** |

两条经验写在用例注释里，别再踩：

- **判 `code` 不判 HTTP 状态**：本项目 `BusinessException` 走 HTTP 200 + 响应体 `code`，
  "越权被挡住"在 Network 面板里不是红色请求。第一版断言 `status === 403` 直接误判成失败。
- **`el-drawer` 关闭后 DOM 还在**（只是隐藏），所以判 `isVisible()`，`count() === 0` 永远不成立。
- 徽标依赖 userInfo/权限码，登录后立刻数会数到 0 —— 用 `waitFor({ state: 'visible' })`。

跑法：`node e2e/admin-flow.mjs http://localhost:5173`（会现场注册 `e2e-flowmember*`，
用 `cleanup-residue.sql` 清）。

## 配色体检（`theme-audit.mjs`，v2.19.7）

不开断言式用例，而是"量数字"：

```bash
node e2e/theme-audit.mjs http://localhost:5173         # 亮色 + 暗色
node e2e/theme-audit.mjs http://localhost:5173 dark    # 只查暗色
```

- **浅色块残留**（只在暗色判）：元素自己画了近白底、面积 > 800px²、实际底色亮度 > 0.55。
- **低对比**：文字与"实际底色"（往上找第一个不透明背景）对比度 < 3:1。
- 渐变背景与 hero/banner 装饰区跳过——那里底色画在同级图层上，逐元素算必然是假警报。

当前基线：暗色 `浅色块 0 / 低对比 0`，亮色 `0 / 0`（14 个页面）。
退出码：暗色查出浅色块就返回 1，接入 CI 时可以直接当门禁。

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
4. **关引导要写对 key**：真状态是 `localStorage.mimo_tour_state`（按主题存 `{status:'done'|'skipped', v:2}`），
   写 `sessionStorage` 的假 key 完全无效。引导一旦自动启动，遮罩会把后续点击全吃掉，
   页面看起来"按钮点不动"。（`main-chain.mjs` 的 `addInitScript` 就是标准写法。）

## 还没覆盖的（后续补）

- 变更审批（申请 → 审批 → 自动执行）
- Sprint 启动 / 完成 / 未完成任务迁移
- 导出 PDF / 导入 Excel
- 配置了真实 Key 后的 AI 正常返回路径（RAG 引用质量、AI Sprint 规划）
- 多角色验收矩阵（`main-chain.mjs` 目前只跑"创建者 + 一名管理员"这一种角色组合）
