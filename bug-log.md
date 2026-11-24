# Mimo Bug Log

记录 Mimo 项目自开发以来遇到并修复的所有 Bug，按版本倒序排列。

---

## v2.19.5 — 待验收的事在工作台就能处理；示例数据能生成也得能收回 (2026-11-24)

> 两件都是"链路闭合"的收尾：一件是把动作搬到用户真正待的地方，一件是把上一轮开的门装上回程。

### 工作台「待我处理」直接通过 / 打回

以前验收人处理一条待验收，要走 **项目列表 → 看板 → 卡片 → 验收页签 → 通过** 五步换一个按钮；
工作台明明已经把"待我处理"聚合出来了，点进去却只是跳转。现在这一行直接给「通过 / 打回」：

- 通过：可选一句验收说明，留空两点就能收工；
- 打回：必须填原因（前端拦 + 后端同一规则），提交人会收到通知并进「被驳回」；
- 做完就地刷新列表，不整页跳转；变更审批仍只给「去审批 →」——
  因为审批前必须先看影响分析，就地批是不负责任的。
- 面板底下加一句实话：**验收通过后工作项不会自动完成**，负责人回看板标记完成（门禁会放行）。
  否则用户会以为点了通过就结束了。

用例 `review-matrix.mjs` 加了第 K 条：admin 在 `/dashboard` 的「待我处理」里点通过 → 记录变 APPROVED、
该行从列表消失。首跑红了一次：断言按 issueKey 全局找行，把"我的任务"里的同一张卡也数了进去，
改成限定「待我处理」面板内匹配。

### BUG-103: 清除示例数据后再生成 → 500（Duplicate entry 'DEMO2'）[Medium]

- **现象**: 点「清除示例数据」成功，再点「生成示例数据」就 500：
  `Duplicate entry 'DEMO2' for key 'projects.uk_project_key'`。
- **根因**: 两层。① 项目是**逻辑删除**（`deleted=1`），`allocateProjectKey()` 用普通 `selectList`
  只看得见未删除的行，于是又挑了 `DEMO2`——而 `uk_project_key` 是唯一索引，不看 deleted 标记；
  ② `findDemoProject()` 同样只看未删除行，所以表面上"没有示例数据"，才走到新增这一步。
- **修复**: `ProjectMapper` 加 `selectAllKeysIncludingDeleted()` / `selectByNameIncludingDeleted()`
  两条自定义 SQL（绕过 `@TableLogic` 过滤），分配 Key 一律问前者。补两条单测：
  一条把"清除后再分配"复现成断言（期望 `DEMO4`），一条守住原来的"取第一个空闲 Key"。
- **教训**: 唯一索引 + 逻辑删除同时存在时，"这个值还能不能用"必须连已删除行一起看。

### 示例数据一键清除（把 v2.19.2 开的门装上回程）

- 新端点 `DELETE /api/admin/demo-data`（只给系统管理员、幂等）。删的就是生成器自己造的东西：
  示例项目 → 工作项 → 评论 / 工时 / 验收 / 版本快照 / 变更申请 / 附件 / 标签 / 通知 →
  看板列 / 迭代 / 燃尽快照 / 报告 / 动态 / 工作流 / 交付快照 / Wiki → 项目本体；
  团队只在"除了示例项目没别的项目"且名字是「示例团队」时才删，避免连坐用户自己的团队。
- 首版只删了最直接的几张表，实测留下三类孤儿：`delivery_daily_snapshots`（16 行）、
  `burndown_snapshots`（8 行，因为 sprint 是**物理**删除）、以及早就存在的 44 条
  `notifications`（指向被硬删的工作项）。补齐后重跑，孤儿归零。
- 入口：项目列表页头按钮按状态切换成「清除示例数据」，工作台空库那条也同步切换。

### e2e 清库脚本也修了

`e2e/cleanup-residue.sql` 以前先物理删 `sprints` 再也没人管 `burndown_snapshots`，
也不管 `notifications` 的引用；这次补上"先删快照再删 sprint"，末尾加一段**全局孤儿收尾**
（通知指向不存在工作项 / 燃尽快照指向不存在 sprint / 交付快照指向不存在项目），
结果查询也带上 `orphan_notifications`、`orphan_burndown` 两列，跑完就能看是不是 0。

### 验证

- 后端 `mvn test`：**154 项**全绿（新增 5 条：清除的权限 / 级联范围 / 团队保留 / 团队回收 /
  Key 分配要看软删行）。
- e2e：`review-matrix` **13/13**（新增 K）；回归 `workflow-guard` 4/4、`main-chain` 11/11、
  `next-step` 6/6、`chain-links` 5/5。
- 前端 `vue-tsc` 通过、`npm run build` 通过。
- 接口实测：生成(23) → 清除 → 状态 `exists:false` → 再生成(24) 正常，清除后孤儿数据 0。

---

## v2.19.4 — 验收按角色分工：谁该看到按钮，界面别撒谎 (2026-11-24)

> 这一轮补 KNOWN-017 剩下的那半：**多角色验收矩阵**。写着写着撞到一个真问题——
> 界面上「通过 / 打回」对谁都显示，只有点下去才被后端拒绝。

### BUG-102: 验收面板不判权限，提交人和无关成员也能看到「通过 / 打回」 [Medium]

- **现象**: 打开任意工作项的「验收」页签，只要有一条 PENDING 记录，**所有人**都能看到
  「通过」「打回」两个按钮；「提交验收」按钮同理，非负责人也看得到。
  点了才会被后端拒（`不能验收自己提交的结果` / `只有项目管理员或报告人可以验收` /
  `只有任务负责人可以提交验收`）。
- **根因**: `IssueReviewPanel.vue` 里 `v-if="item.status === 'PENDING'"` 只判了状态，
  没有判"我是谁"。后端 `IssueReviewService.assertReviewer` 规则齐全，前端从来没对齐过。
- **影响**: 最典型的"功能看着有、用起来像坏的"——提交人被告知可以点，点了就报错；
  旁观者同样。这类交互正是"浮于形式"的观感来源。
- **修复**: 面板按后端同一口径判定（`canSubmit` / `canReview`）：
  - 能提交 = 我是负责人；未指派时 = 我是报告人或管理员；
  - 能验收 = 记录 PENDING + 我不是提交人 + （我是系统管理员 / 团队管理员 / 项目负责人 / 报告人）；
  - 没有按钮的人改为看到一句说明：提交人看到"这是你自己提交的，等报告人或项目管理员验收"，
    其他人看到"等报告人或项目管理员验收，你可以先看进度"。
  - 上下文（报告人、项目负责人）由面板自己 `getIssueById` + `getProjectById` 取，
    拿不到就宁可不显示按钮（不给点了会 403 的入口）。
- **顺带确认（不是 bug）**: 验收通过后工作项**不会**自动进「已完成」。设计如此——
  看板侧有 review gate：最新一轮没通过时拖进完成列会被 409 拦住
  （`最新验收轮次尚未通过，不能将任务移到完成列`）；通过之后由负责人拖/点「标记完成」收工，
  详情页「下一步」里就有这个按钮。用例把这两头都钉住了。

#### KNOWN-017 收口：验收权限矩阵进 e2e

新增 `e2e/review-matrix.mjs`（**12 条**，自己建团队/项目、跑完可用 `cleanup-residue.sql` 收掉）：

| # | 断言 | 结果 |
|---|------|------|
| A | 非负责人提交验收被拒（403 只有任务负责人…） | ✓ |
| B | 负责人提交 → PENDING | ✓ |
| C | 提交人验收自己被拒（403 不能验收自己提交的结果） | ✓ |
| D | 普通成员验收被拒（403 只有项目管理员或报告人可以验收） | ✓ |
| I | 界面上提交人看不到通过/打回，只看到"在等谁" | ✓ |
| J | 界面上报告人看得到通过/打回 | ✓ |
| E1 | 跳步被工作流挡（400 工作流不允许从「待办」转换到「已完成」） | ✓ |
| E2 | 验收没通过不能进完成列（409 review gate） | ✓ |
| E3 | 验收通过后能进完成列，工作项变 DONE | ✓ |
| F | 已处理的验收记录不能再操作（400） | ✓ |
| G | 打回必须填原因；打回后状态回退 + 进提交人「被驳回」 | ✓ |
| H | 团队管理员（RBAC `team.manage`，非系统管理员）能验收 | ✓ |

于是 KNOWN-017 从"部分已解决"改为 ✅ 已解决：核心链路现在有 5 套 e2e 共 **38 条**
（main-chain 11 / next-step 6 / chain-links 5 / workflow-guard 4 / review-matrix 12）。
仍然没接 CI，这条另立 KNOWN-024。

### 账目更正（这一节顺手对平）

上一版统计把"KNOWN-017 部分已解决"当成已关闭算了，导致 KNOWN 未关闭写 6、总计写 107。
按本文件一贯口径（部分解决仍计未关闭）这次重算：**BUG 102 + KNOWN 未关闭 7 = 总计 109**。

### 验证

- 后端 `mvn test`：149 项全绿（本轮无后端改动）。
- e2e：`review-matrix` **12/12**；回归 `workflow-guard` 4/4、`main-chain` 11/11、
  `next-step` 6/6、`chain-links` 5/5。
- 前端 `vue-tsc` 通过、`npm run build` 通过。
- 用例数据随后用 `e2e/cleanup-residue.sql` 收干净（用户 3 / 团队 1 / 项目 2）。

---

## v2.19.3 — 合并另一台机器的工作流自动生成：两边的改动对上号 (2026-11-24)

> 昨晚另一台机器推了 4 个提交（`dc94d84` 工作流按看板列自动生成 + 认领审批自动推进、
> `7c125ee` 审批通知重复弹 + 个人动态存储型 XSS、`bc779e3` 文档、`c51955d` 合并）。
> 我这边的 v2.19.1 / v2.19.2 已推上去，所以是快进合并（`5f77217 → c51955d`），没有冲突。

### 合并后两边的改动确实咬得上（逐条验过）

- **他们的自动生成 + 我的"看不懂"修复**：`WorkflowService.ensureDefaultWorkflows` 在项目创建时
  按看板列生成 STORY / TASK / BUG 三套默认工作流（Scrum，前进+回退），`GET /api/workflow/project/{id}`
  对存量项目懒补齐。我这边加的只读概览正好接上：实测示例项目（id=10）首次访问生成 3 套、
  每套 4 节点 6 条规则，页面顶部显示「自动生成」标签 + 「从→到→谁可以这么做」规则表；
  再查一次仍是 3 套（幂等）。
- **文案对齐**：合并后的 `PageHint` 已被改成"默认按看板列自动生成（Scrum，前进+回退都允许）；
  删掉配置 = 随便拖"，但同一页还多了一段几乎重复的说明。删掉重复段，只留
  「看板上被挡住时会告诉你「当前可以从这里去：…」」。
- **我这边的工作全部还在**（合并后逐个 grep 确认）：设计器只读概览、看板 `whereCanIGo` 提示、
  `ElMessage.closeAll` 去重、详情页单人团队死锁提示、`EmptyGuide` 与审批两个空态、
  演示数据接口与入口。

### 发现的文档版本号对不齐（记下了，正文没改）

同一件事在三个地方标了三个版本：他们的提交写 `v2.17.1`、`CLAUDE.md` 写 `v2.18.1`、
合并后实际是 `v2.19.3`。`项目功能完善计划.md` §4.1 的"实现状态（v2.17.1）"段落**内容准确**，
只是编号旧，暂时保留正文；`AGENTS.md` 统一按 v2.19.3 记，`CLAUDE.md` 头部版本号已同步并注明
"以 AGENTS.md 与本文件为准"。

### 验证（合并后重跑，不是"上次是绿的"）

- 后端 `mvn test`：**149 项全绿**（我的 142 + 他们的 `WorkflowServiceTest` 3 项、
  `IssueClaimServiceTest` 4 项）。
- e2e：`workflow-guard` 4/4、`main-chain` 11/11、`next-step` 6/6、`chain-links` 5/5。
- 前端 `vue-tsc` 通过、`npm run build` 通过。
- 本地环境一度整套停着（MySQL 3306 / 后端 8080 / 前端 5173 都没在跑），先起 MySQL 后端才正常；
  注意 `toolchain\start-mysql.ps1` 是 UTF-8 无 BOM，用 PowerShell 5.1 跑会因中文乱码解析失败，
  需用 pwsh 或直接起 `mysqld`。

---

## v2.19.2 — 空状态别只说"暂无"，空库要有数据可看 (2026-11-23)

> 这一轮不加新功能，专门清两条一直挂着的 Medium：**KNOWN-019（空状态缺下一步）** 和
> **KNOWN-020（没有演示数据入口）**。两条其实是同一个病根：
> 页面在"什么都还没有"的状态下不给用户任何出路 —— 要么对着"暂无数据"发呆，
> 要么看着一堆 0 以为功能没做。

### KNOWN-019: 空状态缺少「下一步」引导 → ✅ 已解决

- **原现象**: 多数空列表只有"暂无 xx"四个字；只有甘特图和收件箱给了可执行指引。
- **做法**: 新增通用组件 `components/common/EmptyGuide.vue`，把空状态要做的事定成三条——
  这里会出现什么（title）、为什么现在是空的/它有什么用（reason）、下一步点哪里（action 主按钮）。
  纯展示组件，只 emit 不猜业务。
- **落地页面**（本轮把"只写暂无"的几处全部换掉）:
  - 公告：管理员看到「发布第一条公告」，普通成员看到"公告只在管理员发布后出现"——
    不再给普通成员一个点了会 403 的按钮；
  - 邮箱绑定：「绑定第一个邮箱」+ 一句"用你自己的 IMAP/SMTP 账号，密码只用于登录邮件服务器"；
  - Webhook：「创建第一个 Webhook」+ 说明"没配也不影响系统本身，只是外部系统收不到消息"；
  - 审批管理两个页签（表格空态改用 `#empty` 插槽）：说清"什么操作会落到这里"；
  - 工作台空库的"三步上手"后面加一条"或：一键生成示例数据"（见 KNOWN-020）。
- **仍未覆盖**: 工作台内部小面板（暂无动态 / 没有被驳回的变更）、Wiki 的历史版本/差异、
  迭代规划里的 Backlog 空态。这些属于"局部信息为空"，指引价值低，留待需要时再补。

### KNOWN-020: 缺少演示数据初始化入口 → ✅ 已解决

- **原现象**: 空库只有 3 用户 / 1 团队。工作台、迭代健康度、交付分析、审计日志全是 0 或空，
  既没法试用也没法演示 —— 看到的是"一堆 0"，很容易被当成"这功能没做完"。
- **做法**: 后端 `POST /api/admin/demo-data`（`DemoDataService`），一次事务里生成一套自洽数据：
  团队 → 项目 → 4 个看板列 → 3 个迭代（进行中/规划中/已完成）→ 15 个工作项（四种状态、三种类型、
  含逾期与临期）→ 5 条评论 → 6 条工时 → 3 条验收（其中 2 条 <b>PENDING 且提交人不是管理员</b>，
  这样「待我处理」里真的有东西）→ 2 份报告 → 5 条审计动态 → 2 条通知。
- **三个刻意的设计**:
  1. **幂等**：以项目名 `示例项目：移动端改版` 为标记，已生成过就返回现状（`created=false`），
     反复点不会把库堆满；
  2. **只给管理员**：`AccessControlService.isSystemAdmin` 判定，普通成员点了是 403 提示，
     前端也不显示入口（实测 zhangsan 看不到按钮）；
  3. **不碰已有数据**：只新建自己的项目和团队，删掉那个项目就干净了。
- **前端入口**: 工作台空库的三步上手后加"或：一键生成示例数据"；项目列表页头对管理员常驻
  「生成示例数据」按钮（有项目时也能用，这是实际演示最常用的入口）。共用
  `composables/useDemoData.ts`，确认框会列清"会建什么、不会动什么"。
- **自测撞到的一处 500（已修）**: 第一版把项目 Key 写死成 `DEMO`，而 `projects.key` 上有
  **全局**唯一索引，dev 库里的 demo 项目本身就占着 `DEMO` —— 一点就
  `Duplicate entry 'DEMO' for key 'projects.uk_project_key'`。改成先读现有 Key 再取
  （`allocateProjectKey()`），并补了单测 `shouldPickAFreeProjectKeyBecauseProjectKeyIsGloballyUnique`
  钉住它。这类"硬编码唯一值"的错误用单测挡比靠手点便宜得多。
- **顺手清掉的历史残留**: dev 库里躺着早期 e2e 跑挂留下的 8 条 `E2E任务*` 和一组
  「链路团队 / 链路项目」—— 演示数据想好看，这些必须先清掉（已按依赖顺序删除并核对计数）。

### 验证

- 后端：`mvn test` **142 项全绿**（新增 `DemoDataServiceTest` 4 项：拒绝非管理员、
  已有数据时幂等、真的铺满 15 卡/3 迭代/含"别人提交的待验收"、项目 Key 不硬编码）。
- 接口实测：`POST /api/admin/demo-data` → `created=true, issueCount=15`；再点一次 →
  `created=false, issueCount=15`（幂等）。
- 界面实测（Playwright）：工作台从空变成「当前共有 5 项个人任务 · 2 项审批或验收等待你处理」；
  看板/交付分析/审计日志均有内容；审批管理两个空态、Webhook 空态渲染出引导文案与按钮；
  管理员看得到「生成示例数据」、zhangsan 看不到。
- 前端 `vue-tsc` 通过。

---

## v2.19.1 — 「工作流不知所云」：配好了看不见、挡住了不说往哪走 (2026-11-23)

> 反馈只有六个字：**工作流不知所云**。去页面上一看，确实——这一页把"规则"藏起来了。

### BUG-101: 工作流配好之后看不懂，被拦住也不说还能去哪 [Medium]

- **现象（两部分，同一根因：信息只在一个视角里存在）**:
  1. 打开 `/projects/1/workflow`，页面上只有一行「当前「STORY」类型已配置工作流: scrum 工作流
     [编辑] [删除]」。**允许什么、禁止什么，一个字都没有** —— 得先点「编辑」进入拖拽画布才看得见。
     而画布本身是一堆抽象词：节点、转换规则、终态、执行条件 (空=所有人)，没有一处解释它们
     对应到看板上的什么东西。
  2. 看板上真被挡住时，提示是「工作流不允许从「待办」转换到「已完成」」——
     然后呢？用户不知道现在到底能往哪拖。后端 `/api/workflow/transitions`（可用转换列表）
     **前端从来没调用过**，接口一直在那儿。
- **修复**:
  - 设计器加只读概览（`ruleRows` 计算属性）：已保存的节点以彩色块列出、终态单独标出，
    规则渲染成表格「从 → 到 → 谁可以这么做」，条件翻成「任何成员 / 仅管理员 / 仅指派人 / 仅报告人」。
    不点「编辑」就能看懂。
  - 顶部加 `PageHint`：说明「不配置=随便拖，配置后只能走这些路径，按工作项类型分别生效，保存即生效」；
    空状态文案改成「「Story」类型没有配置工作流 —— 这一类型的任务可以随便拖」，并提示其它类型的状态。
  - 保存/套用模板的提示改成有信息量的：保存 → 「已生效：现在看板上「Story」只能按这 4 条规则移动」；
    模板 → 「模板已套用，确认或修改后点「保存工作流」才真正生效」（以前说"已应用"，容易以为已经生效）。
  - 看板侧接入 `getAvailableTransitions`：被挡时把消息合并成一条
    「工作流不允许从「待办」转换到「已完成」。当前可以从这里去：进行中」。
    注意 `request.ts` 的响应拦截器对非 200 已经弹过一条，所以这里先 `ElMessage.closeAll()` 再弹，
    否则会叠两条一样的 toast。
- **不是装饰的取证**: 新增 `e2e/workflow-guard.mjs`（4 条）—— 造一张自己的 STORY 卡，
  ① 设计器不点编辑就能看到 4 条规则和「谁可以这么做」列；
  ② 把它从「待办」拖到「已完成」（scrum 模板不允许跳步）→ 卡片回到原列，`columnId` 仍是 1；
  ③ 提示里带「当前可以从这里去：进行中」；
  ④ 再拖到「进行中」（允许）→ `columnId=2`、`status=IN_PROGRESS`。
  当前 **4/4**。
- **遗留**: 规则目前只能"看"和"手写"，不能可视化地拖动连线（画布还是拖看板列那种）；
  条件只支持三种角色，不支持"必须填完工时""必须通过验收"这类业务条件。

### 验证

- 前端 `vue-tsc` 通过。
- e2e：`workflow-guard.mjs` **4/4**；主线三套回归（`main-chain` 11/11、`next-step` 6/6、
  `chain-links` 5/5）不受影响。

---

## v2.19.0 — 主线自证：让一个全新账号走完 团队→项目→迭代→任务→验收→完成→报告 (2026-11-23)

> 这一轮不加功能。拿一个刚注册的账号，只走界面，从"建团队"走到"看报告"，
> 走到哪一步卡住就当成 bug 修。首轮 **5/9**，现在 **11/11**。
>
> 之后补一句自我校正：首跑那 4 个红里，有一个（Sprint 创建）**是判错了** ——
> 界面其实有提示，是用例自己没填必填项。真正的缺陷比记的小得多，已在本段改写。
> 记在这里是因为"用例红了就等于产品坏了"这个推断，本轮错了两次（另一次见 e2e 引导遮罩那条）。

### BUG-099: 有资格验收的人在「待我处理」里看不到这条待验收 [High]

- **现象**: 新账号建团队 → 邀请 admin 进团队 → 把任务提交验收。admin 打开工作台，
  `GET /api/workbench/my` 的 `pendingTaskReviews` 是**空数组**；但 admin 直接打开这个工作项，
  验收面板里"通过"按钮好好地在（后端 `assertReviewer` 也认他是管理员）。
- **根因（两处口径不一致）**:
  1. `IssueReviewService.listPendingForUser` 的规则是"团队成员 + 非提交人 + （系统管理员 / 项目负责人 /
     团队管理员 / 报告人）"，而 `WorkbenchService.mapPendingReviews` 又把结果按
     `projectService.listByUser(userId)`（我的项目）过滤了一遍。系统管理员、团队管理员往往
     **不在项目的成员表里** —— 于是"能批的人在待办里看不到，看得到的人未必能批"。
     `mapPendingChanges`（变更审批）是同一个写法，同一个毛病。
  2. `getMyWorkbench` 开头还有 `if (projectIds.isEmpty()) return emptyWorkbench();`：
     一个人不在任何项目里，整页就是空的，连他能审批的入口都一起没了。
- **修复**:
  - 两处 map 去掉"我的项目"二次过滤（只保留 `issue == null` 判空）—— 权限口径只由
    `listPendingForUser` 决定，不再各说各话。
  - `projectName()` 遇到不在缓存里的项目现查一次；`loadProjects` 改成可变 `HashMap`
    （原来是 `Collections.emptyMap()`，往里补项目会直接抛 `UnsupportedOperationException`）。
  - `getMyWorkbench` 不再因为"没有项目"整页返回空，只跳过需要项目列表的那几个查询。
- **真实栈对照**: 修前 admin `/api/workbench/my` → `pendingTaskReviews: []`；修后同一条待验收
  出现在「待我处理」，点进去 → `/projects/{id}/board?issue={id}` → 切「验收」→ 通过 → 状态 `APPROVED`。
- **回归防线**: `WorkbenchServiceTest` 新增 `shouldShowPendingReviewsFromProjectsImNotIn`
  （"无项目 + 有可审批的待验收"），`e2e/main-chain.mjs` 第 8 步从工作台入口验收。

### BUG-100: 表单校验失败时 `validate()` 的 reject 没人接（Sprint / 工时） [Medium]

> **先说更正**：这条最初记成"点创建没有任何反应、也没有任何提示"——**是错的**。
> 回看真实界面：起止时间没选时字段下方一直有红字「请选择起止时间」，弹窗也不会关。
> 当时是被自己的用例带偏了：用例从没选过日期，断言又只看"接口里有没有新 Sprint"，
> 于是把"用例没填必填项"写成了"产品没提示"。真实缺陷是下面这个。

- **现象**: 新建 Sprint、记录工时，必填项没填就点提交 —— 界面表现其实正常（红字 + 弹窗不关），
  但控制台里有一条被吞掉的异常（Playwright 记为 `pageerror: Object`）。
- **根因**: `SprintView.handleCreate()` 与 `WorkLogTab.handleCreate()` 都写成
  `await createFormRef.value.validate()` 而不接 reject。Element Plus 的 `validate()`
  校验不过时是 **reject**：异常没人接就成了未处理异常；更要紧的是**代码继续往下跑**
  —— 工时弹窗在"没通过校验"之后照样 `creating = true` 并发出请求，等于让服务端再兜一次错。
- **修复**: 两处都改成 `const valid = await validate().catch(() => false); if (!valid) return`。
  Sprint 侧另留一条兜底提示（`请选择 Sprint 的起止时间`），正常路径不会走到。
- **取证方式（这次才做对）**: 不要只断言接口，先读页面本身 ——
  `.el-form-item__error` 得到 `["请选择起止时间"]`、toast 为空、弹窗仍在；
  同时监听 `pageerror` 才看见那条被吞的异常。
- **顺带**: 全前端只有这两处是这种写法（`await .*\.validate\(\)` 共 2 命中），已全部修掉。

### 单人团队：验收本身是一个死锁（本轮补上说明与出口）

- **现象/根因**: 验收要求"不能验收自己提交的结果"，而单人团队里提交人往往同时是项目负责人 +
  报告人 —— 唯一"能验收"的只剩自己。任务会永远停在待验收，界面上一个字都不解释。
- **修复**: 详情页在"待验收 + 团队里除我之外没有别人"时给一条警示，说清规则并提供「去邀请成员」
  （`IssueDetailDialog` 的 `checkReviewable()` + `.next-step-blocked`）。
- **用例**: `main-chain.mjs` 先断言这条提示确实出现，再去邀请成员、再验收 —— 提示和真流程一起验。

### 新增 `e2e/main-chain.mjs`（主线自证）

- 11 个检查点，全程走界面（只有"注册一个全新账号"用接口，注册页不是考察点）：
  建团队 → 建项目 → 建迭代 → 看板建任务 → 待办→进行中 → 提交验收 → 无人验收提示 → 邀请成员 →
  管理员从工作台验收通过 → 一键标记完成 → 项目报告有生成入口。
- 顺手修掉两个用例可靠性问题：
  - 引导遮罩：真正的 key 是 `localStorage.mimo_tour_state`（按主题存 `{status,v}`），
    用例之前写的 `sessionStorage` 假 key 完全无效 —— sprint 页的引导自动启动，遮罩把点击吃掉，
    表现成"按钮点不动"，白查了半天。
  - 报告页断言前不再直接读 `body.innerText`（页面还没挂载就读，会误判成"没有生成入口"）。
- 清理改先用新账号自己的权限删（项目负责人/团队管理员），删不掉才打警告，不再假装成功。

### 验证

- 后端 `mvn test`：**138 通过**（v2.18.5 为 137，新增 1 条）。
- 前端 `vue-tsc` 通过。
- e2e：`main-chain.mjs` **11/11**、`next-step.mjs` **6/6**、`chain-links.mjs` **5/5**。
- 真实栈：见 BUG-099 的修前/修后对照。

---



> 起因只是收尾时顺手 `select count(*) from user_roles`：89 行。库里一共 13 个用户，
> 每人最多只该有 1 条 GLOBAL 授权 —— 多出来的 76 行全是重复。

### BUG-098: 每启动一次后端，就重复插一遍角色授权 [High]

- **现象**: `user_roles` 里 admin 有 13 条完全相同的 `SUPER_ADMIN/GLOBAL` 记录，其余用户各
  4~13 条不等；`GET /api/role/user/{id}/assignments` 也跟着返回十几条重复角色。
  重复条数正好等于后端启动次数（当天重启了 13 次）。
- **根因（两处，缺一不可）**:
  1. `db/migration/V11__rbac.sql` / `V12__fix_user_role_migration.sql` 用
     `INSERT IGNORE INTO user_roles ... scope_id = NULL` 做幂等。表上确有唯一键
     `uk_user_role_scope (user_id, role_id, scope_type, scope_id)`，但 **MySQL 的唯一键认为
     NULL 互不相等**，GLOBAL 行（scope_id 为 NULL）等于完全不受约束，`INSERT IGNORE` 从不"忽略"。
     而 `MigrationRunner` 每次启动都会把所有 `V*.sql` 重跑一遍（没有版本记录表），于是每次 +1。
  2. `RoleService.assignRole` 的幂等检查写的是
     `.eq(req.getScopeId() == null, UserRole::getScopeId, null)`，生成的 SQL 是 `scope_id = NULL`
     —— 恒为假。所以走接口手动分配同样每次插一条。
- **修复**:
  - 迁移脚本改为 `INSERT ... SELECT ... WHERE NOT EXISTS (… scope_id IS NULL)`（NULL 安全）；
    `V12` 开头新增一段可重复执行的重复行清理，只保留 `id` 最小的那条。
  - `RoleService.assignRole` 改成 `scopeId == null ? wrapper.isNull(...) : wrapper.eq(...)`，
    并用 `selectCount` 判断。
  - **没有新建 `V2x` 迁移**：schema 层面给 `scope_id` 加 `NOT NULL DEFAULT 0` 才是彻底的解法，
    但那要占一个新版本号，与今晚要推的工作流/面板迁移号有冲突风险。当前做法在代码层解决了
    全部两条写入路径，schema 变更留待后续统一排。
- **真实栈对照**: 修复前 `user_roles` 89 行（每人 4~13 份）；带修复的 jar 启动一次 → 13 行
  （每人 1 份）；再启动一次 → 仍是 13 行（日志 `V12 … ok=3 skipped=0 failed=0`）；
  连续 3 次 `POST /api/role/assign` 同一角色 → 分配记录数 1 → 1，不再增长。
- **回归防线**:
  - `RoleServiceTest` 加两条用例直接断言生成的 SQL（`scope_id IS NULL` / `scope_id = ?`）。
    旧用例只 mock 了 mapper 返回值，看不到 SQL 长什么样，所以这个 bug 一直没被拦住。
  - 新增 `RoleMigrationScriptTest`：迁移脚本里禁止对 `user_roles` 用 `INSERT IGNORE`，
    且写入语句必须带 `NOT EXISTS`；`V12` 必须含去重语句。
  - 顺带关闭 KNOWN-022（`user_roles` 唯一键对 GLOBAL 行失效）——以代码层幂等替代 schema 变更。

### 首屏「接下来做什么」先下结论的假状态 [Medium]

- **现象**: `e2e/next-step.mjs` 有一次失败："提示=当前没有卡在你这儿的事，三件事=0"，
  可是同一时刻接口 `GET /api/workbench/my` 明确返回 1 条任务。
- **根因**: 区块的渲染条件是"首屏 dashboard 已加载"，而它依赖的是**工作台的另一路请求**。
  数据没到时 `nextUps` 为空 → 直接渲染成"没有卡在你这儿的事"——界面先撒一次谎再改口。
- **修复**: 工作台加载中显示骨架屏与"正在整理你今天该做的事…"，不再输出结论；
  用例侧改为等待"结论态"（有事项 / 上手清单 / 空态）而不是等区块可见。

### 验证

- 后端 `mvn test`：**137 通过**（v2.18.4 为 133，新增 4 条）。
- 前端 `vue-tsc` 通过；`e2e/next-step.mjs` **6/6 连跑两次**稳定；`e2e/chain-links.mjs` **5/5**。
- 真实栈：两次启动 `user_roles` 行数 13 → 13；`/api/role/user/1/assignments` 返回 1 条。

---

## v2.18.4 — 主链路给出「下一步」+ 修菜单可点却进不去的权限口径 (2026-11-23)

> 上一轮（v2.18.3）把断掉的线接上了，这一轮回答"接上之后每一步该点哪里"：
> P1 工作项详情的四段链路、P2 工作台首屏的"接下来做什么"、P3 每页一句话定位。
> 顺带挖出一个"看得见、进不去"的权限口径问题。

### BUG-097: 「设置」菜单能看见，点进去被弹回工作台 [High]

- **现象**: 登录一个 `users.role = ROLE_ADMIN`、但 RBAC 里只有 DEVELOPER 的账号，
  侧边栏「设置」分组正常显示（含角色权限 / 操作审计 / Webhook / 邮件日志），
  点任一条却跳回工作台，顶部提示"无访问权限：role.manage"。
- **根因（两条口径打架）**:
  1. 菜单可见性 = RBAC 权限码 **∪** 旧字段 `ROLE_ADMIN`（v2.18.3 的 `isLegacyAdmin` 兜底）；
  2. 路由守卫的 `meta.requiresPermission` 只判 RBAC —— 于是"菜单按并集显示、守卫按 RBAC 拒绝"。
  这与后端 `AccessControlService.isSystemAdmin`（同样是并集）也不一致。
  另外 `store/permission.ts` 的 `fetch()` 写的是 `if (loading.value) return`，
  两个入口（MainLayout 挂载 + 路由守卫）同时触发时，后到的那次拿到 `undefined` 就走，
  `loaded` 仍是 false → `has()` 判无权限。
- **修复**:
  - 守卫与后端同口径：`permStore.has(code) || userInfo.role === 'ROLE_ADMIN'`；
  - `fetch()` 改为**返回同一个在途 Promise**（`inflight`），并发调用都等同一个请求；
    同一会话成功拉取后不再重复请求。
- **决定性对照（真实栈 + 真实浏览器）**: 临时把 lisi 的 `users.role` 改成 `ROLE_ADMIN`
  （RBAC 仍是只有 14 个码的 DEVELOPER），访问 `/admin/audit-logs`：
  - 守卫只判 RBAC（旧行为）：`url=/`、页面没渲染 → **点进去被弹回**；
  - 守卫判并集（修复后）：`url=/admin/audit-logs`，页面与说明文案正常渲染。
  验证后已把 lisi 的角色改回 `ROLE_MEMBER`。

### P1: 工作项详情给出「下一步」（四段链路 + 唯一主动作）

- **问题**: 链路各段散落在不同地方 —— 状态在「基本信息」的下拉框、验收在第六个 Tab、
  报告在项目菜单里。做完一件事之后，界面上没有任何地方告诉你"然后呢"。
- **实现**: `IssueDetailDialog.vue` 顶部新增「下一步」条：
  - 四段节点 `待办 → 进行中 → 待验收 → 已完成`，按 status + 最新验收记录高亮"走到哪一段"；
  - 一句话说明 + 一个主动作（必要时附第二个出口）：
    待办→**开始处理**；进行中→**提交验收**（可直接拉起提交弹窗）或直接标记完成；
    验收中→**去验收处理**；被打回→**重新提交验收**（带打回原因）；已通过→**标记完成**；
    已完成→**查看项目报告** / 回到看板。
  - 状态推进复用「保存」的同一份载荷（抽出 `buildPayload()`），所以看板列、bugStatus、
    依赖字段不会两条路分叉；失败会回滚界面状态，不让界面骗人。
  - `IssueReviewPanel` 通过 `@loaded` 回传验收记录（详情页不再重复请求），
    并 `defineExpose` 出 `openSubmitDialog()` 供「下一步」直接调用。

### P2: 工作台首屏「接下来做什么」

- **问题**: 首屏是"平台有什么"（统计卡 + 四五个面板），不是"你现在该做什么"；
  0 数据时更是全 0，只能靠新手引导讲控件。
- **实现**: `Dashboard.vue` 在统计卡下面加一块：
  - 有数据时按"卡住别人 → 卡住自己 → 快到期"排序给最多三件事（被打回 / 待我处理 / 已逾期 /
    进行中 / 待开始），每条直达对应工作项；
  - 一个项目都没有时切成三步上手清单：建项目 → 建迭代 → 建第一个任务。

### P3: 每页一句话定位（`PageHint`）

- 新增 `components/common/PageHint.vue`，给 Webhook 集成、操作审计、邮件发送日志、公告、
  日历、收件箱、邮箱绑定七个页面各补一句"这一页解决什么问题"。
- Webhook 那一句如实写明**当前只覆盖工作项新建/更新/删除三种事件** ——
  以前页面看着像通用集成，实际只在这三处触发（v2.18.3 审计发现），与其让人猜不如写在页面上。

### 验证

- 新增 `e2e/next-step.mjs`：**6/6 通过**（四段链路文案与主动作、点「开始处理」后接口状态真的变
  IN_PROGRESS、提交验收弹窗被拉起、首屏三件事可点且直达、审计页说明文案渲染）。
- 回归 `e2e/chain-links.mjs`：**5/5 通过**。
- 前端 `npm run build`（含 `vue-tsc`）通过；本轮无后端改动。
- 用例造出来的工作项由脚本删除（走 `DELETE /api/issues/{id}`，服务端软删），
  临时改的 `users.role`、临时授予的角色、审批与通知数据均已还原/清理。

---

## v2.18.3 — 功能链路闭合：把"散着的功能"接成一条路 (2026-11-23)

> 起因是你那句"很多功能遍布很散，没有形成一股完整的链条……很多东西浮于形式"。
> 逐个查下去，功能其实都在，坏的是**页与页之间的那一步**：入口指向重复、链接落不到地方、
> 通知点了没反应、菜单还在用旧字段判断。这一轮**不加任何新功能**，只把已有的线接上。

### BUG-093: `@RequirePermission` 切面 NPE，「角色权限」页所有写操作 500 [High]

- **现象**: `POST /api/role/assign` 直接 `500 服务器内部错误`。后端日志：
  `NullPointerException: Cannot invoke "com.mimo.common.RequirePermission.scopeType()" because "rp" is null`。
- **根因**: `PermissionAspect` 写的是 `@Around("@annotation(rp) || @within(rp)")`。
  两个切点用 `||` 合并时，参数绑定**只在命中的那一支有效**，另一支传 `null`；
  `RoleController` 用的是方法级注解 → 命中 `@annotation`，`@within` 那支给的就是 `null`。
- **影响**: 角色新增 / 删除 / 分配 / 撤销 **四个写接口全部 500** ——
  也就是说 v2.18.2 刚统一的 RBAC 权限口径，在管理页面上根本没有被真正用过。
- **修复**: 切点改成全限定名（不再绑定参数），advice 内用 `resolveAnnotation()` 依次从
  方法 → 实现方法 → 声明类取注解；取不到就放行而不是 NPE。
- **验证**: 新增 `PermissionAspectTest` 5 项（方法级拒绝 / 方法级放行 / 类级生效 / 未登录 401 /
  无注解放行）；真实栈 `POST /api/role/assign` → `code=200`，`DELETE /api/role/assign` → `code=200`
  （修复前同一个请求是 500）。

### BUG-094: 所有通知邮件里的「查看详情」都是死链 [High]

- **现象**: `EmailNotificationService#buildActionUrl` 给 comment / assignment / mention / issue_status
  一律返回 `/issues/{id}`；而 `frontend/src/router/index.ts` **没有这条路由**，也没有 `addRoute`
  动态注册 → 点开被 catch-all 兜走，落到登录页或工作台。等于每封通知邮件的按钮都作废。
- **修复**: 新增落地页 `frontend/src/views/IssueRedirect.vue` + 路由 `issues/:id`：
  用 issueId 反查 projectId，再 `replace` 到 `/projects/{pid}/board?issue={id}`（用 replace 是为了
  按"返回"不回到中转页）；403 / 404 分别给出"无权限 / 已删除"的提示与两个出口，不留白屏。
  后端不用改 —— 它拼的 `/issues/{id}` 现在真的存在了。
- **验证**: `e2e/chain-links.mjs` 用例 2，真实浏览器访问 `/issues/13` →
  落到 `/projects/1/board?issue=13` 且详情弹窗打开。**基线（改动收起后）同一用例 20s 超时失败。**

### BUG-095: 工作台 4 个快捷入口指向同一个页面 [Medium]（KNOWN-014 收口）

- **现象**: `Dashboard.vue` 的 `quickActions` 里「项目管理 / 看板视图 / Sprint / 项目报告」
  四项 `route` 全是 `/projects` —— 点"看板视图"永远看不到看板，用户以为功能没做。
- **修复**: 改为 `computed`，看板 / 迭代 / 报告带上「我的项目」第一个（接口按最近活跃返回）的
  projectId；没有项目时退回项目列表，并用按钮 title 说明"先创建或加入一个项目"。
- **验证**: `e2e/chain-links.mjs` 用例 1，落到 `/projects/1/board`，title=`打开「Mimo 演示项目」的看板`
  （基线 15s 超时失败）。

### BUG-096: 审批类通知点了回工作台 [Medium]

- **现象**: `handleNotifClick` 只认 `relatedType === 'ISSUE'`，其余一律 `router.push('/')`。
  审批结果通知（`relatedType = APPROVAL_REQUEST`）就这样被吞掉 —— 收到"审批已通过"，
  点进去却回到工作台，还得自己去设置里翻。
- **修复**: ISSUE → `/issues/{id}`（和邮件同一个入口，一处修两处通）；
  APPROVAL_REQUEST → `/admin/approvals?id={id}`；`ApprovalList.vue` 支持 `?id=`，
  高亮那一行并在顶部提示"来自通知"。
- **验证**: `e2e/chain-links.mjs` 用例 5（lisi 提 PROJECT_UPDATE → admin 通过 → lisi 点通知 →
  `/admin/approvals?id=3`）；基线代码读 `git show HEAD:` 可见只有 ISSUE 分支、其余 `push('/')`。

### 技术债（不计入条目）：前端「设置」菜单改按 RBAC 能力判断

- **现象**: `MainLayout.vue` 用 `userStore.userInfo?.role === 'ROLE_ADMIN'` 决定要不要显示整个
  「设置」分组。后端 v2.18.2 已把"是不是管理员"收敛到 `AccessControlService`（RBAC 优先），
  前端还在看旧字段 → 只在 RBAC 里有角色的管理员**后端放行、前端藏起来**。
- **修复**: 用 `usePermissionStore` 逐项判权限码（`level.manage` / `role.manage` /
  `approval.manage` / `audit.log.view` / `settings.manage`），分组成员为空则整组不显示；
  旧字段只保留"权限码还没拉回来"时的兜底，避免刷新瞬间菜单闪一下。
- **验证**: `e2e/chain-links.mjs` 用例 3 —— lisi（`users.role=ROLE_MEMBER`）临时授予 RBAC
  `SUPER_ADMIN`：授权前看不到管理项 → 授权后看得到 → 用例结束时自动撤销。
  基线代码读的是旧字段，这条恒为 false，所以该用例在基线上必红。

### 等级 ≠ 权限：把话说在页面上

- **问题**: `UserLevel` 后端只有 `UserLevelService` 自己的 CRUD，**没有任何业务逻辑读它** ——
  改 L1-L4 不改变任何人的可用功能。而页面副标题写的是"配置 L1-L4 铭牌权限体系"，与事实不符。
- **处理**: 副标题改为"权益铭牌：只影响展示（头像徽章、等级名），不参与任何权限判断"，
  顶部加一条 `el-alert` 说明"等级 ≠ 权限"并给出跳转「角色权限」的入口。
  **没有删功能**，只是不再让它假装有作用。

### 验证

- `mvn test`：**133 通过**（v2.18.2 是 128，新增 `PermissionAspectTest` 5 项）。
- 前端 `npm run build`（含 `vue-tsc` 类型检查）通过。
- 真实浏览器 `e2e/chain-links.mjs`（新增）：**5/5 通过**；同文件在基线代码上 **2/4 通过**
  （用例 1、2 必红；用例 4/5 是新增后才有诊断力）。
- 真实栈冒烟：`POST /api/role/assign` 200 / `DELETE` 200；`/issues/13` 落地；
  用例产生的审批、通知、临时角色授权已从开发库清理（approvals=0、stray_grants=0）。

---

## v2.18.2 — 技术债清理：权限口径 / AI 可复现 / 通知触发点 (2026-11-23)

> 计划书 §七 的三条遗留项。都不是"新 bug"，是"老做法会持续产生 bug"的结构性欠账，
> 因此登记在本文件但**不计入 BUG/KNOWN 条目数**（统计口径不变，仍为 100）。

### 技术债①: 新旧权限判断并存 → `AccessControlService` 单一入口

- **问题**: 11 处各写各的 `"ROLE_ADMIN".equals(user.getRole())`，公告还从 JWT 的 `ROLE_ADMIN` 授权项里读。
  同一句"是不是管理员"在不同接口上口径不同 —— 计划书原文就是「个别接口可能绕过 RBAC」。
- **取证位置**: `backend/mimo-app/.../service/` 的 `ApprovalService`、`IssueChangeRequestService`、
  `IssueReviewService`、`ProjectAccessService`、`TeamService`、`WorkflowService`、`WikiService`、
  `UserLevelService`，以及 `controller/` 的 `UserLevelController`、`ApprovalController`、`AnnouncementController`。
- **修复**: 新增 `com.mimo.service.AccessControlService`，只回答两个问题：
  `isSystemAdmin`（RBAC 权限码 `role.manage`，V11 里仅授予 SUPER_ADMIN）+ 旧字段兜底；
  `isTeamAdmin`（系统管理员 ∪ RBAC `team.manage` 的 TEAM 作用域 ∪ `team_members.role=ROLE_ADMIN`）。
  两套口径**求并集**：老管理员不会被误挡（回归为零），"只在 RBAC 里有角色、旧字段没同步"的账号也能生效。
  旧字段这条路可用 `mimo.access.legacy-role-fallback=false` 关掉，关掉即纯 RBAC 判定。
- **为什么旧字段先查**: 旧路径各一次查询，RBAC 路径要查角色 + 权限两张表；而
  `ProjectAccessService.requireProject` 在每个请求上都会被调用，让管理员/成员在第一次查询就短路，
  避免所有读接口白多两次 SQL。
- **验证**: `AccessControlServiceTest` 11 项，含"旧字段命中后不再查 RBAC""只在 RBAC 有 `role.manage`
  的账号不再被拒""团队判定必须落在 TEAM 作用域""开关关闭后 `ROLE_ADMIN` 彻底失效"。

### 技术债②: AI 输出不可复现 → 提示词版本化 + 采样参数真的下发

- **取证（两处硬伤）**:
  1. `AiChatRequest.temperature/maxTokens` **一路被丢弃**：`AiService.chat` 调的是 `provider.chat(sys,user)`，
     `DeepSeekProvider` 内部硬编码 `0.7 / 1024`。Controller 里那句
     `.temperature(0.3)  // 低温度保证结构化输出稳定` 是注释，从未生效 —— 结构化输出其实一直按 0.7 采样。
  2. `AiChatResponse.model` 从没被赋过值，`AiProvider` 也没有模型名可问：复盘时连"用的哪个模型"都答不上。
- **修复**:
  - `AiProvider` 新增 `chat(sys, user, temperature, maxTokens)`（抽象）与 `getModel()`；
    原 2 参版本降为 `default` 走默认值，老调用点零改动。
  - `AiService` 每次调用打一行 `[AI] model=… prompt=… temperature=… maxTokens=…`，
    并把 `model / temperature / promptRef` 填进响应；`chatJson` 改走 `chat()`，JSON 分支同样留痕。
  - 新增 `com.mimo.ai.prompt.PromptRegistry` + `PromptTemplate`：6 个提示词
    （`ISSUE_POLISH` / `ISSUE_PRIORITY` / `ISSUE_DRAFT` / `STORY_SPLIT` / `STORY_POINTS` / `CHAT_DEFAULT`）
    集中登记，每个带 `CODE@vN` 与正文指纹（改文案忘改版本号能被抓）；温度与 maxTokens 跟提示词写在一起，
    结构化输出 ≤ 0.3。`AiController` 里 6 处内联 systemMessage 与 `buildParseIssueSystemPrompt()` 全部删除。
- **验证**: `PromptRegistryTest` 7 项（版本格式、结构化提示词温度上限、指纹稳定/随正文变化、
  未登记 code 直接抛、上下文追加不改版本）；`AiServiceTest` 新增 3 项，直接断言
  `verify(aiProvider).chat("sys","hi",0.2,512)`、缺省值回落 `INLINE@v0`、JSON 分支下发 0.3。

### 技术债③: 通知触发点遗漏 → 评论 @提及

- **取证**: `CommentService.create` 只通知 `issue.assigneeId`。评论里写 `@某人` 不会有任何反应，
  而"@ 一下"正是团队里最主要的派活方式。
- **修复**: `parseMentions` 解析提及（收窄字符集：`@张三，谢谢` 解析成「张三」，邮箱 `a@b.com` 不算提及）→
  按用户名查人 → 站内通知 `type=MENTION` + WebSocket 实时推送；跳过作者本人、
  跳过刚收到「新评论」的指派人（同一件事不打扰两次）。
  `NotificationService.mapEmailType` 把 `MENTION` 归到既有的 `comment` 邮件偏好，不新增开关。
- **验证**: `CommentServiceTest` 4 项（被 @ 者收到含任务 key 与作者的通知、自己/不存在的人不通知、
  指派人只收一条、字符集边界）。

- **回归**: `mvn test` **128 项全绿**（改造前 103 项，+25：AccessControlService 11、PromptRegistry 7、
  AiService +3、CommentService +4）。

- **真实栈冒烟**（`java -jar` + 本地 MySQL 8，端口 18080，因为单测用的裸构造覆盖不到新 Bean 的装配）：
  - 应用启动成功 → `AccessControlService` 注入 `RoleService` 无循环依赖，容器正常起来（这是单测看不到的风险）；
  - 评论 `@zhangsan …` → 库里出现 `user_id=2 type=MENTION related_id=13` 的通知（改造前这条记录不可能存在）；
    同一 issue 再发 `@admin 自己@自己` → `MENTION` 计数仍为 1，确认不给自己发；
  - `lisi`（`users.role=ROLE_MEMBER`）发公告 → `code=403 无权操作，仅管理员可发布/编辑公告`，库里没多出行；
  - 临时给 zhangsan 插一条 RBAC `SUPER_ADMIN`（旧字段仍是 MEMBER）→ 同一接口 `code=200` 创建成功，
    随后已删除该临时授权 —— 这就是"两套口径求并集"的实证：改造前这个账号会被挡在门外；
  - `POST /api/ai/estimate-story-points` 日志出现
    `[AI] model=deepseek-chat prompt=STORY_POINTS@v1 temperature=0.2 maxTokens=512`
    （改造前无 prompt 标识，且实际下发的是硬编码的 0.7）。
  - 冒烟用的评论/公告/通知已清理，未在开发库里留下测试数据。冒烟过程中新发现 KNOWN-022。

---

## v2.18.1 — Markdown / 邮件正文 XSS 修复（取证驱动）(2026-11-23)

### BUG-092: 「正则黑名单」清洗挡不住 XSS，`<details ontoggle>` 在真实浏览器里直接执行 [High]

- **严重程度**: High（存储型 XSS —— 恶意内容只要落进评论 / Wiki / 公告 / 报告 / AI 气泡 / 邮件正文，
  点开页面的**其他成员**就会中招；邮件正文那条更糟，发信人可以是团队外任何人）
- **文件**: `frontend/src/utils/markdown.ts`（`renderMarkdown()` 的调用点 6 个：
  `CommentSection.vue`、`Wiki.vue`、`Announcements.vue`、`ReportList.vue`、`AiChatPanel.vue`、`IssueDetailDialog` 相关）、
  `frontend/src/views/EmailInbox.vue`（自己另抄了一份更弱的正则清洗）
- **现象**: 用户输入经 `marked` 渲染后直接 `v-html`。所谓防护是 `sanitize()` 里的 8 条正则：
  删 `<script>`/`<iframe>`/`<object>`/`<embed>`、删带引号的 `on\w+="…"`、删 `javascript:` 字符串。
- **取证方式**: 新增 `e2e/xss-markdown.mjs` —— 不需要后端/前端 dev server，用 Playwright 起真实 Chromium，
  把仓库里真实的 `markdown.ts` 用 esbuild 转译后**原样执行**（不是抄副本），渲染结果用 `innerHTML` 注入
  （与 `v-html` 完全一致），再用 `window.__xssFired` 哨兵判断**脚本是否真的执行**，而不是 grep 关键词。
- **实测（修复前 = git HEAD 的版本，6/10 挡住）**:

  | 载荷 | 结果 |
  |---|---|
  | `<details open ontoggle=window.__xssFired=true>` | **脚本已执行** —— 事件处理器不带引号就不匹配正则 |
  | `[点我](jav&#x61;script:window.__xssFired=true)` | 实体解码后得到 `javascript:` 链接，点一下就执行 |
  | `<math><mtext><table><mglyph><style><img …>` | mXSS 解析器混淆，`<style>` 留在 DOM 里 |
  | `<style>div{background:url("javascript:…")}</style>` | `<style>` 标签根本没被处理 |

  邮件正文那份清洗连 `javascript:` 都不删，同一批载荷全过。
- **根因**: 用正则做 HTML 过滤 —— 正则不懂 HTML 解析，引号可有可无、字符可用实体编码、
  浏览器还会为修正畸形标签重新分组（mXSS）。黑名单又永远漏项。
- **修复**: 换成 DOMPurify（按真实 DOM 解析 + 白名单重建），`sanitizeHtml()` 统一出口，
  Markdown 与邮件正文共用；`dompurify` 从 `jspdf`/`html2pdf.js` 的间接依赖提为直接依赖（`^3.4.8`）。
- **验证**: `node e2e/xss-markdown.mjs` 当前工作区源码 **10/10 PASS**（含"正常 Markdown 仍能渲染"回归项），
  同一脚本跑修复前的 `git HEAD` 版本稳定复现 3 条失败 + 1 条真执行，证明用例本身有诊断力；
  `npm run build`（vue-tsc + vite）通过，新增 DOMPurify 使 markdown 共享块 69.6 kB / gzip 23 kB。
- **仍未做**: 后端存储前不做 strip（只靠前端白名单，导出/第三方消费方仍需自行转义）；
  `Profile.vue` 的活动流 `v-html="item.text"` 走的是后端拼的文案，本次未纳入。

---

## 历史补录 — 2026-11-20 审计

> 用户要求"系统出现过的 bug 都要登记"。审计方式：把仓库里所有 `fix:` 提交拉出来，
> 逐条对照 bug-log 的条目，找出**改了代码但没写文档**的 8 个问题（BUG-084~091）。
> 这些都不是新问题，是当时直接修掉、漏了记录的；细节以对应提交的 diff 为准。

### BUG-084: 移动/删除任务报 500，`Field 'user_id' has no default value` [High]

- **严重程度**: High（看板核心操作直接不可用）
- **文件**: `BoardController` / `BoardService`、`IssueController` / `IssueService`
- **现象**: 拖拽移动任务、删除任务返回 500，后端日志为 `Field 'user_id' has no default value`。
  两次独立踩坑：v2.6.2（删除）、v2.6.4（拖拽移动）。
- **根因**: 写 `activity_logs` 时 `user_id` 为必填，但 controller → service 链路没把当前用户传下去。
- **修复**: 两个入口都补 `userId` 透传（提交 `056b3a0`、`9af0c17`）。
- **教训**: 审计日志类"横切"字段必须由 service 显式接收，不能依赖 `SecurityContext` 隐式取（单测里没有上下文就会炸）。

### BUG-085: `spring.sql.init` 在 Spring Boot 2.7 + MySQL 下不执行迁移，缺表导致接口 500 [High]

- **严重程度**: High（本地/服务器都可能缺表，表现为某个模块整体 500）
- **文件**: `backend/mimo-app/src/main/java/com/mimo/config/MigrationRunner.java`、`application.yml`
- **现象**: 本地库 `work_logs` 表一直没被创建，打开看板时 WorkLog 接口 500。
- **根因**: `spring.sql.init` 在 2.7 上的行为依赖 `schema.sql` 位置与数据源初始化时机，对 MySQL 不可靠；
  迁移脚本写进 `spring.sql.init.data-locations` 后并没有按预期执行。
- **修复**: 改为自定义 `MigrationRunner implements ApplicationRunner`，启动时显式按版本号执行 `V*.sql`，
  重复执行把 `Duplicate column` / `already exists` 视为已执行（提交 `80d5adf`）。
- **后续**: 这个类后来又修过两次——注释剥离（V17 文件名以注释开头被整条跳过）与连接泄漏（没归还 Hikari 连接）。

### BUG-086: 甘特图接口 404（部署跑的是旧 jar）+ 前端 5xx 拦截器吞掉后端错误信息 [High]

- **严重程度**: High（新功能"写了但没上线"，且错误提示无法排障）
- **文件**: `backend/pom.xml`、`application.yml`、`frontend/src/api/request.ts`、`IssueDetailDialog.vue`
- **现象**: 甘特图页面 404；同时 AI 创建任务失败时前端只弹"服务器错误"，看不到后端具体原因。
- **根因**: ① 部署打包没有显式 `mainClass`，repackage 出来的 jar 不是最新代码，`GanttController` 根本没加载；
  ② `request.ts` 的 5xx 分支只用了通用文案，丢掉了 `response.data.message`。
- **修复**: pom 显式指定 mainClass + 启用一次性迁移；拦截器改成 4xx/5xx 都优先展示后端 message（提交 `b36f18b`）。

### BUG-087: 新用户/无项目成员用户的工作台显示全库统计，而不是零数据 [Medium]

- **严重程度**: Medium（新注册用户打开首页就看到别人的任务量，数据口径错误）
- **文件**: `backend/.../service/DashboardService.java`
- **现象**: 刚注册、不属于任何项目的账号，工作台统计卡片有数字（把全库记录算进去了）。
- **根因**: 统计查询没有按"当前用户可访问的项目集合"过滤，直接全表统计。
- **修复**: 重写 DashboardService，无项目成员关系时返回零数据（提交 `f72e56a`，随后 `be3aefb` 让文案区分
  "没有项目"与"有项目但没任务"两种空态）。
- **教训**: 所有聚合接口都要先收敛"可见范围"，再统计。

### BUG-088: 工作台背景不是纯白底（MagnetLines 装饰层压在白底上）[Low]

- **严重程度**: Low（观感问题）
- **文件**: `frontend/src/views/Dashboard.vue`
- **现象**: 工作台页面整体偏灰/有网格纹理，与其它页面的纯白卡片不一致。
- **修复**: 移除 MagnetLines 背景层（提交 `ac88330`）。
- **备注**: 属于早期"炫技装饰"留下来的，后续统一到设计 token。

### BUG-089: 登录/注册页品牌名与光效不一致（品牌名用发光字抢焦点）[Low]

- **严重程度**: Low（视觉一致性）
- **文件**: `frontend/src/components/layout/AuthLayout.vue`、`views/Register.vue`
- **现象**: 登录页 Mimo 品牌名带 ShinyText 流光，比表单还抢眼；注册页表单标题又没有同样的强调，两页观感割裂。
- **修复**: 品牌名恢复静态深色 `#1a1a2e`，把紫光效果挪到注册页表单标题上，与登录页对齐（提交 `eebac92`、`f71c8df`）。

### BUG-090: 本地 java 进程持有 jar 的 mmap 锁，`mvn clean` 删不掉文件导致部署失败 [Medium]

- **严重程度**: Medium（部署脚本第一次跑必失败，且报错看不出原因）
- **文件**: `deploy.ps1`
- **现象**: 后端还在跑的时候执行部署脚本，`mvn clean` 报无法删除 `mimo-app-*.jar`。
- **根因**: Windows 上运行中的 JVM 对 jar 持有内存映射锁；脚本没有先停旧进程。
- **修复**: deploy.ps1 增加 0/4 "Kill local Java"阶段（WMI 按 jar 名找进程并 `Stop-Process`），
  并给 `mvn clean package` 加 3 次重试（提现 Defender/文件系统瞬时占用，提交 `000b9d6`）。
- **备注**: 本次开发中又踩过一次同样的坑（会话里因为 jar 被占用导致 `mvn package` 失败），说明这个保护是必要的。

### BUG-091: 前端构建/运行时导入错误集合（import 路径、缺失导入）[Low]

- **严重程度**: Low（构建直接红，但一眼可见、修起来快）
- **文件**: `components/issue/shared/CommentSection.vue`、`components/common/AiChatPanel.vue`、`views/AuthLayout.vue`
- **现象与修复**:
  - `CommentSection.vue` 引用 `@/stores`（复数），实际目录是 `@/store` → 构建报模块找不到（提交 `e1c57cb`）
  - `AiChatPanel.vue` 用了 `computed` 但没从 `vue` 导入 → 运行时 `computed is not defined`（提交 `0bc60e9`）
  - 同一批 TS 构建错误（8 处）随 `init.html` 粒子动画一起修掉（提交 `0f63ca3`）
- **教训**: 这些都能被 `vue-tsc` 拦住，所以 `npm run build` 必须进 CI，不能只在发布前手动跑。

---

## v2.17.0 — 新手引导重做 (2026-11-20)

> 触发原因：用户反馈"这个引导太简陋而且有逻辑问题"。
> 用真实浏览器把旧引导的完整生命周期跑了一遍（自动触发 → 逐步 → 跳过 → 关闭 → 重播），
> 确认了 4 个逻辑问题，外加 2 个本轮踩到的文档/工具链问题，一并登记。

### BUG-078: 引导第 3 步锚点与文案不符——锚在「创建任务」按钮上讲 Sprint [High]

- **严重程度**: High（引导把人指到错误的位置，比没有引导更糟）
- **发现版本**: v2.17.0（读代码 + 浏览器逐步确认）
- **文件**: `frontend/src/composables/useTour.ts`
- **现象**: 引导共 3 步。第 1 步锚 `#create-task-btn` 讲"创建任务"；第 2 步讲"看板列"（居中气泡 + 手动给第一列加 outline）；
  第 3 步的 `element` **仍然是 `#create-task-btn`**，文案却是"Sprint 迭代：创建 Sprint 来规划迭代周期，
  使用燃尽图跟踪团队进度"。用户看到一个高亮的"新建任务"按钮，被告诉这是 Sprint 功能。
- **根因**: 三步共用同一个选择器，写的时候省事复制粘贴；第 3 步本想讲侧边栏/Sprint 入口却没有对应的稳定选择器。
- **修复**: 步骤改为"锚点函数 + 找不到就跳过"，重写为 3 个主题共 17 步（欢迎 5 / 看板 6 / 协作 6），
  每步锚到真实的元素：`#create-task-btn`、`.board__columns`、`.issue-card-wrapper`、`.board__filters`、
  `.mimo-sidebar__menu`、`.cmd-trigger`、`.ai-chat-panel__trigger`，以及按文案定位的侧边栏菜单项（迭代/交付分析/知识库）。
- **验证**: `e2e/tour.mjs` 断言看板主题进度为 `1 / 6`，逐步标题与锚点一致（用例 1、6）

### BUG-079: 引导遮罩会锁死整站点击，退出路径不可靠（body 残留 driver-active）[High]

- **严重程度**: High（用户第一反应是"页面卡死了"，直接把新用户劝退）
- **发现版本**: v2.17.0（浏览器实测）
- **文件**: `frontend/src/composables/useTour.ts`
- **现象**: 引导自动弹出后，遮罩拦截全站点击——实测此时点看板卡片无效（`clickable=false`）。
  driver.js 给 `<body>` 加的 `driver-active` 是 `pointer-events: none` 的来源；
  只要销毁路径没走到（实例被覆盖、手动删 DOM、路由中途切换），这个类就留在 body 上，
  引导消失了但整站永远点不动。本项目的 E2E 用例里早就有"必须手工 `body.classList.remove('driver-active')`"的绕行代码，
  说明这个问题在实际使用中反复出现。
- **根因**: 只在 `onDestroyed` 里做收尾，没有兜底清理；也没有"已有实例先销毁"的保护。
- **修复**: 所有退出路径（完成 / 跳过 / Esc / 关闭 / 路由切换）统一走 `safeDestroy()`：
  销毁实例 → 移除 `.driver-overlay`、`.driver-popover`、`#driver-dummy-element` → 清 `driver-active-element` 等类
  → 清 body/html 上的 `driver-active`、`driver-fade`、`driver-simple` → 还原 `pointer-events`。
  路由 `watch` 里调用 `abortTourOnNavigate()`，切换页面不会把遮罩带到新页面。
- **验证**: `e2e/tour.mjs` 用例 2（跳过后 popover/overlay 均为 0、body 无 driver-active）、
  用例 4（跳过后卡片可点）、用例 7（Esc 退出后同样干净）

### BUG-080: 引导入口"看过一次就永久消失"，跳过也算完成 [Medium]

- **严重程度**: Medium（新用户想再看一遍时找不到入口）
- **发现版本**: v2.17.0
- **文件**: `frontend/src/composables/useTour.ts`、`components/layout/MainLayout.vue`
- **现象**: 账号菜单里的「功能引导」带 `v-if="!tourDone"`，而 `tourDone` 是 `onMounted` 时
  从 localStorage 读的一次性快照——引导一结束就再也不更新，条目状态和真实状态脱节；
  同时 `onDestroyed` 无条件 `markTourCompleted()`，**点「跳过」也被记成完成**，于是入口彻底消失，
  用户没有任何办法把引导调回来看。
- **根因**: 引导状态只有一个布尔值，且"跳过"与"完成"没有区分；入口存在性绑在这个布尔上。
- **修复**: 状态改成按主题记录的 `{ status: 'done'|'skipped', v, at }`（`mimo_tour_state`，带 `TOUR_VERSION`）；
  跳过只记 `skipped`，不影响下次；菜单入口改为常驻三个主题（快速上手 / 看板实操 / 协作与治理），
  点任意一个都强制重播（`force: true`）。旧的 `mimo_tour_completed` 标记做了自动迁移。
- **验证**: `e2e/tour.mjs` 用例 3（跳过后 `status=skipped`）、用例 5（入口常驻）、
  用例 6（能重播）、用例 9（走完最后一步才记 `done`）

### BUG-081: 引导可从任意页面触发，但内容写死看板 [Medium]

- **严重程度**: Medium（在工作台点「功能引导」，看到的是看板相关的三步文案和居中气泡）
- **发现版本**: v2.17.0
- **文件**: `frontend/src/composables/useTour.ts`
- **现象**: 自动触发只在 `/projects/:id/board` 生效，但同一套步骤也能从账号菜单手动调用；
  driver.js 对找不到的 `element` 会退化到一个屏幕中央的 0×0 dummy 元素，
  于是气泡悬空显示"创建任务 / 看板列 / Sprint 迭代"，与当前页面毫无关系。
- **根因**: 一套步骤不分页面，且没有"锚点不存在时怎么办"的策略。
- **修复**: 主题化 + 页面守卫：`board` 主题要求路径含 `/board`，`collab` 要求 `/projects/`，
  不满足时只提示"看板引导需要在项目看板页启动"，不启动也不再把这个主题记成已完成；
  步骤锚点解析不到就跳过该步，整套都解析不到则提示"当前页面没有可引导的内容"。
- **验证**: `e2e/tour.mjs` 用例 8（首页点「看板实操」→ popover=0 且提示需要在看板页启动）

### BUG-082: 文档把 SSE 流式对话写成 GET，实现是 POST [Low]

- **严重程度**: Low（照文档接第三方会直接 405）
- **发现版本**: v2.17.0
- **文件**: `CLAUDE.md`、`AGENTS.md`
- **现象**: 两处文档写 `SSE流式对话(GET /api/ai/chat/stream)` / `GET /api/ai/chat/stream (SSE)`，
  但 `AiController` 是 `@PostMapping("/chat/stream")`——早期为了绕开 URL 长度限制从 GET 改成了 POST，
  文档没跟上。按文档发 GET 会拿到 405。
- **修复**: 文档改为 `POST /api/ai/chat/stream`（body 传 `message` / `systemPrompt`），并说明为什么用 POST。

### BUG-083: e2e 运行说明与实际不符，跑一次就弄脏工作区 [Low]

- **严重程度**: Low（第一次照着 README 跑必然失败）
- **发现版本**: v2.17.0
- **文件**: `e2e/README.md`、`e2e/core-flows.mjs`
- **现象**: README 写 `npm i -g playwright` + `node e2e/core-flows.mjs`，但 ESM 的 `import 'playwright'`
  不会去全局目录找，实际报 `ERR_MODULE_NOT_FOUND`；另外脚本把 `shots-suite/`、`suite-report.md`、
  临时附件都写进**当前工作目录**（仓库根），跑一次 `git status` 就多出一堆未跟踪文件。
- **修复**: 支持 `PLAYWRIGHT_MODULE` 指向仓库外的 playwright 包；产物统一落到脚本同目录；
  新增 `e2e/.gitignore`；README 补上准确的运行命令与环境变量说明。

---

## v2.16.2 — Wiki 目录越权修复 + 请求参数校验 (2026-11-20)

> 触发原因：KNOWN-017 的"越权场景未验证"一直没做。补了一套接口级越权用例
> （`e2e/authz.mjs`，注册一个不在团队里的外人账号去打 23 个读接口），第一次跑就漏了一个。

### BUG-076: Wiki 目录树未校验项目成员，任何登录用户可列出他人知识库结构 [High]

- **严重程度**: High（跨租户信息泄漏，无需任何特殊权限）
- **发现版本**: v2.16.2（越权用例首跑）
- **文件**: `backend/mimo-app/src/main/java/com/mimo/service/WikiService.java`、`controller/WikiController.java`
- **现象**: `GET /api/wiki/tree?projectId=1` 用一个刚注册、不属于任何团队的外人账号调用，
  返回 200 与完整目录：页面标题、作者、更新时间、层级结构。
  同一批对照接口（页面详情 `/wiki/pages/{id}`、版本历史、附件列表）都正确返回 403「无权访问该项目知识库」。
- **根因**: `getTree(Long projectId)` 只调了 `requireProject(projectId)`（确认项目存在），
  漏掉了其它 Wiki 接口都在用的 `requireMember(projectId, userId)`；
  Controller 也没把 `Authentication` 里的 userId 传进来——签名里压根没有 userId，所以"忘了校验"在编译期看不出来。
- **修复**: `getTree(Long projectId, Long userId)` 内部改调 `requireMember`，Controller 传入 `getUserId(auth)`；
  成员访问路径实测仍为 200，没有把授权改成一刀切。
- **验证**: `e2e/authz.mjs` 25 条断言全 PASS（23 个越权目标全部 403、成员读目录 200、缺参数 400）；
  新增 `WikiServiceTest` 2 项回归（非成员被拦且不再查目录内容、项目不存在报 404）

### BUG-077: 缺少必需参数返回「服务器内部错误」，把调用方错误伪装成服务端崩溃 [Medium]

- **严重程度**: Medium（排障成本高：前端少传一个参数，看起来像后端挂了）
- **发现版本**: v2.16.2
- **文件**: `backend/mimo-app/src/main/java/com/mimo/common/GlobalExceptionHandler.java`
- **现象**: `GET /api/wiki/search?projectId=1`（漏了 `q`）返回
  `{"code":500,"message":"服务器内部错误"}`，HTTP 也是 200；`GET /api/issues/abc`（id 非数字）同样 500。
- **根因**: `GlobalExceptionHandler` 只处理了 `BusinessException` / `BindException` / `AccessDeniedException`，
  `MissingServletRequestParameterException`、`TypeMismatchException`、`HttpMessageNotReadableException`、
  `MethodArgumentNotValidException` 全部落到兜底的 `Exception` 分支 → 500。
- **修复**: 四类调用方错误逐一接管，统一返回 400 + 具体原因
  （`缺少必需参数: q` / `参数类型不正确: id` / `请求体格式不正确，需要合法的 JSON` / 字段校验消息）；
  路径变量转换失败时 `propertyName` 为 null 的坑用 `MethodArgumentTypeMismatchException.getName()` 单独处理。
- **验证**: `GlobalExceptionHandlerTest` 3 项；接口实测：
  缺 `q` → 400「缺少必需参数: q」，`/issues/abc` → 400「参数类型不正确: id」

---

## v2.16.1 — AI 降级链路修复（无 Key 场景）与前端解包修复 (2026-11-20)

> 触发原因：KNOWN-018「未配置 AI Key 时的用户提示未验证」一直没有实测。按"AI 不可用时
> 应该降级、应该给友好提示"的规格跑了一遍，发现降级路径不是降级，而是把错误当成结果。

### BUG-072: AI 不可用时 `/ai/polish` 把错误说明当润色结果返回 200 [High]

- **严重程度**: High（会把用户正文直接改坏）
- **发现版本**: v2.16.1（KNOWN-018 实测）
- **文件**: `backend/mimo-app/src/main/java/com/mimo/ai/AiProvider.java`、`DeepSeekProvider.java`、`AiService.java`、`ai/dto/AiChatResponse.java`、`controller/AiController.java`
- **现象**: 未设置 `DEEPSEEK_API_KEY` 时 `POST /api/ai/polish` 返回
  `{"code":200,"data":"AI 服务未配置，请设置环境变量 DEEPSEEK_API_KEY"}`。
  前端把 `data` 当润色结果展示，用户点「应用」后任务描述被整段替换成这句错误提示——正文丢失。
- **根因**: `AiProvider.chat()` 的契约是"返回字符串内容"，未配置 Key 和调用异常时把错误说明
  当成内容返回；`AiChatResponse` 又没有错误标记，`AiController.polish` 无从判断，
  只能 `Result.success(response.getContent())`。
- **修复**: 引入 `AiUnavailableException`（message 直接面向用户，细节只进日志）+
  `AiProvider.isConfigured()` + `AiChatResponse.error` 标记；
  provider 未配置/调用失败一律抛异常（流式则推送同一句说明后结束）；
  `AiService.chatWithQuota` 捕获后返回带 error 标记的响应；
  `polish` 检测到 `hasError()` 返回 `503`，前端只弹提示、不动正文。
- **验证**: 未配置 Key 下 `polish` → `{"code":503,"message":"AI 服务未配置…"}`；
  浏览器用例断言"点润色后描述框内容不变、无预览卡、有错误提示"（`e2e/ai-degrade.mjs` 3/3 PASS）

### BUG-073: 解析类接口把上游原文拼进 500 消息，且失败也扣配额 [Medium]

- **严重程度**: Medium（内部实现细节泄漏到用户提示；配额被白白消耗）
- **发现版本**: v2.16.1
- **文件**: `backend/mimo-app/src/main/java/com/mimo/ai/AiService.java`、`controller/AiController.java`、`common/ResultCode.java`
- **现象**: `POST /api/ai/suggest-tasks` 返回
  `{"code":500,"message":"AI 拆分失败: AI返回格式异常: AI 服务未配置，请设置环境变量 DEEPSEEK_API_KEY"}`
  ——前端弹出的 toast 直接暴露出"AI返回格式异常"这类实现细节。
  同时 `/api/ai/usage` 的 `used` 在失败后也会 +1。
- **根因**: `AiService.chatJson` 把模型原始返回的前 200 字符拼进异常消息；controller 的
  `catch (Exception e)` 又把 `e.getMessage()` 原样透出；`chatWithQuota` 先 `recordUsage` 再判断结果。
- **修复**: `chatJson` 原始内容只写日志（截断 300 字），异常消息改为
  「AI 返回的内容无法解析，请重试」；controller 区分 `AiUnavailableException`（503，友好文案）
  与其它异常（500，通用文案）；`chatWithQuota` / `chatStreamWithQuota` 加 `isConfigured()`
  前置判断，失败不计用量；`ResultCode` 新增 `SERVICE_UNAVAILABLE(503)`。
- **验证**: 同上三接口返回 503 + 中文说明；`used` 在多次失败后仍为 0

### BUG-074: 前端 `api/ai.ts` 解包多了一层，AI 功能整体失效 [High]

- **严重程度**: High（AI 相关功能实际全部不可用，但看起来"能点"）
- **发现版本**: v2.16.1（排查 BUG-072 时发现 AI 面板 Provider 一直显示 `Unknown`）
- **文件**: `frontend/src/api/ai.ts`
- **现象**: AI 面板 Provider 标签永远 `Unknown`、用量永远 `0/100`；润色返回空串
  （点「应用」等于清空描述）；优先级建议永远落回 `MEDIUM / 无法解析AI响应`；
  智能创建、Story 拆分拿不到结构化数据。后端接口日志显示 200 且内容正常。
- **根因**: axios 响应拦截器 `request.ts` 已经把 `Result` 本体返回
  （`return res`，`res` 即 `response.data`），业务数据就在 `res.data`；
  `api/ai.ts` 里却写 `res.data?.data`，多解一层永远拿到 `undefined`，
  于是全部走了 `|| 默认值` 兜底。同仓其它 api 模块（issue/rag/role…）都是正确的 `res.data`。
- **修复**: 6 处 `res.data?.data` → `res.data`（polish/priority/parse-issue/suggest-tasks/
  estimate-story-points/usage），并给润色加空值防御（空内容抛错，不写回表单）
- **验证**: `e2e/ai-degrade.mjs` 第 1 条断言 Provider 与提示条正常；`npm run build` 通过

### BUG-075: 任务详情「AI 润色」是死代码，按钮从来没渲染出来 [Medium]

- **严重程度**: Medium（功能宣称有、界面没有）
- **发现版本**: v2.16.1
- **文件**: `frontend/src/components/issue/IssueDetailDialog.vue`
- **现象**: `handlePolish` / `applyPolish` / `polishedText` 三个成员都有定义，
  但模板里没有任何按钮引用它们，「AI 润色」实际不可达；同页的「AI分析」按钮有入口。
- **修复**: 在基本信息页签增加「AI 润色描述」按钮 + 结果预览卡（取消 / 应用到描述），
  三种工作项类型的描述都绑在 `form.description` 上，一处入口全部生效；
  描述为空时按钮禁用，避免无意义调用
- **验证**: 按钮在真实浏览器里渲染且 disabled 状态随描述内容变化；应用流程走预览卡二次确认

---

## v2.16.0 — 看板进行中自动计时 (2026-11-20)

### BUG-071: 停止计时不生效——MyBatis-Plus `updateById` 默认忽略 null 字段 [High]

- **严重程度**: High（功能看起来做了，实际"停表"永远失败）
- **发现版本**: v2.16.0（开发过程中由接口验证发现）
- **文件**: `backend/mimo-app/src/main/java/com/mimo/entity/Issue.java`
- **现象**: 把任务从「进行中」拖到「已完成」后，`in_progress_seconds` 确实累加了，但
  `in_progress_started_at` 仍是旧时间，看板上已完成的任务还在"继续计时"
- **根因**: 停止计时依赖把 `inProgressStartedAt` 置为 `null`，而 MyBatis-Plus 的 `updateById`
  默认策略是 `NOT_NULL`（忽略 null 字段），这条 UPDATE 里根本没有 `in_progress_started_at` 列。
  同类坑在 v2.13.8 Wiki 目录拖拽（`parentId=null` 移回根级）已经踩过一次
- **修复**: 字段上加 `@TableField(updateStrategy = FieldStrategy.IGNORED)`，强制把 null 写进 UPDATE
- **验证**: 接口复跑 `待办 → 进行中 → 已完成`：`startedAt` 由 `null` 变为时间戳再回到 `null`，
  `seconds` 从 0 累加到 6；UI 上已完成卡片只剩灰色累计时长
- **经验**: 凡是"取消/清空"类字段更新，都要显式确认 null 是否落库；必要时用
  `LambdaUpdateWrapper.set(field, null)` 或 `updateStrategy = IGNORED`，
  不能假设 `updateById` 会把 null 写进去

---

## v2.15.8 — 真实使用审计 + 信息架构 + 暗色模式 (2026-11-20)

> 触发原因：用户反馈"很多功能不知道怎么用、很多功能跑不通、暗色模式效果很差"。
> 用 Playwright 以真实用户身份跑了一遍（25 个页面逐页截图 + 抓 4xx/5xx + 7 条核心流程真填真提交），
> 以下缺陷全部有可复现证据（失败请求、错误文案、截图），不是推测。

> **本轮审计结论（完成度）**：25 个页面都能打开，但"能打开"不等于"能交付"。
>
> - 请求层：2 处断链直接暴露给用户（概览页右上角 404、Webhook 项目下拉 405）
> - 交互层：2 处首屏即阻断（引导遮罩拦截点击、暗色下弹窗全浅色），另有 4 处硬编码颜色
> - 信息架构：2 处结构性问题（项目概览无入口、侧边栏 13 项平铺）
> - 验证覆盖：7 条核心流程里，只有 Wiki 新建 / 报告生成 / 日历事件 / 公告发布 / 导出 PDF / 概览统计 / Webhook 下拉
>   这 7 个动作拿到了接口级通过证据；**建任务、评论、附件、拖拽、验收、变更审批、Sprint 启停均未闭环验证**（见 KNOWN-017）
> - 结论：roadmap 上"已完成"的功能数量与"被验证过能用"的功能数量差距明显，后续应以"可演示 + 可自动化验证"为完成标准，而不是接口存在即勾选

### BUG-057: 项目概览统计请求 `/api/api/...` 双前缀导致 404 [High]

- **严重程度**: High（主页面直接报错、统计卡空白）
- **发现版本**: v2.15.6
- **文件**: `frontend/src/views/ProjectOverview.vue`
- **现象**: 进入项目概览，右上角红色 toast `Not Found`；Network 里 `GET /api/api/reports/stats/1` → 404
- **根因**: axios 实例 `baseURL: '/api'`（`api/request.ts`），而此处又手写 `request.get('/api/reports/stats/' + id)`，两段 `/api` 叠加
- **修复**: 改为 `/reports/stats/{id}`；并全仓 grep 确认其余 `/api/` 字样只出现在 `SockJS('/api/ws')` 与 AI 的 `fetch('/api/ai/chat/stream')` 这类非 axios 调用里
- **验证**: 复跑后 `GET /api/reports/stats/1` → 200，页面无错误提示

### BUG-058: Webhook 页项目下拉调用不存在的 `GET /api/projects` 返回 405 [Medium]

- **严重程度**: Medium（功能半残：「生效范围=项目」永远选不到项目）
- **发现版本**: v2.13.5
- **文件**: `frontend/src/views/Webhooks.vue`
- **现象**: 新建/编辑 Webhook 时项目下拉为空；`GET /api/projects?size=200` → 405 Method Not Allowed
- **根因**: 后端只有 `/projects/my` 与 `/projects/team/{teamId}`，没有列表接口 `/projects`；catch 里又把异常静默吞掉，所以界面只表现为"空下拉"
- **修复**: 改用 `/projects/my`；保留空数组兜底但不再假装成功
- **验证**: `/projects/my` → 200，页面无 4xx

### BUG-059: 看板接口遇到 `column_id` 为空的 Issue 抛 NPE 整体 500 [High]

- **严重程度**: High（整个看板不可用）
- **发现版本**: v2.13.8（潜伏已久，导入数据/直接调 API 创建即可触发）
- **文件**: `backend/mimo-app/src/main/java/com/mimo/service/BoardService.java`
- **现象**: `GET /api/board/{projectId}` → 500；后端日志 `java.lang.NullPointerException: element cannot be mapped to a null key`，栈顶在 `BoardService.getBoard` 的 `Collectors.groupingBy`
- **根因**: `issues.stream().collect(Collectors.groupingBy(Issue::getColumnId))`；通过 Excel/CSV 导入、或直接 POST `/api/issues` 未指定 `columnId` 时该字段为 null，JDK 的 groupingBy 不接受 null key
- **修复**: 空列任务统一归入首列（项目无列时用哨兵 key 兜底）；列内按 `sortOrder` 排序时对 null 做 `nullsLast` 保护
- **测试**: 新增 `shouldPutIssuesWithoutColumnIntoFirstColumn`、`shouldReturnEmptyColumnsWhenProjectHasNoBoard`，后端单测 64 → 66 全绿

### BUG-060: 侧边栏引用不存在的 `Inbox` 图标 [Low]

- **严重程度**: Low（每次渲染产生 Vue 告警，菜单项缺图标）
- **发现版本**: v2.13.0
- **文件**: `frontend/src/components/layout/MainLayout.vue`
- **现象**: 控制台每次渲染 `[Vue warn]: Failed to resolve component: Inbox`，收件箱菜单左侧空白
- **根因**: `@element-plus/icons-vue` 共 294 个图标，没有 `Inbox`，模板却写了 `<Inbox />`
- **修复**: 改用 `Postcard`

### BUG-061: `el-tag` 传空字符串 `type` + 12 处模板类型不匹配 [Low]

- **严重程度**: Low（控制台告警刷屏，掩盖真实问题；引入 `components.d.ts` 后直接阻断构建）
- **发现版本**: v2.1 ~ v2.15.6
- **文件**: `ProjectList.vue`、`TeamDetail.vue`、`CalendarView.vue`、`ReportList.vue`、`BugCard.vue`、`BugTableView.vue`、`AiIssueCreator.vue`、`AcceptanceCriteria.vue`、`CommentSection.vue`、`EventForm.vue`、`RoleManage.vue`、`Wiki.vue`
- **现象**: 控制台大量 `Invalid prop: validation failed for prop "type". Expected one of [...] got value ""`；启用 unplugin 生成的 `components.d.ts` 后，`vue-tsc` 报 12 处 TS2322/TS2345/TS2367
- **根因**: 把空字符串当作"默认样式"传入；事件回调签名与 Element Plus 实际类型不符（el-checkbox 的 `CheckboxValueType`、el-radio-group 的 `string|number|boolean`、el-tree 的 `Node` + `DragEvent`）
- **修复**: 统一改 `primary` 并给映射函数补精确返回类型；事件签名按 EP 类型修正（`v === true` 归一化、`dropType` 收敛为字面量联合、`onKeydown` 接收 `Event | KeyboardEvent`）

### BUG-062: 首次进入看板时引导遮罩拦截点击且没有「跳过」[High]

- **严重程度**: High（新用户第一屏就"点不动"，直接判定产品卡死）
- **发现版本**: v2.2
- **文件**: `frontend/src/composables/useTour.ts`
- **现象**: 首次进看板弹出 driver.js 引导（1 of 3），遮罩层拦截全页点击，popover 上只有「上一步 / 下一步 / 完成 / ×」；点击看板任何按钮都无响应
- **根因**: driver.js 的 overlay 默认拦截指针事件；配置里没有提供 skip 入口，且「完成」要连点 3 步才出现
- **修复**: `onPopoverRender` 注入「跳过引导」按钮，点击即 `d.destroy()` 并写入已完成标记
- **验证**: 断言"跳过引导"可见，点击后 `.driver-overlay` 数量为 0

### BUG-063: 暗色模式从未启用 Element Plus 暗色主题 [High]

- **严重程度**: High（暗色模式下弹窗/表格/输入框全是浅色，整体割裂）
- **发现版本**: v2.1
- **文件**: `frontend/src/main.ts`、`frontend/src/store/app.ts`
- **现象**: 切到暗色后，`el-dialog` / `el-table` / `el-input` / `el-calendar` 仍是白色；顶部 header 也是浅灰
- **根因**: 只设置了 `<html data-theme="dark">`（仅驱动自定义 CSS 变量），而 Element Plus 的暗色主题要求 `<html class="dark">` + 引入 `element-plus/theme-chalk/dark/css-vars.css`
- **修复**: `main.ts` 引入 dark css-vars；`applyTheme()` 同时 `classList.toggle('dark', dark)`
- **验证**: `document.documentElement.classList.contains('dark')` → true，表格/输入框背景变为 `rgb(20,20,20)`

### BUG-064: 日历页暗色下整页白底，且 Sass 颜色运算编译失败 [High]

- **严重程度**: High（暗色下该页几乎全白；修一半还会整页变成红色报错层）
- **发现版本**: v2.8.0
- **文件**: `frontend/src/views/CalendarView.vue`
- **现象**: ① 暗色下日历主体仍为白底黑字；② 把 SCSS 变量改成 CSS 变量后，Vite 直接报 `[sass] $color: var(--bg-hover) is not a color`，页面只剩错误覆盖层
- **根因**: ① 文件顶部 `$primary:#409eff;$border:#e4e7ed;$text:#303133;$bg:#f5f7fa;$card:#fff` 全是硬编码浅色；② 有两处 `darken($bg,3%)` 对颜色做运算，CSS 变量无法参与 Sass 运算
- **修复**: 除 `$primary`（有 `rgba($primary,.x)` 运算）外，其余改为 `var(--text-primary)` 等 CSS 变量；两处 `darken($bg,3%)` 换成 `filter: brightness(1.12)`；`month-cell` / `today` / `other` 在 `global.css` 补暗色覆盖（实际类名是 `month-cell`，不是 `cal-cell`）
- **验证**: 暗色日历白格计数 13 → 0

### BUG-065: 首页问候语硬编码深色，暗色下几乎不可见 [Medium]

- **严重程度**: Medium（首页第一行文字看不清）
- **发现版本**: v2.1
- **文件**: `frontend/src/views/Dashboard.vue`
- **现象**: 暗色下「早上好，admin」呈深蓝色，和黑底几乎融在一起
- **根因**: `<ShinyText color="#0f172a" shine-color="#7c3aed" />` 写死了浅色主题的配色
- **修复**: 传 `var(--text-primary)` / `var(--color-accent)`，由主题变量决定

### BUG-066: 任务弹窗「甘特图排期」区块硬编码淡紫渐变 [Low]

- **严重程度**: Low（暗色下弹窗底部一块刺眼亮斑）
- **发现版本**: v2.9.3
- **文件**: `frontend/src/components/issue/IssueDetailDialog.vue`
- **现象**: 暗色新建/详情弹窗底部出现浅紫卡片
- **根因**: `background: linear-gradient(135deg, #f5f3ff 0%, #eef2ff 100%)` + `border: 1px solid #e0e7ff` 写死
- **修复**: 改用 `--color-primary-lighter`，并在暗色块里把它定义为半透明紫 `rgba(99,102,241,.16)`

### BUG-067: 暗色调色板过黑且 `--text-muted` 未定义 [Medium]

- **严重程度**: Medium（层次不可辨、部分文字颜色静默失效）
- **发现版本**: v2.1
- **文件**: `frontend/src/styles/variables.css`
- **现象**: 页面 `#030304` 配卡片 `#111114`，卡片边界看不出来；Dashboard 多处 `color: var(--text-muted)` 因变量未定义而回退成继承色
- **根因**: 暗色 token 取值过暗；`--text-muted` 只被引用从未定义
- **修复**: 重做暗色盘（页 `#0b0d13` / 卡 `#151822` / 列 `#10131b` / 悬停 `#1e2230`，边框与次要文字提亮）；补 `--text-muted`（亮色 `#94a3b8`、暗色 `#7b8798`）

### BUG-068: 项目概览页没有任何入口 [High]

- **严重程度**: High（信息架构缺陷：项目主页要先猜路径才能到达）
- **发现版本**: v2.1
- **文件**: `frontend/src/views/ProjectList.vue`、`frontend/src/views/ProjectOverview.vue`
- **现象**: 项目列表卡片整体 `@click` 直接跳 `/projects/{id}/board`；`/projects/{id}`（概览）只能从看板左上角返回
- **根因**: 卡片点击目标写成 board；概览页的跳转按钮集中堆在 hero 区（12 个）
- **修复**: 卡片点击进概览；概览 hero 只保留「新建工作项/新建日程/导入/导出」这类动作，交付分析/甘特图/工作流/知识库收进项目内导航

### BUG-069: 侧边栏 13 项平铺 + 项目内入口分散 [Medium]

- **严重程度**: Medium（"工具堆砌感"，用户不知道从哪开始）
- **发现版本**: v2.1
- **文件**: `frontend/src/components/layout/MainLayout.vue`
- **现象**: 一级菜单 13 个平级入口（工作台/项目管理/团队管理/个人信息/日历/公告/邮箱/等级/角色/审批/审计/Webhook/邮件日志）；项目内功能又分散在 hero 按钮、页面 tab、看板工具栏三处
- **根因**: 功能按"新增顺序"堆到菜单，没有信息架构分层
- **修复**: 三层导航（我的工作 / 项目 / 团队 / 设置）+ 项目内固定导航（概览/看板/迭代/时间线/报表/知识库/交付分析/工作流/故事地图），管理类功能下沉到「设置」，个人设置收进头像菜单

### BUG-070: 前端产物体积过大（入口 1.2 MB / ECharts 全量 / html2pdf 全量）[Medium]

- **严重程度**: Medium（首屏白屏时间长，弱网明显）
- **发现版本**: v2.1
- **文件**: `frontend/src/main.ts`、`vite.config.ts`、6 个图表组件、`ReportList.vue`
- **现象**: 生产构建入口 chunk 1227.8 kB、ECharts 共享块 1011.3 kB、ReportList 971.3 kB
- **根因**: Element Plus 全量 `app.use` + 全部图标注册；`import * as echarts from 'echarts'` 把全量图表类型打进包；html2pdf 静态引入
- **修复**: Element Plus/ECharts 改按需，html2pdf 改动态 import
- **验证**: 入口 1227.8 → 414.9 kB（−66%）、ECharts 1011.3 → 617.0 kB（−39%）、ReportList 971.3 → 11.0 kB（−99%）、JS 总量 −17%

---

## v2.9.0 — AI 助手流式对话稳定化 (2026-06-17)

### BUG-049: AI 助手 SSE 端点返回 406 Not Acceptable [High]

- **严重程度**: High（功能不可用）
- **发现版本**: v2.8.0
- **文件**: `backend/src/main/java/com/mimo/controller/AiController.java`
- **现象**: AI 助手发送消息时返回 `HttpMediaTypeNotAcceptableException: Could not find acceptable representation`
- **根因**: Spring MVC 的 content negotiation 在匹配 `text/event-stream` 时未找到对应的 `HttpMessageConverter`，加上 `SseEmitter` 内部用 `ResponseBodyEmitter` 返回，触发了 converter 链的查找
- **修复**: 放弃 `SseEmitter` / `StreamingResponseBody`，改用 `javax.servlet.AsyncContext` 原生异步 API + `void` 返回 + 在主线程 `flushBuffer()` 立即提交响应头，完全绕开 Spring MVC 的 content negotiation 和 message converter 链

### BUG-050: StreamingResponseBody 触发 HttpMessageNotWritableException [High]

- **严重程度**: High
- **发现版本**: v2.8.0
- **文件**: `backend/src/main/java/com/mimo/controller/AiController.java`
- **现象**: 后端日志 `No converter for [class java.util.LinkedHashMap] with preset Content-Type 'application/javascript;charset=UTF-8'`，导致部分 AI 请求无响应
- **根因**: `StreamingResponseBody` 在异步分发时 Spring MVC 仍尝试用 `LinkedHashMap` 作为返回值，错误地把 Content-Type 推断为 `application/javascript`（来自 fetch POST 默认 Accept 头）
- **修复**: 同 BUG-049，使用 `AsyncContext` 直接写响应，**不返回任何对象**给 Spring MVC 处理

### BUG-051: AI 助手长消息 URL 过长触发 ERR_ABORTED [High]

- **严重程度**: High
- **发现版本**: v2.8.0
- **文件**: `frontend/src/api/ai.ts`, `backend/.../AiController.java`
- **现象**: 用户输入超长消息（如 `495000000....` 一长串数字）时，浏览器报 `net::ERR_ABORTED`，后端未收到任何请求
- **根因**: `chatStream` 最初用 GET 请求 + `URLSearchParams` 将 message 拼接到 URL 中，超长消息导致 URL 超过浏览器/Tomcat 的 URL 长度限制（~8KB），请求被提前终止
- **修复**:
  - 前端 `fetch` 改用 `POST` + `body: JSON.stringify({ message, systemPrompt })`
  - 后端 `@PostMapping("/chat/stream")` + `@RequestBody Map<String, String>` 接收
  - 不再受 URL 长度限制

### BUG-052: AI 助手连续发送触发旧请求 ERR_ABORTED [Low]

- **严重程度**: Low（噪音）
- **发现版本**: v2.8.0
- **文件**: `frontend/src/components/common/AiChatPanel.vue`
- **现象**: 用户连续发送多条消息时，控制台出现 `net::ERR_ABORTED http://localhost:8080/api/ai/chat/stream`
- **根因**: `handleSend()` 没有先 abort 上一个未完成的 fetch，新的 controller 覆盖了全局 `abortController`，旧请求被浏览器以 aborted 方式终止
- **修复**: `handleSend()` 开头先 `abortController?.abort()` 终止旧请求，避免控制台噪音
- **已知问题**: AI 助手暂未接入项目上下文（`systemPrompt` 固定），用户需通过 `systemPrompt` 参数手动注入项目信息；后续计划支持自动从 session 加载项目上下文

### BUG-053: 服务器部署 V7 数据库迁移未执行导致日历 500 [High]

- **严重程度**: High（功能不可用）
- **发现版本**: v2.9.0
- **文件**: 服务器 `mimo` 数据库、`backend/src/main/resources/db/migration/V7__calendar_events_team_id.sql`
- **现象**: 服务器上点击日历菜单，浏览器 console 报 `GET /api/calendar?... 500`，后端日志 `17:38:33 ERROR` 触发 `JwtAuthenticationFilter.extractToken` 抛 `StrictHttpFirewall.validateAllowedHeaderValue` (因为后续 SQL 异常被异常处理链冒泡到过滤器链)
- **根因**:
  1. `docker-compose.yml` 用 `mysql:/docker-entrypoint-initdb.d/init.sql` 挂载初始化脚本，**仅在 MySQL 数据卷首次创建时执行一次**
  2. 项目未启用 Flyway，新加的 `V6__calendar_events.sql` / `V7__calendar_events_team_id.sql` **不会自动跑**
  3. 服务器上 `calendar_events` 表已存在（V6 手工合并过）但**缺 `team_id` 列**（V7 没跑）
  4. `CalendarEventService.selectByUserAndDateRange` 在 SQL 中 select `team_id` → MySQL 报 `Unknown column 'team_id'` → 异常冒泡成 500
- **修复**:
  1. **服务器端立即执行**: `mysql -u root -p336699 mimo -e "ALTER TABLE calendar_events ADD COLUMN team_id BIGINT NULL AFTER user_id; CREATE INDEX idx_calendar_events_team_id ON calendar_events(team_id);"`
  2. **代码侧改进（待办）**: 启用 Flyway 自动化迁移 / 把 V6+V7 合并到 `init.sql` 并挂卷
- **教训**: Docker 持久卷 + init.sql 是"一次性"模式，后续每次 schema 变更必须有自动化迁移工具接管

### BUG-054: 前端 ai.ts 写死 localhost:8080 导致生产环境 AI 助手 ERR_CONNECTION_REFUSED [High]

- **严重程度**: High（生产 AI 助手完全不可用）
- **发现版本**: v2.8.0（v2.9.0 暴露）
- **文件**: `frontend/src/api/ai.ts:85`
- **现象**: 服务器部署后浏览器 console 报 `net::ERR_CONNECTION_REFUSED http://localhost:8080/api/ai/chat/stream`
- **根因**: `chatStream()` 用原生 `fetch` 调用 SSE 端点，URL 写死 `http://localhost:8080/api/ai/chat/stream`。开发环境 Vite 代理会重写，但生产环境直接读这个 URL → 浏览器把 `localhost` 当作本机 127.0.0.1 → 拒绝连接
- **修复**: 改为相对路径 `/api/ai/chat/stream`，依赖 Vite (dev) / nginx (prod) 反代

### BUG-055: nginx worker 进程无法读 dist/assets 目录导致 JS MIME 错 [High]

- **严重程度**: High（前端完全无法加载）
- **发现版本**: v2.9.0
- **文件**: 服务器 `/root/Mimo/frontend/dist/assets/`
- **现象**: 浏览器 console `Failed to load module script: Expected a JavaScript-or-Wasm module script but the server responded with a MIME type of "text/html"`
- **根因**: scp 部署后 `/root/Mimo/frontend/dist/assets/` 权限为 `drwx------` (700)——只允许 root 进入。nginx worker 进程以 `nginx` 用户运行，**没有 `o+x` 权限**进入此目录，`try_files $uri $uri/ /index.html` 末尾的 fallback 把 `/assets/index-XXX.js` 路径转给 `/index.html`（823 字节 + text/html），浏览器拒绝执行
- **修复**: `chmod -R o+rx /root/Mimo/frontend/dist`（或 `chown -R nginx:nginx /root/Mimo/frontend/dist`）
- **教训**: scp 上传时保留 `umask 022` 的默认权限较安全；建议部署脚本加 `chmod -R o+rX` 后置步骤

### BUG-056: 生产环境 application.yml 缺 deepseek.api-key 段 [Medium]

- **严重程度**: Medium（功能降级，AI 助手不可用但其他正常）
- **发现版本**: v2.9.0
- **文件**: 服务器 `/root/Mimo/backend/src/main/resources/application.yml`
- **现象**: AI 助手返回 "AI 服务未配置，请设置环境变量 DEEPSEEK_API_KEY"
- **根因**: 部署时 `application.yml` 没包含 v2.8.0 加入的 `deepseek.api-key` 配置段。后端 DeepSeekProvider 用 `@Value("${deepseek.api-key:}")` 读默认空字符串
- **修复**: 两种方式任选
  1. **推荐**: 在 `/etc/systemd/system/mimo-backend.service` 的 `[Service]` 段加 `Environment="DEEPSEEK_API_KEY=sk-xxx"`，然后 `systemctl daemon-reload && systemctl restart mimo-backend`
  2. 编辑 `/root/Mimo/backend/src/main/resources/application.yml` 加：
     ```yaml
     deepseek:
       api-key: sk-xxx
     ```
     然后 `systemctl restart mimo-backend`
- **教训**: 生产环境**敏感配置（API key、密码）必须通过环境变量注入**，不应 hardcode 到 yml（已被 .gitignore 排除 / 应在部署时渲染）

---

## v2.8.0 — AI Copilot + 粒子动画 + 构建修复 (2026-06-16)

### BUG-043: ProjectBoard.vue 中文乱码导致构建失败 [Critical]

- **严重程度**: Critical（构建阻塞）
- **发现版本**: v2.7.3
- **文件**: `frontend/src/views/ProjectBoard.vue`
- **现象**: `vite build` 报错 `[vue/compiler-sfc] Unexpected token`，看板页加载失败 `TypeError: Failed to fetch dynamically imported module`
- **根因**: 文件中大量中文注释和标签文本出现乱码（如 `鉁?`、`鈹?` 等），导致 Vue 模板解析器和 SCSS 编译器无法正确解析；同时存在函数名拼写错误（`han]leBoardSync`）、标签未闭合、SCSS 括号不匹配等问题
- **修复**:
  - 使用 PowerShell 批量替换乱码文本为正确的中文内容
  - 修正函数名 `han]leBoardSync` → `handleBoardSync`
  - 修复模板中未闭合的标签和字符串
  - 调整 SCSS 样式结构，确保括号匹配，整合孤立的 CSS 代码块

### BUG-044: AI 助手请求返回 404 [High]

- **严重程度**: High（功能不可用）
- **发现版本**: v2.7.3
- **文件**: `frontend/src/api/ai.ts`, `backend/.../AiController.java`, `backend/.../SecurityConfig.java`
- **现象**: AI 助手发送消息时返回 `AxiosError: Request failed with status code 404`，路径 `/api/api/ai/chat/stream`
- **根因**:
  1. 前端 `ai.ts` 中请求 URL 已包含 `/api` 前缀，但 axios baseURL 也配置了 `/api`，导致双重前缀
  2. 后端 `SecurityConfig` 未开放 AI 接口的匿名访问权限
- **修复**:
  - 移除 `ai.ts` 中多余的 `/api` 前缀，确保请求路径为 `/ai/chat/stream`
  - 在 `SecurityConfig` 中添加 `/api/ai/**` 到白名单

### BUG-045: chatStream 参数顺序导致 TypeScript 编译错误 [Medium]

- **严重程度**: Medium（构建阻塞）
- **发现版本**: v2.8.0-dev
- **文件**: `frontend/src/api/ai.ts:79`
- **现象**: `error TS1016: A required parameter cannot follow an optional parameter`
- **根因**: `chatStream(message, systemPrompt?, onChunk)` 中必选参数 `onChunk` 位于可选参数 `systemPrompt` 之后
- **修复**: 调整参数顺序为 `chatStream(message, onChunk, systemPrompt?, ...)`

### BUG-046: AiChatPanel 类型错误与重复定义 [Medium]

- **严重程度**: Medium（构建阻塞）
- **发现版本**: v2.8.0-dev
- **文件**: `frontend/src/components/common/AiChatPanel.vue:35,124,135`
- **现象**:
  1. `error TS2304: Cannot find name 'computed'` — 第 124 行使用 `computed()` 但未导入
  2. `error TS2551: Property 'providerName' does not exist on type 'AiUsageInfo'` — 应为 `provider`
  3. 存在重复的 `computed(() => ...)` 函数定义
- **修复**:
  - vue import 中添加 `computed`
  - `u.providerName` → `u.provider`
  - 删除重复的 computed 函数

### BUG-047: AuthLayout canvas 可能为 null [Medium]

- **严重程度**: Medium（构建阻塞）
- **发现版本**: v2.8.0-dev
- **文件**: `frontend/src/components/layout/AuthLayout.vue:205-206`
- **现象**: `error TS18047: 'canvas' is possibly 'null'`
- **根因**: `onResize()` 函数中使用 `canvas.width` 和 `canvas.height`，但 canvas 来自 `ref<HTMLCanvasElement | null>(null)`，TypeScript 无法排除 null 可能性
- **修复**: 在 `onResize()` 入口添加 `if (!canvas) return` 守卫

### BUG-048: color.ts PALETTE 变量未定义 [Medium]

- **严重程度**: Medium（构建阻塞）
- **发现版本**: v2.8.0-dev
- **文件**: `frontend/src/utils/color.ts:24`
- **现象**: `error TS2304: Cannot find name 'PALETTE'`
- **根因**: `avatarColor()` 函数引用了 `PALETTE` 数组，但该变量从未定义
- **修复**: 定义 PALETTE 为 6 组双色渐变色数组（靛蓝/粉红/青色/橙色/紫色/青蓝）

---

## v2.7.3 — 代码质量加固 (2026-06-15)

### BUG-035: N+1 查询风暴导致看板性能严重下降 [Critical]

- **严重程度**: Critical（性能）
- **发现版本**: v2.7.2
- **文件**: `IssueService.java`, `TeamService.java`, `ApprovalService.java`, `ReportService.java`
- **现象**: 看板页面加载 30 个任务卡片时触发约 180 次数据库查询，页面响应缓慢
- **根因**: `IssueService.toVO()` 对每条 issue 独立查询 column/user/labels/parent/children 共 6 次；`TeamService.listMembers()` 循环内逐条查用户名
- **修复**:
  - 新建 `BatchContext` 工具类，统一管理批量预加载
  - `toVO()` 改为先批量 `selectById` 预加载所有关联数据到 Map，再从 Map 取值
  - 看板 30 卡片：~180 次 → ~6 次数据库查询

### BUG-036: Controller 不安全的 Principal 类型强转 [High]

- **严重程度**: High（稳定性）
- **发现版本**: v2.7.2
- **涉及文件**: DashboardController, ProjectController, TeamController(10处), UserController(3处)
- **现象**: `(Long) auth.getPrincipal()` 在 principal 类型变化时抛 ClassCastException → 500
- **根因**: Spring Security 的 principal 可能是 String、UserDetails 或自定义类型，直接强转不安全
- **修复**: 统一使用 `getLongPrincipal()` 辅助方法，内部用 `instanceof Number` 安全类型判断 + 分步校验

### BUG-037: init.sql 缺少 3 张表导致新部署报错 [High]

- **严重程度**: High（部署）
- **发现版本**: v2.7.2
- **文件**: `backend/src/main/resources/db/init.sql`
- **现象**: 新环境部署时仅运行 init.sql 会缺表，聊天/审批/等级功能全部 500
- **根因**: `chat_messages`、`approval_requests`、`user_levels` 三张表仅在 Flyway migration 脚本中存在
- **修复**: 将 migration V4/V5 中的建表语句同步到 init.sql

### BUG-038: AiController 手写 JSON 解析器脆弱 [Medium]

- **严重程度**: Medium（健壮性）
- **发现版本**: v2.7.2
- **文件**: `AiController.java:96-104`
- **现象**: AI 返回含逗号/冒号/嵌套的 JSON 时解析失败，返回空 Map
- **根因**: 手写的 `parseSimpleJson()` 仅支持最简单的 key:value 格式
- **修复**: 删除手写 parser，改用 Jackson `ObjectMapper.readValue(json, new TypeReference<Map<String,String>>(){} )`

### BUG-039: NotificationService LIMIT 拼接 SQL 注入风险 [Medium]

- **严重程度**: Medium（安全）
- **发现版本**: v2.7.2
- **文件**: `NotificationService.java:31`
- **现象**: `.last("LIMIT " + limit)` 直接拼接参数到 SQL
- **修复**: 改用 MyBatis-Plus `Page<Notification>` 分页查询 API

### BUG-040: 多处硬编码参数无法灵活配置 [Low]

- **严重程度**: Low（可维护性）
- **涉及位置**:
  - Sprint 周期 14 天 / 快速创建任务数 5 → `SprintService.java`
  - 审批过期天数 7 天 → `ApprovalController.java`
  - 聊天撤回窗口 2 分钟 → `ChatService.java`
  - 看板列名中文硬编码 → `ProjectService.java`
- **修复**: 全部改为 `@Value("${xxx.yyy:默认值}")` 可在 application.yml 中覆盖

### BUG-041: 评论无删除入口 [Low]

- **严重程度**: Low（功能缺失）
- **发现版本**: v2.6.0+
- **文件**: `CommentSection.vue`
- **现象**: 后端有 `DELETE /api/comments/{id}` 端点但前端无删除按钮
- **修复**: 新增 `deleteComment` API 函数 + CommentSection 删除按钮（仅作者可见）+ ElMessageBox 确认弹窗

### BUG-042: 前端 v-html 存在 XSS 风险 [Medium]

- **严重程度**: Medium（安全）
- **发现版本**: v2.7.2
- **文件**: `TeamDetail.vue`(renderMention), `markdown.ts`(sanitize)
- **现象**: `renderMention()` 未转义 HTML 即插入 `<span>` 标签；`sanitize()` 未过滤 embed/javascript:/data: 协议
- **修复**: renderMention 先做 HTML 实体转义再高亮 @mention；sanitize 补充过滤 embed 标签和危险协议

---

## v2.7.2 — 安全加固 (2026-06-14)

### BUG-033: DeepSeek API Key 暴露在前端代码中 [Critical]

- **严重程度**: Critical（密钥泄露）
- **发现版本**: v2.7.2
- **文件**: `frontend/src/api/ai.ts`
- **现象**: DeepSeek API Key (`sk-e1b6b4b...`) 硬编码在前端 JS 中，任何人查看源码即可获取
- **根因**: 前端直接调用 DeepSeek 外部 API，Key 无法隐藏
- **修复**:
  - 新建后端 `AiController.java`，提供 `/api/ai/polish` 和 `/api/ai/priority` 代理端点
  - API Key 移至 `application.yml` 的 `deepseek.api-key` 配置项
  - 前端 `ai.ts` 删除硬编码 Key，改为调用后端代理
- **影响**: 修复前 API Key 可被滥用产生费用

### BUG-034: 13 个 Controller 端点缺少授权检查 [High]

- **严重程度**: High（越权操作）
- **发现版本**: v2.7.2
- **涉及 Controller**: IssueController, BoardController, SprintController, CommentController, AttachmentController, NotificationController, ChatController, UserLevelController, ApprovalController, ReportController, ProjectController
- **现象**: 任何已登录用户可操作其他团队/用户的数据（创建看板列、删除 Sprint、查看他人团队聊天等）
- **根因**: Controller 端点仅依赖 Spring Security 的"已认证"检查，缺少业务级权限校验
- **修复**:
  - `TeamService` 新增 `isTeamMember(teamId, userId)` 方法
  - 每个 Controller 添加 `getLongPrincipal(auth)` 安全类型转换
  - 写操作端点添加 `assertTeamMemberByProject/Sprint/Column` 校验
  - 删除操作校验资源归属（评论/附件/通知/报告）
  - 管理员端点添加 `assertAdmin()` 校验
- **具体端点修复**:
  - IssueController: acceptance-criteria CRUD → 校验项目团队成员
  - BoardController: createColumn/updateColumn/deleteColumn/sortColumns → 校验项目团队成员
  - SprintController: create/start/complete/quickStart/complete-migration/addIssueToSprint/snapshot → 校验项目团队成员
  - CommentController: 新增 delete 端点，仅评论作者可删除
  - AttachmentController: delete → 仅上传者可删除
  - NotificationController: markRead → 校验通知归属用户
  - ChatController: send/history → 校验团队成员身份
  - UserLevelController: listAll/setLevel → 校验系统 admin
  - ApprovalController: listAllPending/cleanupExpired → 校验系统 admin；listPendingByTeam → 校验团队管理员
  - ReportController: submit/getById → 校验报告归属用户
  - ProjectController: addMember → 校验团队管理员

---

## v2.7.2 — 审批通知反馈 + 自动降级容错 (2026-06-14)

### BUG-028: 团队详情页成员增删项目报错 500 [High]

- **严重程度**: High
- **发现版本**: v2.7.1
- **文件**: `frontend/src/views/TeamDetail.vue`, `frontend/src/views/ProjectList.vue`
- **现象**: 普通成员在团队详情页创建/删除项目时，页面显示"服务器错误"(500)，而非走审批流程
- **根因**: 前端 `handleCreateProject()` / `handleDeleteProject()` 虽然有 `isTeamAdmin` 判断，但后端返回 403/500 时 axios 拦截器直接弹通用错误，没有降级机制
- **修复**: 三层防御
  1. 非管理员直接调用 `createApproval()` 跳过后端 API
  2. 管理员调用 API，若后端返回 403/500 则自动降级为审批请求
  3. 审批请求本身失败时显示友好提示而非"服务器错误"

### BUG-029: 审批通过/拒绝后申请人无任何反馈 [High]

- **严重程度**: High（功能缺失）
- **发现版本**: v2.7.1
- **文件**: `backend/.../ApprovalService.java`, `frontend/.../MainLayout.vue`
- **现象**: 管理员审批通过或拒绝后，提交审批的成员完全不知道结果
- **根因**: 后端 `approve()` / `reject()` 方法只更新数据库状态，没有通知机制
- **修复**:
  - 后端: `ApprovalService` 新增 `sendApprovalNotification()` 方法，在 approve/reject 时自动写入 `notifications` 表
  - 新增通知类型: `APPROVAL_APPROVED`（绿色弹窗）、`APPROVAL_REJECTED`（橙色弹窗含原因）
  - 前端: `MainLayout.vue` 新增 `checkApprovalNotifications()`，页面加载时拉取未读审批通知并弹窗提示

### BUG-030: admin 员工等级管理页面人员不全 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.7.1
- **文件**: `backend/.../UserLevelService.java`
- **现象**: 系统管理员在"员工等级管理"页面只能看到有 `user_levels` 记录的用户，新注册用户不显示
- **根因**: `UserLevelService.listAll()` 只查了 `user_levels` 表，没有该表记录的用户被遗漏
- **修复**: 改为先查 `users` 表获取所有注册用户，再 left join `user_levels`；无等级记录的用户默认显示 L1

### BUG-031: admin 点击审批菜单显示"仅管理员可查看所有待审批请求" [High]

- **严重程度**: High
- **发现版本**: v2.7.1
- **文件**: `backend/.../ApprovalController.java`
- **现象**: 系统管理员(admin)点击左侧"审批管理"菜单，页面报错"仅管理员可查看所有待审批请求"
- **根因**: `ApprovalController.listAllPending()` 中 `(Long) auth.getPrincipal()` 强转可能因 principal 类型不匹配导致 userId 为 null，进而查询用户失败
- **修复**: 改用 `instanceof Number` 安全类型判断 + 分步校验（principal → userId → user → role），拆分为三个独立异常

### BUG-032: 铭牌(UserBadge) UI 尺寸偏小 [Low]

- **严重程度**: Low
- **发现版本**: v2.7.1
- **文件**: `frontend/src/components/common/UserBadge.vue`
- **现象**: 用户等级铭牌文字太小（medium=11px, small=10px），视觉上不协调
- **修复**: 调整为 medium=12px, small=11px, large=14px，增加 `line-height: 1.6`

---

## v2.7.1 — 审批权限强化 (2026-06-13)

### BUG-024: 前端审批 API 路径重复 /api 前缀 [High]

- **严重程度**: High
- **发现版本**: v2.7.0
- **文件**: `frontend/src/api/approval.ts`
- **现象**: 审批拒绝、撤回、清理超时三个操作调用 API 时返回 404
- **根因**: `rejectRequest`, `withdrawApproval`, `cleanupExpiredApprovals` 三个函数的 URL 写成了 `/api/api/approvals/...`（重复 `/api` 前缀）
- **修复**: 去除重复前缀，改为 `/api/approvals/...`

### BUG-025: 管理员审批列表始终为空 [High]

- **严重程度**: High
- **发现版本**: v2.7.0
- **文件**: `frontend/src/views/ApprovalList.vue`, `backend/.../ApprovalController.java`
- **现象**: 管理员进入"审批管理"页面，列表始终无数据
- **根因**: 前端调用 `getPendingApprovals(0)` 传入 teamId=0，后端按 teamId 查询永远返回空
- **修复**:
  - 后端新增 `GET /api/approvals/pending` 端点（admin-only，返回所有团队待审批请求）
  - 前端改为调用 `getAllPendingApprovals()`

### BUG-026: 非管理员可直接删除项目绕过审批 [Critical]

- **严重程度**: Critical（权限绕过）
- **发现版本**: v2.7.0
- **文件**: `backend/.../ProjectController.java`
- **现象**: 任何已登录用户直接调用 `DELETE /api/projects/{id}` 即可删除项目，无需审批
- **根因**: `ProjectController.create()` 和 `deleteProject()` 没有权限检查
- **修复**: 两个端点添加 `teamService.isTeamAdmin(teamId, userId)` 检查，非管理员返回 403

### BUG-027: ApprovalService 编译错误 [High]

- **严重程度**: High
- **发现版本**: v2.7.0
- **文件**: `backend/.../ApprovalService.java`
- **现象**: 部署时后端编译失败
- **根因**:
  1. `projectService.create(createReq)` 缺少第二个参数 `requesterId`
  2. 返回类型不匹配：`ProjectVO` 无法转换为 `Project`
- **修复**: 补上缺失参数，将接收类型改为 `ProjectVO`

---

## v2.7.0 — 团队管理员权限 + 审批系统 (2026-06-12)

### BUG-023: 项目列表只显示自己创建的项目 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.6.0
- **文件**: `backend/.../ProjectService.java`
- **现象**: 团队成员只能看到自己创建或被加入的项目，看不到团队其他成员创建的项目
- **根因**: `ProjectService.listByUser()` 通过 `project_members` 表关联查询，仅返回用户直接关联的项目
- **修复**: 改为查询用户所在团队的所有项目；创建项目时自动将团队成员加入 `project_members`

---

## v2.6.0 — 看板 Tab 切换 + 删除/移动修复 (2026-06-12)

### BUG-021: 删除任务报错 500 [Critical]

- **严重程度**: Critical
- **发现版本**: v2.5.0
- **文件**: `backend/.../IssueService.java`
- **现象**: 删除任务时后端返回 500 Internal Server Error
- **根因**: `IssueService.delete()` 没有清理 `notifications` 表中 `relatedType='ISSUE'` 的关联记录，外键约束导致删除失败
- **修复**: 完整级联删除顺序：comments → attachments → labels → notifications → 子任务 parentId 置空 → issue 本身，全部在同一事务中执行

### BUG-022: BUG 类型任务拖拽移动报错 500 [High]

- **严重程度**: High
- **发现版本**: v2.5.0
- **文件**: `backend/.../BoardService.java`
- **现象**: 将 BUG 类型任务拖到新列时，后端返回 500
- **根因**: `BoardService.moveIssue()` 直接设置 `bugStatus` 而不校验状态转换规则，非法转换导致数据库约束或业务逻辑异常
- **修复**: 新增 `isValidBugTransition()` 方法，包含完整状态转换矩阵验证；非法转换时只更新位置不更新状态，不抛异常

---

## v2.5.0 — 聊天撤回 + 单设备登录 + @提及 (2026-06-11)

### BUG-019: 无效 JWT 返回 403 而非 401 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.4.0
- **文件**: `backend/.../JwtAuthenticationFilter.java`
- **现象**: Token 过期或无效时，前端收到 403 Forbidden 而非 401 Unauthorized，导致用户看到"无权限"而非"请重新登录"
- **根因**: 无效 JWT 被传递到 Spring Security 授权阶段，返回 403 而非 401
- **修复**: `JwtAuthenticationFilter` 对无效/过期 Token 直接通过 response writer 返回 401 JSON

### BUG-020: 同一账号可多设备同时登录 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.4.0
- **文件**: `backend/.../UserService.java`, `backend/.../JwtAuthenticationFilter.java`
- **现象**: 同一账号可在多个浏览器/设备同时登录，无互踢机制
- **根因**: 后端无 Token 唯一性校验
- **修复**:
  - 登录时将新 Token 写入 Redis (`mimo:token:{userId}`, 24h TTL)
  - 每次请求校验请求头 Token 与 Redis 存储的是否一致，不一致返回 401 "账号已在其他设备登录"

---

## v2.3.1 — 看板拖拽与状态修复 (2026-06-10)

### BUG-016: 快速完成按钮不移动任务到完成列 [High]

- **严重程度**: High
- **发现版本**: v2.3.0
- **文件**: `frontend/src/views/ProjectBoard.vue`
- **现象**: 点击任务卡片上的"快速完成"按钮后，任务状态变为 DONE 但仍停留在原列
- **根因**: `updateIssue` 只发送了 `status='DONE'` 而没有发送 `columnId`，后端不会自动移动列
- **修复**: 快速完成时查找"已完成"列的 ID，同时发送 `status` + `columnId`；BUG 类型额外发送 `bugStatus='CLOSED'`

### BUG-017: 看板拖拽始终显示"移动失败" [Critical]

- **严重程度**: Critical
- **发现版本**: v2.3.0
- **文件**: `frontend/src/views/ProjectBoard.vue`
- **现象**: 在看板中拖拽任务卡片后，始终弹出"移动失败"提示
- **根因**: 使用筛选功能时，被隐藏的卡片使用 `display: none`，导致 sortablejs 的索引计算错误
- **修复**: 将隐藏方式从 `display: none` 改为 `visibility: hidden; height: 0; overflow: hidden`，保持元素在文档流中

### BUG-018: Linux 部署环境拖拽不工作 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.3.0
- **文件**: `frontend/src/views/ProjectBoard.vue`
- **现象**: 在 Linux 服务器部署后，看板拖拽功能完全不可用
- **根因**: vuedraggable 配置了 `force-fallback: true`（HTML5 DnD 降级模式），在某些 Linux 浏览器上不兼容
- **修复**: 移除 `force-fallback: true`，使用原生 HTML5 Drag and Drop API

---

## v2.0.0 — 工作项类型分化 (2026-06-08)

### BUG-011: 优先级选择器重复 [Low]

- **严重程度**: Low
- **发现版本**: v2.0.0-dev
- **文件**: 各类型任务弹窗组件
- **现象**: STORY/TASK/BUG 三个弹窗中各自定义了优先级下拉选择器，存在重复代码
- **修复**: 统一为一个共享的优先级选择器组件

### BUG-012: BUG 环境信息编辑时不回显 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.0.0-dev
- **文件**: `frontend/src/components/issue/bug/BugDialog.vue`
- **现象**: 编辑 BUG 任务时，环境信息（操作系统/浏览器/版本）字段为空
- **根因**: 编辑模式下未将 `environment` 字段拆分回三个输入框
- **修复**: 编辑时解析 `environment` 字段并回填到对应输入框

### BUG-013: 创建模式下验收标准不可用 [Medium]

- **严重程度**: Medium
- **发现版本**: v2.0.0-dev
- **文件**: `frontend/src/components/issue/story/AcceptanceCriteria.vue`
- **现象**: 创建 STORY 时验收标准区域被禁用，只能在编辑模式添加
- **修复**: 启用创建模式下的验收标准编辑（本地编辑，创建时一起提交）

### BUG-014: 数据库中存在垃圾 board_columns 记录 [Low]

- **严重程度**: Low
- **发现版本**: v2.0.0-dev
- **文件**: 数据库
- **现象**: `board_columns` 表中存在不属于任何项目的孤立记录
- **修复**: 手动清理垃圾数据；创建项目时确保看板列正确关联 projectId

### BUG-015: "新建工作项"按钮命名不统一 [Low]

- **严重程度**: Low
- **发现版本**: v2.0.0-dev
- **文件**: `ProjectBoard.vue`, `ProjectOverview.vue`
- **现象**: 不同页面的新建按钮文案不一致（"新建任务"/"新建工作项"/"创建"混用）
- **修复**: 统一为"新建工作项"

---

## v1.x — 基础平台 (2026-06-08 之前)

### BUG-001: 注册接口返回 404 [High]

- **严重程度**: High
- **发现版本**: v1.0.0-dev
- **文件**: `backend/.../AuthController.java`
- **现象**: 前端调用注册接口返回 404 Not Found
- **根因**: 前端发送的 Content-Type 不正确，或使用了 GET 而非 POST 方法
- **修复**: 确保注册接口使用 POST + `application/json`

### BUG-002: JWT Token 过期后页面白屏 [Medium]

- **严重程度**: Medium
- **发现版本**: v1.0.0-dev
- **文件**: `frontend/src/api/request.ts`
- **现象**: Token 过期后，API 返回 401 但前端未处理，页面卡在白屏
- **修复**: axios 响应拦截器中处理 401，清除 localStorage 并跳转到登录页

### BUG-003: WebSocket 连接未携带认证信息 [High]

- **严重程度**: High
- **发现版本**: v1.0.0-dev
- **文件**: `backend/.../WebSocketAuthInterceptor.java`
- **现象**: WebSocket 连接后无法接收个人通知，服务端报认证失败
- **根因**: STOMP CONNECT 帧未携带 `Authorization` 头
- **修复**: 前端在 STOMP connect 时传入 JWT Token

### BUG-004: 看板拖拽后其他用户看不到变化 [High]

- **严重程度**: High
- **发现版本**: v1.0.0-dev
- **文件**: `backend/.../BoardService.java`, `frontend/.../ProjectBoard.vue`
- **现象**: 用户 A 拖拽任务后，用户 B 需要刷新才能看到变化
- **根因**: 后端拖拽操作后未通过 WebSocket 广播
- **修复**: `BoardService.moveIssue()` 操作后通过 `WebSocketService` 向 `/topic/board/{projectId}` 广播 `ISSUE_MOVED` 事件

### BUG-005: 通知点击无法跳转到对应任务 [Medium]

- **严重程度**: Medium
- **发现版本**: v1.0.0-dev
- **文件**: `frontend/.../MainLayout.vue`
- **现象**: 点击通知铃铛中的任务通知，页面不跳转
- **根因**: 通知数据缺少 `projectId` 字段，无法构建正确的跳转 URL
- **修复**: 后端 `IssueVO` 新增 `projectId` 字段，前端根据 `projectId` + `issueId` 跳转到 `/projects/{projectId}/board?issue={id}`

### BUG-006: 团队成员列表不显示角色 [Low]

- **严重程度**: Low
- **发现版本**: v1.0.0-dev
- **文件**: `frontend/.../TeamDetail.vue`
- **现象**: 团队详情页的成员列表没有显示角色标签（管理员/成员）
- **修复**: 后端 `MemberVO` 返回 `role` 字段，前端渲染角色标签

### BUG-007: Sprint 燃尽图数据为空 [Medium]

- **严重程度**: Medium
- **发现版本**: v1.0.0-dev
- **文件**: `backend/.../SprintService.java`
- **现象**: 启动 Sprint 后燃尽图无数据
- **根因**: 启动 Sprint 时未生成首日快照
- **修复**: `startSprint()` 方法中自动调用 `generateSnapshot()` 生成首日快照

### BUG-008: 日报生成内容为空 [Medium]

- **严重程度**: Medium
- **发现版本**: v1.0.0-dev
- **文件**: `backend/.../ReportService.java`
- **现象**: 生成日报时内容为空
- **根因**: 查询条件有误，无法匹配当天完成的任务
- **修复**: 修正日期查询条件，使用 `LocalDate` 而非 `LocalDateTime` 比较

### BUG-009: 深色模式下部分组件样式异常 [Low]

- **严重程度**: Low
- **发现版本**: v1.0.0-dev
- **文件**: `frontend/src/styles/variables.css`
- **现象**: 切换深色模式后，部分卡片、按钮颜色未变化
- **根因**: 部分组件使用硬编码颜色值而非 CSS 变量
- **修复**: 将硬编码颜色替换为 CSS 变量（`var(--bg-primary)` 等）

### BUG-010: 附件上传后列表不刷新 [Low]

- **严重程度**: Low
- **发现版本**: v1.0.0-dev
- **文件**: `frontend/.../IssueDetailDialog.vue`
- **现象**: 上传附件后，附件列表未更新
- **修复**: 上传成功后重新调用 `listByIssue()` 刷新列表

---

## 已知未修复问题

### KNOWN-001: @PreAuthorize 角色前缀 Bug [Low] — 2026-11-23 复核：已失效

- **文件**: `backend/.../TeamController.java`
- **现象**: `@PreAuthorize("hasRole('ROLE_ADMIN')")` 实际检查的是 `ROLE_ROLE_ADMIN`，永远不会匹配
- **原因**: Spring Security 的 `hasRole()` 自动添加 `ROLE_` 前缀
- **建议修复**: 改用 `hasAuthority('ROLE_ADMIN')`
- **当前状态**: 未修复（该注解目前未被实际依赖，权限校验在 Service 层手动实现）
- **复核（2026-11-23）**: 全后端已搜不到任何 `@PreAuthorize`（0 命中），注解被整体移除，
  前缀陷阱不复存在；权限一律在 Service 层显式判断，本条作废。

### KNOWN-002: Redis 未实际使用 [Low]

- **文件**: `pom.xml`, `application.yml`
- **现象**: `spring-boot-starter-data-redis` 已引入，Redis 配置已写入，但仅用于单设备登录 Token 校验，WebSocket 仍使用内存代理
- **影响**: 多实例部署时 WebSocket 消息无法跨实例同步
- **建议**: 切换到 Redis-backed WebSocket message broker

### KNOWN-003: 上传目录配置缺失 [Low] — 2026-11-23 复核：已修复

- **文件**: `backend/.../AttachmentService.java`
- **现象**: `mimo.upload.dir` 属性未在 `application.yml` 中设置，使用默认值 `./uploads`
- **影响**: 部署时附件保存路径不可控
- **复核（2026-11-23）**: `application.yml:54-55` 已显式写成 `dir: ${MIMO_UPLOAD_DIR:./uploads}`，
  部署时用环境变量覆盖即可，本条关闭。

### KNOWN-004: Issue Key 生成存在并发冲突风险 [Medium] — 2026-11-23 已修复

- **文件**: `backend/.../IssueService.java`、新增 `backend/.../IssueKeyAllocator.java`
- **现象**: 多个用户同时创建任务时，`issueKey` 可能重复
- **根因**: `count + 1` 方式非原子操作，高并发下可能生成相同 Key
  （后来改过一版「取项目内最大值 + 1」，但两个并发事务仍可能读到同一个最大值，非原子问题依旧）
- **已修复（2026-11-23 / v2.18.0）**:
  - 抽出 `IssueKeyAllocator`，把「取号 + 插入」收成一个可重试单元：撞 `uk_issue_key` 就重新取号再插，
    最多 5 次；仍冲突才抛 `409 任务编号分配冲突，请稍后重试`，不再把 DuplicateKeyException 抛成 500
  - 能在同一事务内重试的依据：MySQL/InnoDB 的重复键错误只回滚当前语句，事务仍然可用
  - 用例：`IssueKeyAllocatorTest` 3 项（首次取号 / 撞号后重新取号成功 / 连续撞号给出可读错误）
- **仍未做**: 没上真正的数据库序列或分布式锁——单库单实例够用，多实例部署前需要换方案

### KNOWN-005: 删除团队后项目数据成为孤儿 [Medium] — 2026-11-23 已修复

- **文件**: `backend/.../TeamService.java`
- **现象**: 删除团队后，该团队下的项目及其 issues/board/sprints 不受影响但无归属团队
- **根因**: `deleteTeam()` 仅删除 `team_members` 和 `teams`，项目表标注"独立存在"
- **为什么必须修**: 项目列表按「用户所在团队」过滤（`ProjectService.listByUser`），
  失去团队的项目谁都看不到、也删不掉，只能靠手工改库
- **已修复（2026-11-23 / v2.18.0）**: 删除团队时先遍历该团队下所有项目，
  逐个走 `projectService.deleteProject()`（复用既有的级联删除：看板列 / 任务 / 项目成员 / 报告 / Sprint），
  再删成员关系和团队本身；整体在一个事务里，中途失败会整体回滚
- **用例**: `TeamServiceTest` 4 项（连带删项目 / 无项目不误删 / 非所有者拒绝 / 团队不存在）
- **取舍说明**: 选的是「级联删除」而不是「保留孤儿 + 管理视图」——团队删除本就是不可逆操作，
  留一堆没人能访问的数据只是延迟爆炸

### KNOWN-006: 审批通过后执行失败无补偿机制 [Medium] — 2026-11-23 已修复

- **文件**: `backend/.../ApprovalService.java`、新增 `ApprovalExecutionService.java`、`V24__approval_execution_tracking.sql`、`ApprovalController`
- **现象**: 审批通过后 `executeApprovedAction()` 若执行失败（如项目创建失败），审批状态已变更为 APPROVED，但操作未实际生效
- **根因**: 审批和执行在同一个事务中，但异常被 catch 后没有回滚，只 `e.printStackTrace()`
- **已修复（2026-11-23 / v2.18.0）**:
  - 审批结果与执行结果拆成两个状态：`approval_requests` 新增
    `exec_status` / `exec_error` / `exec_attempts` / `last_exec_at`
  - 执行挪进独立的 `ApprovalExecutionService`，用 `REQUIRES_NEW` 单独事务：
    执行失败只回滚执行自己（不会留下「项目建了一半」的脏数据），审批结果仍然有效
  - 失败原因落库（截断 500 字），日志用 SLF4J 记全栈，不再 printStackTrace
  - 新增补偿入口 `POST /api/approvals/{id}/retry-execution`（需管理员）：
    只允许「已通过且未执行成功」的审批重试，已成功的直接拒绝
  - 前端展示字段：`ApprovalRequestVO` 增加 `execStatus` / `execError` / `execAttempts` / `lastExecAt`
  - 用例：`ApprovalServiceTest` 6 项（失败留痕 / 成功标记 / 重试成功 / 重试仍失败 / 已成功拒绝重试 / 非管理员拒绝）
- **仍未做**: 没有接自动重试队列（失败需人工点重试）；执行成功但审批更新提交失败这种极端窗口仍未处理

### KNOWN-007: 数据库无外键约束 [Low]

- **文件**: `init.sql`
- **现象**: 所有表之间通过应用层代码维护关系，无 DB 级外键
- **影响**: 可能出现孤立的 `comments`、`attachments` 等记录
- **当前状态**: 设计选择，非必须修复

### KNOWN-008: Sprint 快速创建限制前 5 个任务硬编码 [Low] — 2026-11-23 复核：已修复

- **文件**: `backend/.../SprintService.java`
- **现象**: `createQuickSprint()` 中 `if (added >= 5) break` 硬编码了 5 个任务的限制
- **建议修复**: 改为可配置参数
- **复核（2026-11-23）**: 已改成配置项 `sprint.quick-create-tasks:5`（`SprintService.java:27-28` 声明，
  第 157 行 `if (added >= quickCreateTasks) break` 使用），调容量不用改代码。

### KNOWN-009: 团队/项目/Sprint/评论均无更新端点 [Medium] — 2026-11-23 已修复

- **文件**: `TeamController`、`ProjectController`、`SprintController`、`CommentController` 及对应 Service/DTO
- **现象**: 创建后可删除但无法编辑，缺少 PUT 更新端点
- **影响**: 用户无法修改团队名/描述、项目名/Key、Sprint 名称/日期、评论内容
- **已修复（2026-11-23 / v2.18.0）**，四个端点：

  | 端点 | 权限 | 可改字段 |
  |---|---|---|
  | `PUT /api/teams/{id}` | 团队管理员 | 名称（查重）、描述 |
  | `PUT /api/projects/{projectId}` | 团队管理员 | 名称、描述 |
  | `PUT /api/sprints/{id}` | 项目成员 | 名称、目标、起止日期（不可倒挂、已结束不可改） |
  | `PUT /api/comments/{id}` | 仅评论作者 | 内容 |

  - 项目 **Key 故意不可改**：任务编号 `issue_key` 的前缀就是项目 Key，改了会让所有历史编号失效
  - 评论不做「管理员代改」：评论是审计痕迹，只有作者本人能改
  - 团队改名会查重（沿用 `TEAM_NAME_EXISTS`），描述传空串表示清空、传 null 表示不改
  - 用例：`TeamServiceTest`（改名/查重场景）、`SprintServiceTest` 4 项、`CommentServiceTest` 3 项
- **仍未做**: 前端还没有接入这四个端点的编辑 UI（本轮只补后端能力），
  接口文档 `接口文档.md` 已补，前端改动建议单独一轮做（涉及团队/项目设置页表单）

### KNOWN-010: 默认看板列名硬编码中文 [Low] — 2026-11-23 复核：已修复

- **文件**: `backend/.../ProjectService.java`
- **现象**: 默认列名"待办/进行中/已完成"写死在代码中
- **影响**: 非中文部署时列名不便
- **复核（2026-11-23）**: 已改成配置项 `@Value("${board.default-columns:待办|#909399,进行中|#409EFF,已完成|#67C23A}")`
  （`ProjectService.java:23`），默认值仍是中文，但新建项目时可用配置覆盖。

### KNOWN-011: 聊天/评论使用 v-html 无 XSS 防护 [Medium] — 2026-11-23 已修复（实测后升级为 BUG-092）

- **文件**: `frontend/src/utils/markdown.ts`、`frontend/src/views/EmailInbox.vue` 及 6 处 `v-html` 调用点
- **现象**: 用户输入的聊天内容和评论经 `renderMarkdown()`/`renderMention()` 后直接通过 `v-html` 渲染
- **本次复核结论（2026-11-23）**:
  - `renderMention()` 早已先做 HTML 转义再高亮，这一条**不再是问题**（`TeamDetail.vue:780-789`）
  - 真正的问题在 `renderMarkdown()`：`sanitize()` 是正则黑名单，被 `<details ontoggle>` 直接绕过并执行，
    另两条（实体编码 `javascript:`、mXSS 留 `<style>`）也能穿；受影响面不止原记录的 2 处，实际 **8 处 `v-html`**
  - 详细取证（真实浏览器 + 哨兵变量）与修法见 **BUG-092**
- **已修复（2026-11-23 / v2.18.1）**: `sanitizeHtml()` 改用 DOMPurify 白名单，Markdown 与邮件正文共用；
  `e2e/xss-markdown.mjs` 10 条载荷全绿（修复前 6/10）
- **仍未做**: 后端不做 strip，导出件与第三方消费方需自行转义

### KNOWN-012: 离开团队使用 window.location.href 触发全页刷新 [Low] — 2026-11-23 复核：已修复

- **文件**: `frontend/src/views/TeamDetail.vue`
- **现象**: 移除成员自己离开团队后，使用 `window.location.href = '/teams'` 而非 Vue Router
- **影响**: 全页刷新丢失 Vue 状态
- **复核（2026-11-23）**: 全前端已无 `window.location.href`（0 命中），离开团队改走 router 跳转，本条关闭。

### KNOWN-013: 在线人数始终显示总成员数 [Low]

- **文件**: `frontend/src/views/TeamDetail.vue`
- **根因**: `onlineCount = computed(() => members.value.length)` 取的是成员总数
- **建议修复**: 通过 WebSocket 统计真实在线连接数
- **复核（2026-11-23）**: 仍在（`TeamDetail.vue:591`），但**文案已改成"N 位成员"**（第 166 行），
  不再对外宣称是"在线人数"，误导消除；真实在线统计仍未实现，故保持未关闭。

### KNOWN-014: 工作台快捷操作 5 个按钮 4 个指向同一路由 [Low] — 2026-11-23 已修复（v2.18.3 / BUG-095）

- **文件**: `frontend/src/views/Dashboard.vue`
- **现象**: "新建任务"/"看板视图"/"Sprint"/"报告" 都跳到 `/projects`
- **复核（2026-11-23）**: 仍在 —— `Dashboard.vue:404-410` 的 `quickActions` 里
  「项目管理 / 看板视图 / Sprint / 项目报告」四项 `route` 全是 `'/projects'`（只有团队管理是 `/teams`）。
- **建议修复**: 分别路由到看板创建弹窗、Sprint 管理、报告页
- **已修复（2026-11-23 / v2.18.3）**: `quickActions` 改为 `computed`，看板 / 迭代 / 报告三项带上
  「我的项目」第一个（接口按最近活跃返回）的 projectId；没有项目时退回 `/projects` 并用 title 说明
  "先创建或加入一个项目"。真实浏览器用例见 `e2e/chain-links.mjs` 用例 1。

### KNOWN-015: 审批"拒绝原因"输入框取消时行为不一致 [Low] — 2026-11-23 复核：已修复

- **文件**: `frontend/src/views/ApprovalList.vue`
- **现象**: `ElMessageBox.prompt` 的 cancel 回调中，Element Plus 返回 `'cancel'` 或 `'close'`，但只检查了 `'cancel'`
- **影响**: 某些情况下用户点 X 关闭后仍会执行 reject 请求
- **复核（2026-11-23）**: 已修复 —— `ApprovalList.vue:92-93` 现在 `.catch(() => null)` 之后紧跟
  `if (reason === null) return`，取消与关闭都不会再发 `rejectRequest`。

### KNOWN-016: 部署脚本 deploy.ps1 无确认步骤 [Low]

- **文件**: `deploy.ps1`
- **现象**: 执行即覆盖服务器生产环境，无确认提示
- **建议修复**: 加 `Read-Host "确认部署? (y/n)"` 确认步骤
- **复核（2026-11-23）**: 仍在 —— `deploy.ps1` 全文件无 `Read-Host` / 无二次确认，
  0/4～4/4 五步直接连跑（其中第 0 步会 `Stop-Process` 杀掉占用 jar 的 java 进程）。


### KNOWN-017: 核心业务链路缺少端到端验证 [High] — ✅ 已解决（v2.19.4）

- **文件**: `e2e/core-flows.mjs`、`frontend/src/views/ProjectBoard.vue`、`components/issue/IssueDetailDialog.vue`、`views/SprintView.vue`
- **现象**: 「新建任务 → 拖拽 → 评论 → 附件 → 提交验收」这条主链路，此前只验证到"弹窗能打开、接口存在"，没有任何一条端到端跑通并断言结果
- **已解决（2026-11-20）**: 新增 `e2e/core-flows.mjs`，用真实浏览器 + 接口断言覆盖 6 条链路，
  当前 **6/6 全绿**：创建任务、拖拽进入进行中（含自动计时启动）、发表评论、上传附件、提交验收、新建 Sprint；
  运行结果逐次写入 `e2e/last-run.md`，失败会截图。跑法与踩坑记录见 `e2e/README.md`
- **已补充（2026-11-20 / v2.16.2）**: 越权访问 403 已覆盖——`e2e/authz.mjs` 注册一个不在团队里的外人账号，打 23 个项目/团队/Sprint/任务/管理端读接口，全被 403 拦住，同时验证成员自己访问仍为 200；首跑即发现 Wiki 目录泄漏（BUG-076）
- **仍未覆盖**: 变更审批（申请 → 审批 → 自动执行）、Sprint 启动/完成与未完成任务迁移、Excel 导入 / PDF 导出、WebSocket 广播一致性
- **再补（2026-11-23 / v2.19.0）**: 新增 `e2e/main-chain.mjs` —— **一个全新账号只走界面**，从建团队
  一路走到看报告（11 个检查点，当前 11/11）。它和 `core-flows.mjs` 的区别是：不是"每个功能各验一次"，
  而是"一条链必须首尾相接"，链路中间任何一环断掉都会红。首跑 5/9，抓到了 BUG-099 和单人团队验收死锁
  （另有两条红是它自己没填必填项 / 引导遮罩没关好，属误报，已在校正里说明）。
  仍留在"未关闭"的原因：只覆盖主线一遍、没有多角色矩阵（前端其余页面、健壮性/异常分支仍无自动化），
  也还没接进 CI。
- **✅ v2.19.4 已解决**: 补上最后一环——**多角色验收矩阵** `e2e/review-matrix.mjs`（12 条，
  A–J 全部走真实接口 + 真实浏览器），把"谁能提交、谁不能验收自己、普通成员能不能批、
  团队管理员算不算管理员"这些按角色分的规则钉住；首跑就抓出 BUG-102（面板不判权限）。
  加上 v2.19.1 的 `workflow-guard.mjs`（4 条）与之前的 `main-chain`（11）、`next-step`（6）、
  `chain-links`（5），核心链路现在有 **5 套 38 条**断言，跑法与清单见 `e2e/README.md`。
  仍未覆盖：变更审批自动执行、Sprint 启动/完成与未完成任务迁移、Excel 导入/PDF 导出、
  WebSocket 广播一致性（这几条单独的 KNOWN 已各自记着；CI 见 KNOWN-024）。
- **风险**: 上述环节坏了仍不会被发现；后端只有 Service 层单测，前端其余页面无自动化
- **建议**: 按 `e2e/README.md` 的"还没覆盖"清单继续补，最终接进 CI

### KNOWN-024: e2e 用例没有接进 CI，只能本地手工跑 [Low]

- **文件**: `e2e/*.mjs`、仓库无 CI 配置
- **现象**: 5 套 38 条用例全靠人工 `node e2e/xxx.mjs` 触发；改完代码没人跑就等于没测，
  合代码时也没有"红了不许合"的闸门
- **风险**: 回归靠自觉。这一轮我自己就出现过"合并后忘了重跑"，靠补跑才补回证据
- **建议**: 接一个 GitHub Actions 作业：MySQL service + 起后端 + 起前端 + 依次跑 5 套用例，
  任一非 0 退出即失败；先做成"允许失败但留报告"，稳定后再改成必须绿

### KNOWN-018: 未配置 AI Key 时的用户提示未验证 [Medium] — 2026-11-20 已解决

- **文件**: `backend/.../ai/`、`frontend/src/components/common/AiChatPanel.vue`、`e2e/ai-degrade.mjs`
- **现象**（原记录）: 本地未设 `DEEPSEEK_API_KEY`，AI 面板/智能创建/Story 拆分/健康度解读的降级表现未知
- **实测结论**: 降级路径当时是坏的，直接命中 4 个 Bug（BUG-072~075）：润色把错误说明当结果写回正文、
  解析类接口把上游原文透出、失败也扣配额、前端解包多一层导致 AI 功能整体失效
- **已解决（2026-11-20 / v2.16.1）**:
  - 后端：`AiUnavailableException` + `isConfigured()` + `AiChatResponse.error` 标记；
    未配置/失败统一返回 503 + 面向用户的中文说明，失败不计当日用量
  - 前端：`api/ai.ts` 解包修正；AI 面板顶部提示条「后端未配置模型 Key，AI 功能暂不可用」；
    润色加空值防御
  - 用例：`e2e/ai-degrade.mjs` 3 条浏览器断言（提示条 / 对话友好说明 / 润色失败不改写正文），
    当前 3/3 PASS；后端新增 7 条单测（`AiServiceTest`），后端测试 71 → 78 全绿
- **仍未验证**: 配置了真实 Key 后的正常返回路径（需要有效的 DeepSeek Key），
  以及 RAG 问答/AI Sprint 规划在 Key 有效时的引用质量

### KNOWN-019: 空状态缺少「下一步」引导 [Medium] — ✅ 已解决（v2.19.2）

- **文件**: `views/Wiki.vue`、`views/ApprovalList.vue`、`views/Announcements.vue`、`views/TeamList.vue`、`views/ProjectList.vue`
- **现象**: 多数空状态只有"暂无数据"；只有甘特图（"为 Issue 设置计划起止后…"）和收件箱（"尚未绑定任何邮箱，请先去邮箱绑定"）给了可执行指引
- **风险**: 新用户面对空列表不知道点哪里，加剧"不知道怎么用"的观感
- **建议**: 统一空状态组件，强制包含一句可操作指引 + 一个主按钮
- **✅ v2.19.2 已解决**: 新增 `components/common/EmptyGuide.vue`（title / reason / action 三段式），
  公告页、邮箱绑定、Webhook、审批管理两个页签、工作台空库均已换用它；
  公告页对普通成员不显示"发布"按钮（见 v2.19.2 段）。
  局部小面板的空态（工作台"暂无动态"、Wiki 历史版本/差异、迭代 Backlog）仍保持原样，理由同段内说明。

### KNOWN-020: 缺少演示数据初始化入口 [Medium] — ✅ 已解决（v2.19.2）

- **文件**: `backend/.../db/init.sql`、前端无对应入口
- **现象**: 空库只有 3 用户 / 1 团队；工作台、Sprint 健康度、交付分析、RAG、审计日志等页面全为 0 或空，无法体验也无法演示
- **风险**: 验收/答辩时看到的是"一堆 0"，容易被判定为未完成
- **建议**: 提供「一键生成演示数据」脚本或 `/admin/demo-data` 接口（项目 + Sprint + 20 条 Issue + 评论 + 工时）
- **✅ v2.19.2 已解决**: `POST /api/admin/demo-data`（只给系统管理员、幂等、不碰已有数据），
  一次生成 项目 + 4 列 + 3 迭代 + 15 工作项 + 评论/工时/验收/报告/动态/通知；
  前端入口在工作台空库末尾和项目列表页头。见 v2.19.2 段落（含写死 `DEMO` 导致 500 的自测记录）。

### KNOWN-021: 前端仍有硬编码颜色残留 [Low]
- **文件**: `frontend/src/**/*.vue`、`styles/global.css`
- **现象**: 全仓仍有约 42 处 `rgba(255,255,255,…)`、10 处 `background: #fff`、若干 `#f8fafc/#f5f7fa/#f1f5f9`；本次只修了已经被截图暴露出来的部分
- **风险**: 换主题或新增页面时继续出现"某一块是白的"
- **建议**: 增加 stylelint 规则禁止颜色字面量（或至少警告），逐步替换为 CSS 变量

### KNOWN-022: `user_roles` 的唯一约束对 GLOBAL 角色失效，同一授权被重复写入 [Medium] — ✅ 已解决（v2.18.5）

- **文件**: `backend/mimo-app/src/main/resources/db/migration/V11__rbac.sql`（建表 + `INSERT IGNORE`）、
  `V12__fix_user_role_migration.sql`
- **现象（v2.18.2 冒烟时实测）**: 本地开发库 `user_roles` 共 **50 行**，而实际只有
  `admin→SUPER_ADMIN`、`zhangsan→DEVELOPER`、`lisi→DEVELOPER` 三条授权，每条重复 **10 次**。
- **根因**: 表上确有 `UNIQUE KEY uk_user_role_scope (user_id, role_id, scope_type, scope_id)`，
  但 GLOBAL 行的 `scope_id` 是 **NULL**，而 MySQL 的唯一索引把多个 NULL 视为互不相同 ——
  唯一约束对全局角色**完全不生效**，`INSERT IGNORE` 便每次都能插进去一条。
- **影响**: 每次重放迁移/重建演示库都会再叠一份；`PermissionMapper` 的 `JOIN user_roles` 结果集随之膨胀
  （`SELECT DISTINCT` 兜住了正确性，所以表现为"悄悄变慢 + 数据看着很脏"，不是权限算错）。
- **✅ v2.18.5 已解决（未动 schema，见 BUG-098）**:
  - 写入侧两条路径全部改成 NULL 安全判重：迁移脚本 `INSERT ... SELECT ... NOT EXISTS`，
    `RoleService.assignRole` 用 `isNull` / `selectCount`；
  - `V12` 增加可重复执行的重复行清理，历史脏数据一次性收掉（本地实测 89 行 → 13 行）；
  - 新增 `RoleMigrationScriptTest` 与两条断言 SQL 形状的 `RoleServiceTest` 用例守住回归。
  - **为什么仍不改 schema**: 给 `scope_id` 加 `NOT NULL DEFAULT 0` 才是根治，但要占一个新迁移编号；
    等你手上未推送的工作流/面板那批推上来、编号确定后再统一排，届时这两道判重仍然兼容 0 值。

### KNOWN-023: e2e 用例不清自己造的账号 / 团队 / 项目，开发库会累积残留 [Low]

- **文件**: `e2e/*.mjs`
- **现象（v2.19.2 清库时实测）**: dev 库里躺着 8 个 `probe-*` / `wiki-probe-*` / `chain*` 账号、
  一组「链路团队/链路项目」和 8 条 `E2E任务*`。issue 级数据大多删得干净，但**账号、团队、项目删不掉**——
  删项目在本系统里要走审批流程，脚本没有"删掉我刚建的东西"的通道。
- **风险**: 跑几十次 e2e 后，项目列表、团队列表全是 `链路团队741614` 这种名字，
  演示和截图都没法用；也会干扰"工作台该显示什么"这类断言。
- **现状**: 提供了可重复执行的清理脚本 `e2e/cleanup-residue.sql`（按前缀/名字定位，按依赖顺序删，
  只碰用例数据），跑一次即可收干净（本地实测：用户 15 → 3、项目 3 → 2、`E2E任务*` → 0）。
- **建议（未做）**: 给测试账号加"测试数据"标记 + 一个管理员专用的 `DELETE /api/admin/test-data`；
  或者 CI 里用独立数据库，跑完直接丢弃容器。当前按 Low 挂着。


---

## Bug 统计

| 严重程度 | 数量 |
|---------|------|
| Critical | 6 |
| High | 42 |
| Medium | 37 |
| Low | 18 |
| **已修复（BUG 条目）** | **103** |
| **已知未修复（KNOWN 条目）** | **7** |
| **总计** | **110** |

| 版本段 | BUG 条数 |
|--------|---------|
| v2.19.5 — 工作台就地验收 + 示例数据一键清除（BUG-103）(2026-11-24) | 1 |
| v2.19.4 — 验收面板按角色显示按钮（BUG-102）+ 验收权限矩阵用例 (2026-11-24) | 1 |
| v2.19.3 — 合并另一台机器的工作流自动生成（无新 BUG）(2026-11-24) | 0 |
| v2.19.2 — 空状态引导 + 演示数据入口（关闭 KNOWN-019/020，不新增 BUG）(2026-11-23) | 0 |
| v2.19.1 — 工作流配好看不见 / 挡住不说往哪走（BUG-101）(2026-11-23) | 1 |
| v2.19.0 — 主线自证（工作台看不到能验收的待验收 / Sprint 静默失败）(2026-11-23) | 2 |
| v2.18.5 — 角色授权重复堆积 + 首屏假结论 (2026-11-23) | 1 |
| v2.18.4 — 主链路「下一步」+ 菜单/守卫权限口径统一 (2026-11-23) | 1 |
| v2.18.3 — 功能链路闭合（断链/假入口/切面 NPE）(2026-11-23) | 4 |
| v2.18.1 — Markdown/邮件 XSS 修复（取证驱动）(2026-11-23) | 1 |
| 历史补录 — 2026-11-20 审计 | 8 |
| v2.17.0 — 新手引导重做 (2026-11-20) | 6 |
| v2.16.2 — Wiki 目录越权修复 + 请求参数校验 (2026-11-20) | 2 |
| v2.16.1 — AI 降级链路修复（无 Key 场景）与前端解包修复 (2026-11-20) | 4 |
| v2.16.0 — 看板进行中自动计时 (2026-11-20) | 1 |
| v2.15.8 — 真实使用审计 + 信息架构 + 暗色模式 (2026-11-20) | 14 |
| v2.9.0 — AI 助手流式对话稳定化 (2026-06-17) | 8 |
| v2.8.0 — AI Copilot + 粒子动画 + 构建修复 (2026-06-16) | 6 |
| v2.7.3 — 代码质量加固 (2026-06-15) | 8 |
| v2.7.2 — 安全加固 (2026-06-14) | 2 |
| v2.7.2 — 审批通知反馈 + 自动降级容错 (2026-06-14) | 5 |
| v2.7.1 — 审批权限强化 (2026-06-13) | 4 |
| v2.7.0 — 团队管理员权限 + 审批系统 (2026-06-12) | 1 |
| v2.6.0 — 看板 Tab 切换 + 删除/移动修复 (2026-06-12) | 2 |
| v2.5.0 — 聊天撤回 + 单设备登录 + @提及 (2026-06-11) | 2 |
| v2.3.1 — 看板拖拽与状态修复 (2026-06-10) | 3 |
| v2.0.0 — 工作项类型分化 (2026-06-08) | 5 |
| v1.x — 基础平台 (2026-06-08 之前) | 10 |

> 统计口径：直接按 `### BUG-0NN` / `### KNOWN-0NN` 标题解析，已知未修复 = KNOWN 条目里未标"已解决"的
> （只写「部分已解决」仍计为未关闭）。数字由 `tools/buglog_stats.py` 生成，改完条目后重跑即可校对，不再手写：
>
> ```bash
> python tools/buglog_stats.py --check --list-open   # --check 会拿文件里这张表对账，不一致退出码 1
> ```
>
> 这个脚本在 v2.19.4 之前其实**并不存在**（本行一直这么写，但文件没有），所以统计长期靠手算，
> 也就出了"017 部分解决被当成关闭"的账。现在脚本已补上，并且用"故意把总计改错"反向验过一次确实能报红。
>
> **2026-11-23 校对**：`tools/buglog_stats.py` 不在当前工作区，本次按同一口径手工重算，并修掉三处不一致 ——
> ① 上版 `总计 110` 是 KNOWN 未关闭还有 19 条时的旧值（v2.18.0 已降到 15，总计没跟着改），
> 现按 `总计 = BUG 条目 + KNOWN 未关闭` 修正为 100；
> ② 本轮复核把 6 条「其实早就修好、只是没标」的 KNOWN（001 / 003 / 008 / 010 / 012 / 015）标为已修复，
> 加上 KNOWN-011 收口，未关闭 15 → 8；
> ③ 严重程度四档合计仍等于 BUG 条目数（6 + 37 + 31 + 18 = 92）。
>
> **2026-11-23 补记**：v2.18.2 冒烟时新发现 KNOWN-022（`user_roles` 唯一约束对 GLOBAL 失效），
> KNOWN 未关闭 8 → 9、总计 100 → 101；本轮的技术债修复不产生 BUG 条目，故 BUG 数仍为 92。
>
> **2026-11-23 补记（v2.18.3）**：功能链路闭合这一轮新增 4 条 BUG（093 切面 NPE / 094 邮件死链 /
> 095 假入口 / 096 审批通知落地），并关闭 KNOWN-014（4 个入口同路由）。于是
> BUG 已修复 92 → 96（High 37→39、Medium 31→33，四档合计 6+39+33+18 = 96）、
> KNOWN 未关闭 9 → 8、总计 101 → 104。教程"等级≠权限"的说明与前端菜单权限口径修正属于
> 表述/口径修正，不单列条目。
>
> **2026-11-23 补记（v2.18.4）**：新增 BUG-097（菜单可见但守卫拒绝 / 权限码并发拉取），
> BUG 已修复 96 → 97（High 39 → 40，四档合计 6+40+33+18 = 97），总计 104 → 105。
> 本轮的 P1/P2/P3（下一步、接下来做什么、每页一句话）是功能增强，不记 BUG 条目。
>
> **2026-11-23 补记（v2.18.5）**：新增 BUG-098（每次启动重复插角色授权 + assign 幂等失效），
> 并关闭 KNOWN-022。于是 BUG 已修复 97 → 98（High 40 → 41，四档合计 6+41+33+18 = 98）、
> KNOWN 未关闭 8 → 7、总计维持 98 + 7 = 105。首屏"数据未到先下结论"的假状态记在
> BUG-098 同一版本段内，不单列条目（属于本轮新增功能的自身缺陷）。
>
> **2026-11-23 补记（v2.19.0）**：主线自证这一轮新增 BUG-099（能验收的人在待我处理里看不到，
> High）与 BUG-100（Sprint 点创建静默失败，Medium）。于是 BUG 已修复 98 → 100
> （High 41 → 42、Medium 33 → 34，四档合计 6+42+34+18 = 100）、KNOWN 未关闭仍为 7、
> 总计 105 → 107。单人团队"验收死锁"的提示条属于补齐缺失指引，不单列 BUG；
> KNOWN-017（核心链路缺 e2e）本轮补了 `e2e/main-chain.mjs` 但只覆盖主线一遍走通，
> 仍按"部分解决"计为未关闭。
>
> **2026-11-23 补记（v2.19.1）**：新增 BUG-101（工作流配好之后看不懂、被拦住不说往哪走，Medium）。
> 于是 BUG 已修复 100 → 101（Medium 34 → 35，四档合计 6+42+35+18 = 101）、
> KNOWN 未关闭仍为 7、总计 107 → 108。
> 另：BUG-100 的定级与描述在 v2.19.1 之前已自行更正过一次（原记"点创建没反应"是误判，
> 真实缺陷是 `validate()` 的 reject 没人接），更正过程写在该条目开头。
>
> **2026-11-23 补记（v2.19.2）**：本轮不产生新的 BUG 条目，把两条 Medium 的 KNOWN 关掉——
> KNOWN-019（空状态引导）与 KNOWN-020（演示数据入口）；同时新登记 KNOWN-023（e2e 用例不清
> 自己造的账号/团队/项目，开发库会累积残留，Low，已给出 `e2e/cleanup-residue.sql`）。
> 于是 KNOWN 未关闭 7 → 6（−2 已解决 +1 新登记）、总计按"BUG 条目 + KNOWN 未关闭"
> 口径从 108 → 107（BUG 数仍为 101，四档合计不变）。
> 演示数据生成器第一版写死项目 Key `DEMO` 触发过 `uk_project_key` 唯一索引冲突（500），
> 属开发期自测发现、未进主干即已修掉，按既往口径只在段落里记录并补单测，不单列 BUG 编号。
>
> **2026-11-24 补记（v2.19.3）**：合并另一台机器推上来的 4 个提交（工作流按看板列自动生成 +
> 认领审批自动推进、审批通知去重、个人动态 XSS），快进合并、无冲突，本轮**不产生 BUG 条目**，
> 账目不变（BUG 101 / KNOWN 6 / 总计 107）。同批改动在三个地方标了 v2.17.1、v2.18.1、
> 实际 v2.19.3 三个版本号，已统一到 v2.19.3 并说明以 `AGENTS.md` 与本文件为准。
>
> **2026-11-24 补记（v2.19.4）**：新增 BUG-102（验收面板不判权限，「通过/打回」对所有人显示，
> Medium）；关闭 KNOWN-017（核心链路缺 e2e，High），同时新登记 KNOWN-024（e2e 未接 CI，Low）。
> 于是 BUG 已修复 101 → 102（Medium 35 → 36，四档合计 6+42+36+18 = 102）、
> KNOWN 未关闭 6 → 7、总计 107 → 109。
> **账目更正**：上一版把"KNOWN-017 部分已解决"直接算成已关闭，导致 KNOWN 未关闭少记 1
> （写 6，按本文件一贯口径应为 7）、总计少记 1（写 107，应为 108）；本节按 017 计入未关闭重算，
> 再叠加 017 关闭与 024 登记，得 KNOWN 7 / 总计 109。判断是否关闭只看标题：写了「✅ 已解决 / 已修复 / 已失效」的算关闭，只写「部分已解决」或什么都没写的算未关闭（当前 7 条：002/007/013/016/021/023/024）。

>
> **2026-11-24 补记（v2.19.5）**：新增 BUG-103（清除示例数据后再生成撞唯一索引，Medium）。
> 于是 BUG 已修复 102 → 103（Medium 36 → 37，四档合计 6+42+37+18 = 103）、
> KNOWN 未关闭仍为 7、总计 109 → 110。
> 同轮的「工作台待我处理就地通过/打回」「示例数据一键清除」是功能补齐，不算 BUG；
> "首版清除漏掉三类孤儿数据"属本轮新功能自测发现，按既往口径写在该版本段里，不单列编号。