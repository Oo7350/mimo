# AGENTS.md

> **最新版本**：v2.22.4（2026-11-27）— 量级验证：把「性能」从推理变成数字。前几轮都在补「功能对不对」，这一轮问的是「人多了还站得住吗」，答案是**动手之前不知道**——开发库常年只有 3 个用户 / 20 个工作项，所有"分页了、批量查了"的结论都是读代码推的。做法：新增 `tools/seed-load.sql` 造 **100 用户 / 5 项目 / 500 工作项 / 5000 活动日志**（独立「压测团队」，一键清）→ `e2e/perf-probe.mjs` 量 18 个核心接口的 p50/p95 → 用 `Com_select` 差值数 SQL 条数、`jcmd Thread.print` 采样热点 → 改 → 复测。**四个真问题**：① **BUG-126**：全员任务流给每个成员单独判一次管理员，单人 6 条 SELECT，101 人 = **606 条 / 433ms**；改成批量判定（`AccessControlService#filterTeamAdmins`，条数与人数无关），并写了"批量版必须等于逐人版"的等价性用例 → **606→28 条，p95 540→125ms**。② **BUG-127**：项目统计卡片按成员 `selectById`、按迭代 `selectCount×2`，**40 条 SELECT 只画 2KB 的图**；该接口此前**一个测试都没有**，现在补了 `ReportServiceTest`（4 条，既守数字也守条数）→ **40→8 条**。③ **BUG-128**：近 7 天趋势把明细整批查进 JVM 再数（线程栈采样 6 次有 4 次停在这条查询上）；改成库里 `GROUP BY DATE(created_at)`，终态判定仍只在 `BoardColumnRules` 一处，等价性用真实数据验过（改前改后逐日逐字段完全一致）。④ **BUG-129**：在线状态逐人问 Redis（每人 2 次往返）。**顺手收口**：计划书路线图快照从 v2.19.5 更新到当前版本；新增 `tools/cleanup-soft-deleted.sql` 物理清理逻辑删除的项目（先自测：造一个 → 跑 → 校验，实际清掉 **3 个项目 / 31 工作项**）；双实例（跨实例在线 + 广播）写进 CI（新增 `two-instance` job：mysql + redis 两个 service、两个后端共用一个 Redis），并把 `two-instance-presence.mjs` 写死的 Windows 路径改成按脚本位置解析。**这个新 job 第一次在真实 runner 上跑就红了**（BUG-130）：步骤顺序写成"先灌种子再起后端"，而空库里一张表都没有（建表是后端启动时 `MigrationRunner` 干的）→ 1 秒内 `Table 'mimo.users' doesn't exist`；照主 job 的"启动 → 种子"重排后绿。紧接着又撞出 **BUG-131**：主 job 的「建表结果自检」红在**只建出 25 张表**，而前一步"启动后端"是绿的——因为 Spring Boot **先开 HTTP 端口、再跑 `ApplicationRunner`**，迁移跑到一半 `users` 表已存在、登录能过，于是"探活通过"并不等于"库建完"；之前的绿灯只是迁移够快凑出来的时序运气。现在就绪判定改成 `grep "Started MimoApplication"`（Runner 跑完才打）**且** 登录能过，自检也改成"最多等 60 秒、最后一次不达标才红"，失败时打印迁移日志与表清单。同一轮还发现 `perf-probe` 第一版"永远退出 0"（连登录失败都绿，等于焊了个装样子的闸门）→ 改成慢不报错、跑挂必报错。**最终 [run #14](https://github.com/Oo7350/mimo/actions/runs/45942184401) 两个 job 都绿**：`e2e` 8 分 45 秒（含造量级数据 / 性能探针 / 清数据三步）、`two-instance` 2 分 1 秒（跨实例在线 + 广播首次在真实 runner 上通过）。这三次推送（#12 红在顺序、#13 红在就绪判定、#14 双绿）刚好把"新写的闸门自己有没有毛病"验了一遍。**再补上索引这一层（BUG-132/133）**：把 606 条 SELECT 压到 28 条之后接口还剩 125ms，这层 `Com_select` 数不出来，得看 `EXPLAIN ANALYZE`——`activity_logs` 上三个索引全是单列，而查询是 `project_id IN (...) AND action = ? AND created_at >= ?`，一条都吃不住，优化器退回全表扫；新增 `V25__hot_query_indexes.sql` 建 `(project_id, action, created_at)`，并删掉与 `idx_created_at` 完全重复的 `idx_created`。4 万行日志实测：新建趋势 31.4→16.4ms、移动趋势 63.7→47.2ms，接口层 A/B（关掉/装回、缓存已热、12 轮）p95 **387→141ms**（第一次量到的 759→177ms 因缓冲池是冷的，没敢用）。同一轮还**量掉了两个看着该建的索引**（`issues (project_id, deleted)` 加了更慢 → 不建；`(sprint_id, status)` 0.9% 选择性下 6.47→0.33ms → 建），并发现 **BUG-133**：压测日志的 `detail` 写成 `压测活动 LOAD1-1 #123`，「移动到终态列」那条趋势查询在压测数据上一行都统计不到——量到的"几毫秒"是空转；改成真实文案，同时堵掉 `cleanup-load.sql` 里"按文案删"的 `OR`（放宽就会连演示项目和 e2e 的正常日志一起删）。后端 **234 项**测试。账本 **133 条 BUG 全修 + 0 条 KNOWN 未关闭**。
> **上一版本**：v2.22.3（2026-11-27）— 关闭 KNOWN-024：CI 在真实 runner 上第一次全绿。做法只是"人去点了一下 Run workflow"，结果全绿，再 push 一次又绿一次（#8 手动 8 分 36 秒 / #9 push 8 分 57 秒，35 个 step 全 success，其中「跑全部 e2e」5 分 41 秒 / 5 分 43 秒），两条触发路径都验过了。过程中纠正了一句**性质完全不同的记录**：KNOWN-024 一直写着"从没在真实 runner 上跑过"，查 Actions 历史才发现 v2.22.0 那次（run #4）**跑过、而且红了**——红在「启动后端」：等 180 秒也登不进去（正是 BUG-123/125 的样子），后面 4 个 step 全 skipped。也就是说闸门早就装上了，只是**没人读结果**，而记录把"验证失败"写成了"未验证"——前者是有反证据，后者只是没证据，混在一起写就会带着同一个错再推一遍。账本因此变成 **125 条 BUG 全修 + 0 条 KNOWN 未关闭**。
> **上一版本**：v2.22.2（2026-11-26）— 全新环境从零到绿（把 KNOWN-024 的风险压到只剩"Linux 环境差异"）：这一轮冲着"CI 从没跑过"去的，做法不是改 YAML 而是**把 CI 的步骤在本机原样演一遍**（独立空 MySQL 实例 + 空库 + 同一条命令），结果第一个 30 秒就红了，而且不是 YAML 的问题——**仓库根本建不出数据库**：① **BUG-123**：基础建表脚本 `resources/db/init.sql`（18 张表）**从来没被任何程序执行过**，`MigrationRunner` 只扫 `db/migration/V*.sql`，`init.sql` 唯一的引用是 `AGENTS.md` 里一条路径还写错的手工命令；于是空库启动后只有 23 张表、`users/teams/projects/issues` 一个都没有，任何接口都失败。修法：搬到 `db/migration/V0__init.sql`（排序最前、只有 DDL、`IF NOT EXISTS` 幂等），把测试账号与演示团队**拆出去**成 `db/seed-demo.sql`（需要显式执行——建表任何环境都该自动，"默认口令的账号"不该），`application.yml` 的 `url` 改成 `${MIMO_DB_URL:…}`（以前写死，CI 里那行环境变量是句废话）。② **BUG-124**：建表与空库引导都是 `ApplicationRunner`，**谁先跑没写**，空库上引导抢在前面查表 → `Table 'mimo.users' doesn't exist` → 启动直接失败；现在 `@Order(HIGHEST/LOWEST)` 写死，并加单测断言顺序。③ **BUG-125**：空库一个账号都没有，而 CI 第一步就要 `admin/123456` 登录 → 新增显式开启的 `mimo.bootstrap.admin-username/password`（默认留空=不动手，只配用户名不配密码也拒绝），建号时同时写旧字段 `users.role` 与 RBAC `user_roles(SUPER_ADMIN, GLOBAL)`，并先查后插避免撞 `user_levels` 唯一键。**结果**：零起点环境（空库）→ 自动建 **38 张表** → 灌演示账号 → 建项目 → 全量 e2e **15 套 148 条 + 3 探针全绿**；workflow 也改成与本机验证一致（`vite` dev server 而非 `preview`——它不配代理 `/api` 全 404、`docker exec` 灌种子、建表结果自检、预热前端编译、pin `playwright@1.65.1`）。顺手修两个"用例假设本机脏数据"：`admin-flow` 依赖 zhangsan 历史上被加过的全局角色（种子改成 `team_members.role=ROLE_ADMIN` 并加纠正 UPDATE）、`presence` 判总人数在连跑时必然飘（改成判"自己的 id 在不在 `onlineUserIds` 里"）。后端 **225 项**测试（新增 `AdminBootstrapTest` 8 条）。① **BUG-122**：AI 规划面板里候选为空时只有一句写死的「Backlog 暂无可规划工作项」，而候选为空其实有**三种**原因、下一步完全不同（项目没工作项 → 该去建；未完成的都排进迭代了 → 该去干活；全做完了 → 该建下一批）。后端本来就算出了 `summary` / `risks`，是前端空态把它们盖掉了。现在 `SprintPlanVO` 增 `emptyReason`（`NO_ISSUES` / `ALL_DONE` / `ALL_SCHEDULED` / `NOTHING_PLANNABLE`），`summary` 与 `risks` 按原因写具体数字，界面显示原因 + 「下一步」+ 一个「去项目看板建工作项」按钮；有候选时该字段不下发（`NON_NULL`）。② **文档诚实化**：计划书里"仍未做：Wiki 历史版本/差异"其实 **v2.13.7/2.13.8 就做完了**，"仍未做：字号体系收敛（100+ 处 10–11px）"实测 **0 处**（BUG-116 那轮全抬到 12px）——两条就地改成"已做 + 版本号"，因为留着假待办会让下一个人照着重做一遍。后端 **217 项**测试（`SprintPlanningServiceTest` 9 → 12）、新 e2e `sprint-plan-empty.mjs` 6 条 → **15 套 148 条 + 3 探针**全绿。
> **上一版本**：v2.22.1（2026-11-26）— 迭代规划的空态说清原因（BUG-122）+ 把计划书里两条过时备注改成实话：① **BUG-122**：AI 规划面板里候选为空时只有一句写死的「Backlog 暂无可规划工作项」，而候选为空其实有**三种**原因、下一步完全不同（项目没工作项 → 该去建；未完成的都排进迭代了 → 该去干活；全做完了 → 该建下一批）。后端本来就算出了 `summary` / `risks`，是前端空态把它们盖掉了。现在 `SprintPlanVO` 增 `emptyReason`（`NO_ISSUES` / `ALL_DONE` / `ALL_SCHEDULED` / `NOTHING_PLANNABLE`），`summary` 与 `risks` 按原因写具体数字，界面显示原因 + 「下一步」+ 一个「去项目看板建工作项」按钮；有候选时该字段不下发（`NON_NULL`）。② **文档诚实化**：计划书里"仍未做：Wiki 历史版本/差异"其实 **v2.13.7/2.13.8 就做完了**，"仍未做：字号体系收敛（100+ 处 10–11px）"实测 **0 处**（BUG-116 那轮全抬到 12px）——两条就地改成"已做 + 版本号"，因为留着假待办会让下一个人照着重做一遍。后端 **217 项**测试（`SprintPlanningServiceTest` 9 → 12）、新 e2e `sprint-plan-empty.mjs` 6 条 → **15 套 148 条 + 3 探针**全绿。
> **上一版本**：v2.22.0（2026-11-26）— 工作流从"拖列进网格"变成真流程：① **节点终于有自己的身份**（`WorkflowNode.nodeKey` + `x/y`，`columnId` 变可空）。以前节点主键就是 `columnId`，等于"流程里的一步必须先是一列"，想加「上线」得先去建列，顺序是反的；现在节点是流程状态、看板列由它投影——保存时链路是 `normalizeConfig`（补身份/坐标、连线两端 key 与 columnId 互相补齐）→ `projectFreeNodes`（缺列的节点建一列并回填）→ `validateConfig`，读出时 `fillNodeIdentity` 给历史配置补身份，**config 是 JSON 所以不动表结构**。② **建列不静默**：`WorkflowVO.createdColumns` 把新建的列名带回，界面弹「为「上线」自动创建了看板列（它们原本没有落脚的地方）」；一个列只能属于一个节点（重复绑定 400）；删节点不删列（列里可能还有工作项）。③ **画布**：节点绝对定位可拖（把手在卡片左上——卡片里全是输入控件，整卡拖动会把点击吞掉，第一版就是这么错的）、SVG 贝塞尔连线 + 箭头，从右侧圆点拖到另一节点就建一条规则（也可以点圆点再点目标）。踩过一个坑：原先 `mouseup` 就清"连线中"状态，而浏览器对"按下在 A、松开在 B"会把 `click` 派发到共同祖先（画布），于是画布先清、目标 click 再执行已经晚了——表现是"拖了半天没连上"。④ **前提条件与角色条件分成两类**：`conditions`（谁可以做，任一命中）vs `prerequisites`（什么情况下才能做，全部满足），首个是 `ACCEPTANCE_DONE`；未勾完时报「还不能进入「已完成」：验收标准还有 2 项没勾完（共 2 项）」，而**一条验收标准都没写的工作项不受限制**（否则老任务永远推不动）。`GET /workflow/transitions` 增可选 `issueId`，给了才提前算得出这句话。⑤ **BUG-121**：设计器的「终态」复选框勾了不生效（看板/工作台判终态用的是 `BoardColumnRules.isTerminalName` 列名规则，`isTerminal` 只用于"至少一个终态"校验），改成只读徽标 + 说明——一个能点但不影响任何行为的控件，比没有更糟。后端 **214 项**测试（`WorkflowServiceTest` 25 → 32）、e2e 新增 `workflow-canvas.mjs` 10 条 → **14 套 142 条 + 3 探针**全绿、`workflow-guard` 8 条回归通过、`vue-tsc`、`npm run build` 全绿。
> **上一版本**：v2.21.0（2026-11-26）— Redis 终于真的用上了（关闭 KNOWN-002）：① 查下来比记录更糟——`MimoApplication` 排除了 `RedisAutoConfiguration`，`StringRedisTemplate` 从来不存在，两处 `@Autowired(required = false)` 永远注入 null，所谓"单设备登录 Token 校验"**从未运行**，`spring.redis.*` 是一段死配置。② 在线状态抽成 `PresenceStore`（`InMemoryPresenceStore` 行为不变 / `RedisPresenceStore` 用 `mimo:presence:sess|user|online` + 90 秒 TTL + 30 秒心跳续期，心跳发现 TTL 过期会重新登记自愈），下行消息抽成 `RealtimeBridge`（`RedisRealtimeBridge` 本地投一次 + 发到 `mimo:bus:realtime`，各实例按 `origin` 丢掉自己那份，保证每个浏览器只收一次）。③ 装配靠互斥条件（`RedisReachableCondition` + 取反版）：`mimo.realtime.store=auto` 启动探一次 Redis（300ms），通就用 Redis、不通就用进程内，开发机没装 Redis 也不影响启动；`RedisAutoConfiguration` **仍然排除**，连接工厂自己建。④ 降级必须说出来：Redis 一抛异常就退回进程内并把 `shared()` 变 false，接口多下发 `presenceStore` / `presenceShared` / `presenceNote`，团队页写「2 人在线（仅本实例）」+ 说明图标——看不见"准不准"的数字，比看到一个不准的数字更糟。⑤ 单设备登录改成 `mimo.security.single-device-login`（默认关）：接上 Redis 后这段逻辑本来会突然生效，同账号多端会互踢，开发调试与 e2e 立刻全崩。⑥ 顺手修掉两个构建坑：`mvn clean package` 其实只出瘦 jar（缺 `repackage` 执行绑定，BUG-119），以及我自己的误操作——`mvn spring-boot:repackage` 在聚合工程里会把 `mimo-common` 打成 22 字节空壳、启动报 `NoClassDefFoundError`（BUG-120）。**真机双实例验证**：起 :8080 A / :8081 B 共用一个 Redis，B 上的连接出现在 A 的在线列表、A 发的团队聊天被 B 上的客户端收到、断开立即回落；再把探针实例改成 `memory` 重跑，这两条按预期不成立（`e2e/two-instance-presence.mjs`）。后端 **207 项**测试、e2e **13 套 132 条 + 3 探针**、`vue-tsc`、`npm run build` 全绿。
> **上一版本**：v2.20.1（2026-11-25）— 全员任务流从"一次铺开所有人"变成能用的面板（关闭 KNOWN-025）：① **分页在后端**（`memberPage` / `pageSize` 默认 8 上限 30、每人 `taskLimit` 默认 6 上限 20），切页顺序是"先筛选 → 再排序 → 最后切页"，所以第 1 页永远是最该管的几个人；被截掉的任务只给计数（`taskTotal`），界面写"还有 N 项没显示（共 M 项）· 点击展开"。② **筛选/排序也搬到服务端**（`keyword` / `only` / `sort`）——这是做分页时才发现的连带 bug：以前筛选按钮上的"有逾期 3"数的是**当前页那 8 个人**，点进去可能只看到 1 个；现在筛选按钮不再显示人数，改由分页文案写"第 x/y 页 · 共 N 人（全队 M 人，已按条件筛选）"，分母永远是全队。③ **概览数字永远按全队算**，筛"空闲"不会让"未完成"变成 0。④ **近 7 天趋势**取自 `activity_logs` 的真实事件（新建 = `CREATE`，完成 = `MOVE` 且目标列是终态列），**不拿 `issues.updated_at` 近似**——项目没有完成时间字段，用更新时间冒充会在"改个标题"时虚增完成数；口径随接口下发并写在图下面。⑤ 把"终态列"判定抽成 `util/BoardColumnRules`（看板拖拽与趋势共用一处，别各写一遍）。过程中自己修掉两个错：`vo.setMembers(streams)` 残留在分页之后会把结果覆盖成全量（页码在变、数据没切），新按钮把 `--color-primary-solid` 当文字色用（暗色 2.82:1，主题审计当场抓到）；另外给 `next-step` 的首屏导航加了 45 秒超时 + 一次重试（连跑十几套后偶发超时，不是产品缺陷）。后端 **192 项**测试、e2e **12 套 126 条 + 3 探针**、`vue-tsc`、`npm run build` 全绿。
> **上一版本**：v2.20.0（2026-11-25）— 这一版全在补"看得见但动不了"的坑，顺手把回归变成一条命令：① **BUG-115**：管理员从「全员任务流」点进成员的任务后，操作被按普通成员拦住（详情面板明写「只有任务负责人（lisi）可以提交验收」）。根因是读门槛在 `ProjectAccessService`（管理员放行）、写门槛却在治理服务里各写一遍（只认 `team_members` 有行）。现在写门槛委托统一入口，`submit()` 抽出 `assertCanSubmit`：负责人**或**管理员都能提交；管理员代提交后仍不能自己验收（职责分离）。② **BUG-117**：同族问题全量排查——发现 11 处"自己实现的写门槛"，症状包括系统管理员进别人团队打不开迭代/团队页、团队管理员看不到待验收、`WikiService` 注释写"含团队管理员"代码却没有、系统管理员删不掉别人的工时。统一收敛到 `canAccessProject / canAccessTeam / canManageProject` 三个判定；团队聊天、建改删项目、加入审批**故意**只给团队权限。真库双向验证：把 admin 从 `team_members` 删掉后 5 个接口 200、换外人账号 403。③ **BUG-116**：全站剩余 106 处 9–11.5px 小字抬到 12px，并用新写的裁切探针确认没撑坏（13 页小字 0、横向溢出 0），顺带揪出一个 `opacity:0` 但仍占 60px 宽度的删除按钮把项目名挤成 46px。④ **BUG-118 / KNOWN-024**：新增 `e2e/run-all.mjs`——一条命令跑 **12 套 120 条 + 3 探针**，失败即退出码 1；顺手发现连跑时 `admin-flow` 会因"挑最后一个成员列"（空闲账号排最后）而超时，单独跑却是绿的。也在这里更正一个长期说错的数字：仓库一直是 **12 套**，不是文档里写的 7 套。CI 配置 `.github/workflows/e2e.yml` 已写，但**没在真实 runner 上跑过**。后端 187 项测试、`vue-tsc`、`npm run build` 全绿。
> **上一版本**：v2.19.8（2026-11-24）— 管理员终于和普通账号长得不一样了：① 顶栏 / 工作台 Hero / 成员列都有了身份徽标（`AdminBadge`，琥珀盾牌 + 金色头像描边），口径是旧 `role` 字段、RBAC `role.manage`、`team.manage` 三者任一，前端只用它决定显不显示。② 工作台的「我的任务」面板加了管理员专属的「我的 / 全员」滑动切换，切到全员就是新接口 `GET /api/workbench/team` 的**全员任务流**：系统管理员看全部团队、团队管理员只看自己管理的团队，返回每人一条 `MemberStreamVO`（负载等级 + 一句话依据 + 排好序的任务）与 `summary`（含**未指派**任务）。空闲的人也占一列——空着本身是信息；未指派单独成栏；负载标签写依据（"有 1 项逾期，未完成 5 项"）而不是只给红黄绿。③ 点任意任务卡右侧滑出**详情抽屉**（`TaskDetailDrawer`：状态/优先级/逾期天数/故事点/标签、6 个字段格、Markdown 描述、验收标准完成度、缺陷信息、子任务、在项目里打开），看完 Esc 关掉还在原位，不跳页。④ 看板给管理员加了「全员泳道」开关。另外修了一条用户当场抓到的 High：前端自己用权限码判管理员，漏掉了 `team_members.role = ROLE_ADMIN` 这条旧字段兜底，团队管理员后端放行、界面却把入口藏了（BUG-113）——现在判定收敛到后端 `AccessControlService#resolveAdminScope`，经 `adminScope` 字段给前端，顶栏 / 工作台 / 看板三处只读这个结论。顺手打磨了一轮前端观感（BUG-114）：全站字号 12px 起（小字 54→32）、抬字号后浮出水面的 4 条隐藏低对比 tag 一并修掉（info/danger/warning/sprint 徽标）、新组件所有可点控件补键盘焦点环。过程中修掉 BUG-110（Element 抽屉的 `close-on-press-escape` 依赖焦点，鼠标点开后按 Esc 没反应）、BUG-111（统计卡标签压淡紫底 2.95:1）、BUG-112（在线人数一次请求问两遍 `PresenceService`，单测里恒为 0）；新登记 KNOWN-025（全员任务流无分页/无历史趋势）。后端 187 项测试、e2e **12 套 120 条**（新增 `admin-flow.mjs` 15 条，含亮/暗两套主题下新界面的对比度与溢出实测）、`vue-tsc`、`npm run build`、`probe-layout` 全绿。
> **上一版本**：v2.19.7（2026-11-24）— 暗色模式的颜色逻辑修好了、界面不再编数字：① 暗色主题下 Element 的 `html.dark` 会把 `--el-color-primary` 重置成默认蓝 `#409EFF`，品牌靛蓝被顶掉；同时多处把 `--text-primary`（暗色下近白）当实心块底色、页面标题写死 `#0f172a`，实测白字压白块只有 1.05:1、标题 1.09:1 直接看不见。现在统一走 `--color-primary-solid` / `--bg-subtle` / `--text-*` 语义变量，暗色实测 `浅色块 0 / 低对比 0`。② 团队页的"在线人数"原来取成员总数，现在 `PresenceService` 按 WebSocket 会话算真实在线，新接口 `GET /api/teams/{id}/presence`，并给简单 broker 加了 10 秒心跳（否则关页面后在线数会虚高）。③ 收口 5 条 Low（在线人数 / 部署脚本确认 / 硬编码颜色 / 无外键改体检工具 / e2e 残留），新增两把尺子：`e2e/theme-audit.mjs` 量配色、`tools/integrity-check.sql` 量孤儿数据（它顺手扫出 BUG-104）。后端 167 项测试、e2e 5 套 47 条断言、`vue-tsc` 与 `npm run build` 全绿。
>
> **上一版本**：v2.19.6（2026-11-24）— 工作流节点能指定责任人了：节点上加了 `ownerUserId`（谁负责把工作项从这一步交给下一步），配了之后只有他或项目管理员能推进，界面被挡住时会点名"这一步由「张三」负责"；`GET /workflow/transitions` 每条转换新增 `fromOwnerName/allowed/reason`，前端据此决定给按钮还是给原因。项目管理员永远能推进是**故意留的逃生口**（责任人休假/离职不能卡死流程）。顺手记一笔自测教训：开发库里 admin/zhangsan/lisi 在 team_members 里的 role **全是 ROLE_ADMIN**，拿他们当"普通成员"测权限会测到管理员放行路径，e2e 改成现场注册纯成员账号。核心链路口径：5 套 43 条断言，后端 160 项。
>
> **上一版本**：v2.19.5（2026-11-24）— 两处链路闭合：① 工作台「待我处理」现在可以直接通过 / 打回待验收（以前要走五步换一个按钮），打回必须填原因，面板下还写清了"验收通过不会自动完成、负责人回看板标记完成"；② 示例数据能生成也能收回——新增 `DELETE /api/admin/demo-data`，只删生成器自己造的东西（项目/工作项/评论/工时/验收/版本快照/变更申请/附件/标签/通知/看板列/迭代/燃尽快照/报告/动态/工作流/交付快照/Wiki），团队仅在"除示例项目外没别的项目"时才删。过程中修掉 BUG-103（清除后再生成撞 `uk_project_key` 报 500，根因是唯一索引不看逻辑删除标记）。e2e 加到 5 套 39 条（review-matrix 新增工作台就地验收一条），后端测试 154 项。
>
> **文档版本号以本文件 + `bug-log.md` 为准**：同一批工作流自动生成改动，另一台机器的提交写 v2.17.1、`CLAUDE.md` 写 v2.18.1，合并后实际是 v2.19.3（`CLAUDE.md` 头部已同步）。

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Mimo is a lightweight Trello-like project management platform for small teams. It supports team collaboration, kanban task tracking, and progress visualization.

- **Version**: v2.22.2 — 全新环境从零到绿：空库自建表 + 启动顺序 + 空库引导（BUG-123~125）(2026-11-26)
- **Previous**: v2.22.1 — 迭代规划空态说清原因 + 计划书过时备注改成实话 (2026-11-26)
- **GitHub**: https://github.com/Oo7350/mimo
- **API docs**: `接口文档.md` (70+ endpoints, Chinese)
- **User manual**: `操作手册.md` (Chinese)

## 收尾必须写文档（Definition of Done，不可跳过）

> 用户的原话：「**每次任务的最后，一定是写文档**」。
> 这不是"有空就补"，是**任务完成的判定条件**——代码跑通了但文档没写，这个任务算没做完。
> 原因很直接：这个项目已经吃过"功能做了但没人知道怎么用、bug 修了但没记根因"的账，
> 下一轮（或换台机器）就得重新推理一遍。

每轮收尾按下面这张表逐项确认，**表里有适用项就必须落到文件里，不允许只留在对话记录**：

| 发生了什么 | 必须写到哪 | 写到什么程度 |
|---|---|---|
| 任何 bug（含自己写出来的） | `bug-log.md` 顶部新增版本段 + `### BUG-NNN: 标题 [High]` | 现象 / 根因 / 修复 / 教训四段；末尾跑 `python tools/buglog_stats.py --check` 对账统计表 |
| 已知隐患收口或判定 | `bug-log.md` 里那条 `KNOWN-NNN` 标题加「— 已解决（vX.Y.Z）」 | 补一行「收口（vX.Y.Z）」说明用什么验证的；不修也要写清为什么 |
| 新功能 / 行为变化 | `操作手册.md`（用户视角怎么用）+ `接口文档.md`（接口契约、字段口径） | 手册写"点哪里、看到什么、边界在哪"；接口写清字段含义与限制，别让读者猜 |
| 修了界面/配色 | `操作手册.md` FAQ 或对应章节 | 说明现在的判据和复查命令（如 `e2e/theme-audit.mjs`） |
| 架构取舍 | `AGENTS.md` Decision Log（+ `CLAUDE.md` 摘要） | 写"为什么不这么做"，否则下一轮又会被当欠账重做 |
| 版本推进 | `AGENTS.md` + `CLAUDE.md` 头部版本号与 Version History + `bug-log.md` 版本段表 + `项目功能完善计划.md` 快照 | 版本号三处一致；测试/e2e 条数是实测数字，不许估 |
| 新增脚本 / 用例 | 对应 README（`e2e/README.md`、`tools/` 注释头） | 怎么跑、判据是什么、当前基线多少 |
| 踩到环境坑 | `AGENTS.md` Local Environment / Commands | 给现成命令，别只写"记得配环境变量" |

- 「文档」包括**注释**：新写的类/方法如果是"为什么这么写"不显然的地方，Javadoc 里写清边界
  （例：`PresenceService` 里写"只算本实例""断线最迟一个心跳周期"）。
- 提交信息里要能看到"文档改了哪些"，方便下一轮只看 `git log` 就知道该翻哪个文件。

## Decision Log（为什么不这么做）

> 这里的每一条都是**已经定过的事**，下一轮不要当欠账重做。要改先改这一节。

| 决定 | 具体做法 | 为什么 | 代价 / 边界 |
|---|---|---|---|
| **不建数据库外键** | 只有 `announcements/issues/projects/teams/users/user_email_accounts` 走逻辑删除（`deleted`），其余物理删除；引用关系由应用层保证 | MyBatis-Plus 逻辑删除与外键约束冲突：删项目会撞一串 FK 错误 | 可能出现孤儿行 → 用 `tools/integrity-check.sql` 体检（本轮 BUG-104 就是它扫出来的） |
| **消息出口只有一个类** | 所有下行消息走 `WebSocketService` → `RealtimeBridge`（`LocalRealtimeBridge` / `RedisRealtimeBridge`），业务代码不直接碰 `SimpMessagingTemplate` | 改造 KNOWN-002 时只要换这一层，20 个调用点一个都不用改；也才可能"每个浏览器只收一次" | 新消息必须走这个出口，绕过它就等于只发本实例 |
| **Redis 是可选能力，连不上不影响启动** | `mimo.realtime.store=auto` 时启动探一次 Redis（300ms）：通就用 Redis，不通就用进程内；`RedisAutoConfiguration` **仍然排除**，连接工厂自己建 | 多实例共享是可选增强，不该让"没装 Redis 的开发机"起不来；自己建工厂也顺手避免 Spring Data Redis 仓库自动配置去要一个 `redisTemplate` bean | 两套实现要各自维护；`RealtimeRedisConfig` / `RealtimeLocalConfig` 靠互斥条件装配（`RedisReachableCondition` + 取反版） |
| **降级要说出来，不能悄悄少算** | Redis 一抛异常就把自己标记 degraded、退回进程内表，`shared()` 变 false，接口 `presenceShared=false` + `presenceNote` 一句话，界面写「N 人在线（仅本实例）」 | "界面上的数字看不出准不准"是本项目反复修的同一个病（KNOWN-013 / KNOWN-025）；宁可少算并说明，也不要展示一个看起来全站实际只有本机的数字 | 降级后在线表只算本实例，要恢复到共享得等 Redis 回来并重连 |
| **在线状态用 TTL + 心跳而不是"断线即删"** | Redis 里 `sess:{sessionId}` / `user:{userId}` 存活 90 秒，本实例每 30 秒续期一次；心跳发现 TTL 已过期会**重新登记**（自愈） | 进程被杀、网络闪断时收不到 DISCONNECT，只靠删除会让在线人数只涨不跌 | 刚断线的一小段时间可能仍算在线（最长一个 TTL 周期），比永久多算一个人强 |
| **单设备登录默认关** | `mimo.security.single-device-login=false`；打开后同账号在别处登录会踢掉旧 Token | 这段逻辑过去靠"`StringRedisTemplate` 永远是 null"静默跳过；接上 Redis 后它会突然生效，而开发调试与 e2e 都用同一账号开多个浏览器上下文，一开就互踢 | 想用单设备登录要显式打开，并接受 e2e 需要改造（每套用独立账号） |
| **在线人数按"人"去重** | `PresenceService` 用 `userId` 维度聚合，多标签页只算一人 | 显示"3 人在线"结果其中一人开了三个页签，是骗人 | 不代表"活跃度"，只代表"连着"；断线最迟一个心跳周期（10s）才掉 |
| **CSS 变量按用途分层** | 见下表 | 暗色模式翻车全部来自"一个变量干两件事"（BUG-106） | 新页面必须按表选变量，选错 `theme_literals.py` 查不出、但 `theme-audit.mjs` 会报到低对比 |
| **实心色块用 `--color-primary-solid`** | 按钮/选中态/徽标底色 | `--color-primary` 在暗色下会变亮，白字压上去不够 | 深色下实心块比亮色略暗，属刻意 |
| **部署必须人工确认** | `deploy.ps1` 需输入 `y`，`-Force` 跳过 | 脚本会 scp 覆盖生产并重启服务 | CI 里要显式传 `-Force` |
| **改字号必须配布局探针** | 抬字号后除了重扫对比度，还要查"是否被裁切"（`overflow:hidden` 且 `scrollHeight/scrollWidth` 超出）与页面横向溢出 | 只看"小字数量归零"会漏掉撑坏的容器；`opacity:0` 的按钮仍占布局宽度，比字号更值得修（BUG-116） | 探针要覆盖真实路由，路由兜底重定向会造成"这页没问题"的假象 |
| **全局改字号要立刻全站重扫对比度** | 抬 `el-tag`/`mimo-nav-label` 字号到 12px 后，`theme-audit` 全站重扫揪出 4 条浮出水面的历史低对比（以前 <12px 小于阈值没被抓） | 体检阈值是按"<12px 记小字"——字号一旦跨过阈值，原本没被抓的低对比会突然全冒出来 | BUG-114 |
| **筛选项的分母必须是被统计的全体** | 筛选/排序/分页一律放服务端；数字写清"筛出 N / 全队 M" | 前端筛当前页会得出"有逾期 3 人"而实际全队 12 人的假数字（v2.20.1 之前就是这样） | 但前端筛选无需请求、更快；代价是它只能看见一页 |
| **趋势从事件算，不从"最后更新时间"猜** | 完成数 = `activity_logs` 里 MOVE 到终态列的事件；口径随接口下发并展示 | 用 `issues.updated_at` 近似时，改个标题、改个标签都会虚增"完成数" | 事件表写得更慢；但数字能被解释 |
| **看板列语义只有一处** | `util/BoardColumnRules#isTerminalName`，拖拽门禁与趋势统计都调它 | 抄两份的判断迟早不一致（BUG-115/117 就是这个病根） | 多一个类；好处是改口径只改一处 |
| **工作流节点有独立身份，看板列由它投影** | `WorkflowNode.nodeKey` 是稳定身份，`columnId` 可空；保存时缺列的节点自动建列并回填，`createdColumns` 把列名带回界面 | 以前节点主键就是 `columnId`，等于"流程里的一步必须先是一列"，加「上线」得先建列（顺序反了）；而不让节点凭空存在又是必须的——工作项总得停在某列，界面才画得出来 | 保存会多出列（界面上必须明说建了哪一列）；删节点不删列，要删列去看板删 |
| **"谁可以做"与"什么情况下"分成两类条件** | `conditions`（`ASSIGNEE`/`REPORTER`/`ROLE_ADMIN`，任一命中）与 `prerequisites`（`ACCEPTANCE_DONE`，全部满足）分开存 | 混在一个 OR 列表里，"仅管理员"就会绕过"验收标准没勾完"，等于没配 | 前提条件对"没写验收标准"的工作项不生效，这是故意留的口子 |
| **拦人时给数字，不给形容词** | 验收标准没勾完报 `验收标准还有 2 项没勾完（共 2 项）`；责任人挡住时点名 | "条件不满足"这种话用户没法行动，只能挨个试 | 需要 `issueId` 才提前算得出，所以 `/workflow/transitions` 加了可选参数 |
| **空态必须由「为什么空」算出来** | 后端返回 emptyReason + 具体数字，界面显示原因与「下一步」，并给一个可点的出路 | 写死一句「暂无可规划工作项」会把三种不同处境说成一种：该去建的、该去干活的、该建下一批的全被同一句话打发（BUG-122）；后端算出来的话被前端空态盖掉，等于白算 | 每多一种空原因就要多维护一句文案 |
| **"我跑过 N 套"不等于"仓库有 N 套"** | 收口前先 `ls e2e`；新套件必须登记进 `run-all.mjs` 的清单 | 手敲命令会漏：文档写 7 套、实际 12 套，漏掉的 5 套一直绿但没人看（BUG-118） | 共享真实库的用例有顺序依赖，选择器别假设"最后一个是什么" |
| **建表自动，"默认账号"必须显式** | 表结构进迁移链（`db/migration/V0__init.sql`）；演示账号与团队放 `db/seed-demo.sql`，要有人敲命令 | 两者混在一个 `init.sql` 里，结果只能是"要么自动带上 admin/123456、要么一张表都不建"，而且那个文件从没被执行过（BUG-123） | 新环境多一步；好处是生产库不会躺着一个默认口令 |
| **启动期 Runner 必须显式排序** | 建表 `@Order(HIGHEST_PRECEDENCE)`、依赖表的引导 `@Order(LOWEST_PRECEDENCE)`，并用单测断言两个值 | 两个 `ApplicationRunner` 都不标顺序时谁先跑"看扫描顺序"，空库上引导抢在建表前面，启动直接崩（BUG-124） | 以后再加 Runner 要自己挑位置；顺序错只在新库暴露，老库永远看不出 |
| **CI 配置必须在本地按同一命令演一遍** | 改 `.github/workflows/*.yml` 时，先把里面的命令在本机原样执行（可用独立空库），确认从零能绿 | 这份 YAML 看着齐全（service、healthcheck、探活、产物），真跑起来第一个 30 秒就撞死：仓库建不出数据库（BUG-123~125） | 演练要花时间、要真起一个空库；但比"推上去看它红"快 |
| **"未验证"和"验证失败"分开写** | 记录里写"没跑过"之前，先去 Actions 翻一遍历史；跑过就写结论 + 链接（红就写红在哪一步） | KNOWN-024 写了半年的"从没在真实 runner 上跑过"，实际 v2.22.0 那次跑过并红在「启动后端」（180 秒等不到登录），后面 4 个 step 全 skipped —— 闸门早就装了，只是没人读结果；把"有反证据"写成"没证据"，下一个人就会带着同一个错再推一遍 | 需要能读到 Actions 历史（公开仓库用 `web_fetch` 打 `api.github.com/.../actions/runs` 即可，curl 可能被本机代理挡掉） |
| **"我这能跑"不算跨平台证据** | 跑不了目标平台时，静态查四件事：绝对路径/盘符、require 本地相对文件的大小写、子进程是否用 `process.execPath`、行尾是否被 `.gitattributes` 钉成 LF | e2e 里曾有一处默认模块路径写死 `C:/mimo-env/…`，在 runner 上只会报 `MODULE_NOT_FOUND`；`core.autocrlf` 是每台机器的设置，靠自觉总有人提 CRLF，`\r` 混进断言就是永远不等的假红 | 静态检查覆盖不了 runner 自身差异（mysql 客户端版本、sudo 装浏览器依赖），最后还是得真跑一次 |
| **"分页了"不等于"没白干"** | 判断一个列表接口是否真的省了活，要数 **SQL 条数**（`SHOW GLOBAL STATUS LIKE 'Com_select'` 前后差），不看响应体大小 | 全员任务流早就分页（每页 8 人），但它仍把全体成员 × 全体工作项算了一遍：分页只省带宽，不省查询。100 人团队实测 **606 条 SELECT / 433ms**，3 人时完全看不出来（BUG-126） | 计数器是全局的，测的时候别同时跑别的请求；`mimo` 账号没有 SUPER，`general_log` 开不了，只能用 `Com_select` 差值 |
| **"批量查了"不等于"查得快"** | 条数降下来之后，用 `EXPLAIN ANALYZE` 看每条的**计划**（`type` / `rows` / 有没有 filesort、有没有回表），别只看它跑通 | 606 条 SELECT 压到 28 条后接口还剩 125ms：`activity_logs` 上三个索引全是单列，吃不住 `project_id IN + action + created_at` 三条件，优化器退回全表扫 4 万行（BUG-132）。加 `(project_id, action, created_at)` 后 31.4→16.4ms，接口 p95 387→141ms | `EXPLAIN ANALYZE` 要 8.0.18+；计划随数据量翻转，量级变了要重看 |
| **"有 WHERE 就建索引"是错的直觉** | 建索引前先量两种选择性：命中比例高（>50%）时顺序扫反而更快，索引只是多一次回表；只有**选择性好**才赚。量完不建的，把数字写进迁移文件注释 | 看板/报表形状 `project_id = ? AND deleted = 0` 看着最该建复合索引，实测 17% 选择性下扫表 6.45ms、走索引 **7.65ms**（更慢），87% 时优化器直接忽略 → 不建；而 `issues (sprint_id, status)` 在 0.9% 选择性下 6.47→0.33ms → 建 | 选择性依数据分布而变，压测数据要像真数据（见下一条）；一条 SQL 的实际选择率要看 `EXPLAIN ANALYZE` 的 `rows`，不是 `filtered` 的估计 |
| **压测数据也得"长得像真数据"** | 造数据时对着**代码的判定条件**写文案：`detail` 要能被 `SUBSTRING_INDEX(detail,'移动到',-1)` 认出列名；seed 末尾多打一列证明"被判定命中的行数" | 第一版写成 `压测活动 LOAD1-1 #123`，「移动到终态列」在压测数据上一行都统计不到，量到的"几毫秒"是空转（BUG-133） | 文案一改，清理脚本的"按文案删"就会失效甚至误删，清理一律按 **id** 删 |
| **量级数据必须能一键清，且不掺进演示库** | 压测数据单独一个「压测团队」+ 配套 `tools/cleanup-load.sql`；跑全量 e2e 前先清 | 100 个成员一进来，「全员任务流」的耗时和成员数断言全变，回归会莫名红；把数据和演示数据混在一起，清的时候又不敢删 | 每次想量都得先灌一遍（751ms，可接受） |
| **CI 里的耗时只记录，不设门槛** | `perf-probe` 打印 p50/p95 后照常退出 0；要卡就卡**条数**（查询数、明细行数），不卡毫秒 | runner 规格会变，把 540ms 设成红线，下次机器快一点就没人再关心，慢一点就随机红；条数（606 → 28）才是可复现的结论 | 数字不会自动报警，得有人偶尔看一眼日志 |
| **"服务能响应"≠"服务准备好了"** | 就绪判定选"依赖全部完成之后才会出现"的证据：`grep -q "Started MimoApplication"`（Runner 跑完才打）**且** 登录能过；不确定的计数检查一律"等一会儿再看" | Spring Boot 先开 HTTP 端口再跑 `ApplicationRunner`，而建表就在 Runner 里：迁移跑到一半 `users` 表已存在、登录能过，于是"探活通过"被当成"库建完"，CI 红在**只建出 25 张表**（BUG-131）。前三次全绿只是迁移够快凑出来的时序运气 | 日志文案变了会失效，改启动类时要一起改；断言仍要留一个真数字（38 张表） |
| **步骤顺序就是依赖关系** | 排 CI 步骤前先看旁边已经绿的 job 怎么排；建表 → 种子 → 用例，顺序错了就是 `Table … doesn't exist` | 新写的 `two-instance` job 把"灌种子"排在"起后端"前面，1 秒内就红（BUG-130），而同文件的主 job 一直是绿的——不是环境问题，是没照着抄 | 抄顺序不能连"为什么这么排"一起抄丢，否则下次又拧回去 |
| **访问判定只写一次** | 用 `ProjectAccessService` 的 `canAccessProject` / `canAccessTeam` / `canManageProject`，调用点只保留自己的错误文案 | 同一判断题在 11 个地方各写一遍，每次漏一个角色：管理员"能看不能动"、团队管理员看不到待验收、注释与代码矛盾（BUG-115 + BUG-117） | 团队聊天与建/删项目属于**故意**只给团队权限，别一刀切放宽；关键判定别用 mock 测 |
| **读门槛与写门槛必须同源** | 写操作统一走 `ProjectAccessService.requireProject(...)`，与读完全一致 | 各服务自己写 `isTeamMember(...)` 会出现"能看进去、一动手就被拦"，系统管理员看别的团队时连写都进不去（BUG-115） | 新增服务不要自己判；已发现 10+ 处同款写法，逐步收敛 |
| **前端只读后端给的 `adminScope`** | 管理范围由 `AccessControlService#resolveAdminScope` 判定，写进 `/api/user/profile` 与登录响应；前端只用它决定显示什么 | 前端看不到旧字段兜底（`team_members.role`）与其他迁移开关，自己算必然对不上后端（BUG-113） | 多一个字段要跟着判定改；新增权限来源时只改 `resolveAdminScope` 一处 |
| **管理员视角是"范围"不是"特权"** | `GET /api/workbench/team` 的差别只在**看哪些团队**：系统管理员看全部、团队管理员看自己管理的、其他人明确拒绝 | "管理员能看所有人的任务"是工作要求，不是奖励；把范围写清楚，越权与偷窥才分得清 | 团队管理员看不到别的团队（哪怕他是那个团队的普通成员）；范围口径要与 `AccessControlService` 保持一致，改一处必须改另一处 |
| **非管理员拿到 403 而不是空列表** | `BusinessException(FORBIDDEN, "只有管理员能查看全员任务流…")` | 空列表会被读成"大家都没任务"，界面先撒一次谎 | 本项目业务错误是**响应体 `code` + HTTP 200**（`GlobalExceptionHandler`），判错的人会以为成功了；e2e 一律断言 `body.code` |
| **负载标签必须给依据** | `loadLevel` 永远配 `loadReason`（"有 1 项逾期，未完成 5 项"），阈值写在 `applyLoad` 一处 | 只给红黄绿灯等于让管理员猜；逾期权重高于数量，因为"逾期了手上几件都不重要" | 阈值是经验值，不是科学结论；团队规模变了要重新看 |
| **颜色来自数据时算对比度** | `readableInk(bg)` 决定白字还是深字（日历事件条） | 写死 `color:#fff` 时，浅色数据必然看不清（BUG-108） | 计算在渲染时进行，极端渐变背景仍按主色判 |

### 颜色变量怎么选（照这张表，别自创）

| 要用在哪 | 用哪个变量 | 不要用 |
|---|---|---|
| 正文文字 | `--text-primary` / `--text-regular` / `--text-secondary` | ~~把 `--text-*` 当底色~~（BUG-106） |
| 弱化说明、占位符 | `--text-muted` / `--text-placeholder`（亮色也压到 ≥4.5:1） | `#909399`、`#cbd5e1` 之类字面值 |
| 页面底 / 卡片底 / 悬停底 | `--bg-body` / `--bg-card` / `--bg-hover` / `--bg-subtle` | `#fff`（暗色下是浅色块） |
| 实心按钮、选中态、徽标底 | `--color-primary-solid`（+ `-hover` / `-active`） | `--text-primary`、`--color-primary` |
| 语义色文字（成功/警告/危险） | `--color-success` / `--color-warning` / `--color-danger` | Element 的 `--el-color-*`（暗色下语义会被重算） |
| 语义色浅底 | `--bg-success-soft` / `--bg-warning-soft` / `--bg-danger-soft` / `--bg-info-soft` | 手写 `rgba(...)` |
| 边框 / 分隔线 | `--border-color` / `--border-light` | `#e2e8f0` |
| 颜色来自数据（事件条、头像） | `readableInk(bg)` + `avatarGradient()` | `color:#fff` 硬编码 |

- 覆盖 Element 变量时，`[data-theme="dark"]` 与 `html.dark` **两处都要写**：Element 的
  `dark/css-vars.css` 在 `html.dark` 下会重置 `--el-color-primary`（BUG-105）。
- 检查命令：`python tools/theme_literals.py --check`（样式块里的中性色字面值应为 0）、
  `node e2e/theme-audit.mjs http://localhost:5173`（暗色浅色块 / 低对比应为 0）。



## Tech Stack

- **Frontend**: Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts + vuedraggable + driver.js + marked + SockJS + STOMP.js + html2pdf.js + DeepSeek API + Inter font
- **Backend**: Spring Boot 2.7.18 (Java 17+) + MyBatis-Plus 3.5.3.1 + MySQL 8.0 + Redis + Spring Security + JWT (jjwt 0.11.5) + Spring WebSocket + STOMP + **DeepSeek API (AI Copilot)**
- **Deployment**: Docker Compose (Nginx + Spring Boot + MySQL + Redis)
- **Build**: Maven (backend), npm (frontend)

## Local Environment (oo7350's machine)

| Tool | Path / Version |
|------|---------------|
| Java 21 | `D:\Java\jdk-21` (21.0.5 LTS) |
| Maven 3.6.1 | `D:\maven\apache-maven-3.6.1` |
| MySQL 8.0.33 | `D:\MySQL\MySQL Server 8.0\` (root password: `336699`, local service) |
| Redis | Native Windows, `localhost:6379` (auto-started) |
| Node | v22.22.2, npm 10.9.7 |
| Python | 3.10+ |
| Docker | **Not available** — use native MySQL/Redis instead |

**Important**: Docker is not installed on this machine. Always use the local MySQL and Redis services. Set `JAVA_HOME=D:\Java\jdk-21` before running Maven (the system default is Java 8).

**GitHub SSH**: Configured to use `ssh.github.com:443` (port 22 blocked by GFW). Push via HTTPS instead.

## Commands

### Backend (from `backend/`)

```bash
# Build (must set JAVA_HOME first)
set JAVA_HOME=D:\Java\jdk-21
mvn clean compile
mvn clean package -DskipTests

# Run (dev)
mvn spring-boot:run

# Run (jar)
java -jar target/mimo-backend-1.0.0-SNAPSHOT.jar
```

**启动前必须给三个环境变量**（`application.yml` 里没有硬编码兜底，缺一个就起不来）：

| 变量 | 缺了会怎样 |
|------|------------|
| `JWT_SECRET` | `JwtUtil` 构造抛 `WeakKeyException`，整个上下文起不来 |
| `MIMO_DB_PASSWORD` | `Access denied for user 'mimo'@'localhost' (using password: NO)` |
| `JAVA_HOME` | `mvn` 找不到 JDK |

本机有一份现成的（不进仓库）：`. C:\mimo-env\dev-env.ps1`，里面就这几个值。
这套坑是 v2.19.7 重启后端时踩到的：jar 明明打好了，却报 `WeakKeyException` / `Access denied`，
看起来像"构建坏了"，其实只是环境变量没带。

**路径注意（本机）**：`C:\mimo-env\mimo` 是指向 `C:\Users\小晟\Desktop\AItest\mimo` 的 junction。
**前端构建要走真实路径**（`cd C:\Users\小晟\Desktop\AItest\mimo\frontend`）——从 junction 路径跑
`npm run build` 会报 `[vite:build-html] The "fileName" ... must be strings that are neither absolute
nor relative paths, received "../../../Users/..."`（v2.20.1 踩到，白查了一轮）。
后端 `mvn` 用哪条路径都行。


### Frontend (from `frontend/`)

```bash
npm install
npm run dev          # Vite dev server on :5173, proxies /api → :8080
npm run build        # Type-check (vue-tsc) + production build to dist/
```

### 端到端用例（E2E）

### 端到端用例（E2E）

仓库 `e2e/` 下是真实浏览器 + 接口级用例（Playwright 装在仓库外，不污染 `package.json`）。
**不要一个个手敲**——用统一入口：

```bash
PLAYWRIGHT_BROWSERS_PATH=<浏览器目录> PLAYWRIGHT_MODULE=<playwright 包目录> NO_PROXY=localhost,127.0.0.1 \
  node e2e/run-all.mjs                 # 15 套 148 条，约 10 分钟；--probes 连探针一起；--only x,y 只跑几套
node e2e/two-instance-presence.mjs   # 双实例验证（需两个后端共用同一 Redis）；--local 跑进程内基线
node e2e/presence-source-label.mjs   # 在线人数"口径"文案（已并入 run-all 清单）
```

全部通过退出码 0，任何一套失败退出码 1 并打印该套输出尾部。跑完执行 `e2e/cleanup-residue.sql`。

| 套件 | 条数 | 覆盖 |
|---|---|---|
| `main-chain.mjs` | 11 | 主链路闭环 |
| `core-flows.mjs` | 6 | 创建任务 / 拖拽进进行中（含自动计时）/ 评论 / 附件 / 验收 / 新建 Sprint |
| `review-matrix.mjs` | 13 | 验收权限矩阵（谁能提交、谁能通过/打回、已处理后不可再动） |
| `next-step.mjs` | 6 | 详情页「下一步」建议与链路口径 |
| `chain-links.mjs` | 5 | 事件/链接/ChatOps 口径一致 |
| `workflow-guard.mjs` | 8 | 工作流转换守卫（含节点责任人、逃生口） |
| `workflow-canvas.mjs` | 10 | 工作流画布：自由节点投影成列 + 拖连线 + 验收标准前提 |
| `sprint-plan-empty.mjs` | 6 | 迭代规划的候选空态说清三种原因并给出路 |
| `admin-flow.mjs` | 26 | 管理员视角（徽标 / 全员任务流 / 抽屉 / 亮暗主题 / 权限 / 代提交验收） |
| `presence.mjs` | 4 | 在线人数按 WebSocket 会话算（判自己的 id 在不在名单里，不比总人数） |
| `tour.mjs` | 9 | 新手引导（自动触发 / 跳过 / 重播 / Esc） |
| `ai-degrade.mjs` | 3 | AI 未配置时的提示条与降级话术 |
| `xss-markdown.mjs` | 10 | Markdown 与邮件正文的 XSS 载荷（跑仓库真实源码；**不需要起服务**） |
| `authz.mjs` | 25 | 外人打各读接口必须 4xx / 成员正常 / 缺参数 400（接口级，不需要浏览器） |

探针（量尺子，不判业务）：`probe-text.mjs`（小字/裁切/横向溢出）、`probe-layout.mjs`（15 页布局）、
`theme-audit.mjs`（亮暗两套主题的对比度与浅色块）。三者都会被 `--probes` 一起跑。

**套件共用同一个开发库，彼此不独立**：曾出现"单独跑绿、连起来红"——`admin-flow` 挑了
"最后一个成员列"，而别的套件注册的空闲账号正好排在最后（BUG-118）。选择器别假设"最后一个是什么"；
用例崩过之后再跑先清残留。

CI：`.github/workflows/e2e.yml`。**已在真实 GitHub runner 上跑通**（v2.22.3，
[run #8](https://github.com/Oo7350/mimo/actions/runs/45926155601)：8 分 36 秒、35 个 step 全绿，
其中「跑全部 e2e」5 分 41 秒）——KNOWN-024 已关闭。
在此之前"从空库到全绿"先在本机按同一套命令演过一遍（v2.22.2）：
空库启动 → 自建 38 张表 → 灌 `db/seed-demo.sql` → `node e2e/seed-fresh-db.mjs` → `run-all.mjs` 全绿。
演练照出三个致命坑（建表脚本从没执行 / Runner 顺序未定 / 空库没账号，BUG-123~125），
所以**别随意调换步骤顺序**——顺序是踩过坑之后的样子。改配置前先在本地演一遍（见决策表末几条）。

v2.22.4 起 workflow 变成**两个 job**（都已在本地按同一命令验过）：
- `e2e`（19 个 step）：原有链路 + 末尾「造量级数据 → 性能探针 → 清量级数据」三步。
  探针**只出数字、不设门槛**——机器规格会变，拿它当红线只会变成随机红。
- `two-instance`（9 个 step，`needs: e2e`）：mysql + redis 两个 service，jar 从主 job 的 artifact 取回，
  起两个后端（8080 / 8081）共用一个 Redis，跑 `e2e/two-instance-presence.mjs`（跨实例在线 + 跨实例广播）。
  单独一个 job 是为了不让 `store=memory` 的主回归和 Redis 语义混在一个进程里。

```bash
# 全新环境（空库）从零到能跑：后端自动建表 → 演示账号 → 演示项目（幂等，可重复跑）
mysql -u mimo -p mimo < backend/mimo-app/src/main/resources/db/seed-demo.sql
node e2e/seed-fresh-db.mjs            # MIMO_API=http://127.0.0.1:8080/api 可改地址
```

跑法与三个必踩的坑（driver.js 的 `driver-active` 会锁死全站点击、Element Plus 页签要 `dispatchEvent`、
vuedraggable 不吃 `locator.dragTo`）见 `e2e/README.md`。

### Database

**空库不用手工建表**：后端启动时 `MigrationRunner` 会按序执行 `db/migration/V*.sql`，
其中 `V0__init.sql` 就是基础 18 张表（`IF NOT EXISTS`，老库跑一遍是空操作）。
v2.22.2 之前它是 `db/init.sql`，**从没被程序执行过**，只出现在下面这条手工命令里（路径还是错的）——
见 BUG-123。真正的操作只有两步：

```bash
# 1) 起后端（空库会自己建出 38 张表）
java -jar backend/mimo-app/target/mimo-app-1.0.0-SNAPSHOT.jar

# 2) 只有需要演示账号/团队时才灌种子（生产环境不要跑，里面有默认口令）
mysql -u mimo -p mimo < backend/mimo-app/src/main/resources/db/seed-demo.sql
```

演示账号 `admin` / `zhangsan` / `lisi`，密码都是 `123456`（BCrypt 存的）。
其中 **zhangsan 是"旧字段兜底的团队管理员"**：`users.role=ROLE_MEMBER`、RBAC 无角色，
但 `team_members.role=ROLE_ADMIN`——`admin-flow` 用例专门盯这个落差，别把它改成普通成员。

空库想直接有个管理员（CI / 新环境）：配 `MIMO_BOOTSTRAP_ADMIN_USERNAME` +
`MIMO_BOOTSTRAP_ADMIN_PASSWORD` 启动一次即可（`AdminBootstrap`，默认关闭，只建不覆盖）。

## Architecture

Single backend + RBAC with two roles: `ROLE_ADMIN` (team/project admin) and `ROLE_MEMBER`.

### Backend Package Layout (`com.mimo`)

| Package | Purpose |
|---------|---------|
| `common/` | `Result<T>` response wrapper, `ResultCode` enum, `BusinessException`, `GlobalExceptionHandler` |
| `ai/` | **`AiProvider`** (interface), **`AiService`** (orchestration + quota), **`DeepSeekProvider`** (SSE streaming impl), `dto/` (`AiChatRequest`, `AiChatResponse`) |
| `config/` | `SecurityConfig`, `JwtAuthenticationFilter`, `CorsConfig`, `MyBatisPlusConfig`, `WebSocketConfig`, `WebSocketAuthInterceptor` |
| `controller/` | 16 REST controllers under `/api/*` (including **`AiController`**, ApprovalController, UserLevelController) |
| `service/` | 15 service classes including `WebSocketService`, `ChatService`, **`ApprovalService`**, **`UserLevelService`** |
| `mapper/` | MyBatis-Plus `BaseMapper` interfaces (no XML, all queries via `LambdaQueryWrapper`) |
| `entity/` | 21 entity classes mapped to database tables (including ApprovalRequest, UserLevel, **WikiPage/WikiVersion/WikiAttachment**) |
| `dto/` | Request/response DTOs per domain |

### Frontend Layout

| Directory | Purpose |
|-----------|---------|
| `api/` | `request.ts` (Axios instance + interceptors) + per-domain API modules + **`ai.ts`** (DeepSeek API: chatStream/polish/priority/parse-issue/suggest-tasks/estimate-story-points) |
| `store/` | Pinia stores — `user.ts` (auth/role), `app.ts` (sidebar, current project, **dark mode**) |
| `router/` | Vue Router with navigation guard for auth + redirect |
| `views/` | 12 page components (Dashboard, ProjectOverview, ProjectBoard, ReportList, **LevelManage**, **Wiki**, etc.) |
| `components/layout/` | `MainLayout.vue`, `AuthLayout.vue` (split login/register), `BreadcrumbNav.vue` |
| `components/issue/` | `IssueDetailDialog.vue`, `IssueCard.vue` (hover quick actions), type-specific cards/dialogs, **`AiIssueCreator.vue`** (AI对话式创建), **`AiTaskSplitter.vue`** (Story拆分) |
| `components/common/` | `StatCard.vue`, `SkeletonLoader.vue`, `PageHeader.vue`, **`CommandPalette.vue`**, `AssigneeAvatar.vue`, **`UserBadge.vue`**, **`AiChatPanel.vue`** (全局AI助手悬浮窗) |
| `composables/` | `useTour.ts`, `useNotifications.ts`, `useWebSocket.ts`, **`useCommandPalette.ts`** (Ctrl+K) |
| `types/` | TypeScript interfaces for all domain objects |
| `utils/` | `constants.ts`, `markdown.ts`, **`color.ts`** (avatar gradient hash) |
| `styles/` | `variables.css` (light/dark CSS tokens), `global.css` |

### API Pattern

- All responses wrapped in `Result<T>`: `{ code: 200, message: "success", data: ... }`
- Public endpoints: `/api/auth/login`, `/api/auth/register`, `/api/ws/**` (WebSocket), Swagger UI paths
- All other endpoints require `Authorization: Bearer <token>` header
- WebSocket: STOMP over SockJS at `/api/ws`, JWT auth via CONNECT frame header
- Axios request interceptor auto-attaches token from localStorage; response interceptor handles 401 (redirect to login), 403 (toast), 500 (toast)
- Knife4j/Swagger UI at `/swagger-ui.html` and `/doc.html`

### Database

21 MySQL tables (InnoDB, utf8mb4): `users`, `teams`, `team_members`, `projects`, `project_members`, `board_columns`, `issues`, `issue_labels`, `attachments`, `sprints`, `burndown_snapshots`, `reports`, `activity_logs`, `comments`, `notifications`, `chat_messages`, **`approval_requests`**, **`user_levels`**, **`wiki_pages`**, **`wiki_versions`**, **`wiki_attachments`**.

- Schema at `backend/mimo-app/src/main/resources/db/migration/V0__init.sql`（迁移链第一环，启动时自动执行）
- Seed data: 3 users (admin/zhangsan/lisi, password `123456`), 1 team, 3 team members
- Soft delete via MyBatis-Plus `@TableLogic` on `deleted` column (users, teams, projects, issues)

## Key Features

- **Kanban Board**: 3 fixed columns (TODO/IN_PROGRESS/DONE) with drag-and-drop, **swimlanes** (by assignee/epic), **filter presets**, card **hover quick actions** (complete/edit), search and filter (assignee/priority/type)
- **Command Palette**: **Ctrl+K** global search — cross-project issue query, page navigation, quick actions; header search trigger in `MainLayout`
- **UI/UX (v2.1)**: Split-screen auth (`AuthLayout`), dashboard hero + 4 stat cards, dark mode (`data-theme` + `appStore.darkMode`), consistent card grids (teams/projects), polished project overview charts
- **Real-time Collaboration**: WebSocket-powered board sync — drag-and-drop broadcasts to all users on the same board; instant notification push (no polling)
- **Notifications**: Click ISSUE notification → fetch issue by id → navigate to `/projects/{projectId}/board?issue={id}` (requires `projectId` on `IssueVO`, v2.1+)
- **Project Overview**: Status pie chart, member workload bar chart with completion rates, upcoming due tasks, sprint burndown chart (ideal vs actual), recent activity timeline
- **Task Comments**: Markdown-enabled discussion thread per issue, Ctrl+Enter to send; commenter notification to assignee
- **Reports**: Daily/weekly report generation with auto-draft content; data dashboard with 3 ECharts charts; one-click PDF export
- **Sprint**: Quick-start 2-week iteration with auto task assignment; burndown chart with snapshots; member statistics (completion rate, overdue rate, avg time in column); complete-with-migration for undone tasks
- **AI Assistant (v2.8.0)**: **DeepSeek-powered AI Copilot** — SSE流式对话(`POST /api/ai/chat/stream`，body 传 message/systemPrompt)、智能任务创建(自然语言→结构化任务)、Story拆分子任务、润色描述、优先级推荐、故事点估算；全局悬浮窗(AiChatPanel)集成于MainLayout；API Key由后端管理(DEEPSEEK_API_KEY环境变量)
- **Onboarding Tour (v2.17.0)**: driver.js 三主题引导——`welcome`（快速上手）/ `board`（看板实操）/ `collab`（协作与治理），账号菜单常驻入口可重播；每步锚点自解析、找不到就跳过，退出统一清理 `driver-active` 等遮罩状态；状态按主题记在 `mimo_tour_state`（跳过 ≠ 完成）
- **Chat Recall**: Messages can be recalled within 2 minutes; recalled messages show gray italic text; WS broadcast syncs recall state
- **Single-Session Login**: Redis-backed token validation — same account login kicks previous session (401 "账号已在其他设备登录")
- **@Mention**: Type `@` in chat input to trigger member picker; selected username inserted as `@username`; rendered as green highlight in message bubbles
- **Team Admin Permission (v2.7)**: Two-level permission system — system admin (ROLE_ADMIN) has global control, team admins can invite/remove members and approve requests; non-admins need approval for project operations
- **Approval Workflow (v2.7, v2.7.1 hardened)**: Non-admins creating/deleting projects auto-generate approval requests; **backend enforces 403 for direct API calls** (not just frontend UI guard); team admins can approve/reject via API or team chat; approved actions execute automatically; admin global approval list via `GET /api/approvals/pending`
- **User Level System (v2.7)**: L1-L4 levels managed by system admin only; dynamic badge UI in profile page with progressive visual effects (L4 has rotating glow animation)
- **403 Fix**: JwtAuthenticationFilter now returns 401 JSON directly for invalid/expired tokens instead of passing through to Spring Security (which caused misleading 403)
- **Approval Notification (v2.7.2)**: Backend auto-sends notification to requester on approve/reject (`APPROVAL_APPROVED` / `APPROVAL_REJECTED` types); frontend shows ElNotification popup on login + bell icon unread badge
- **Auto-Degrade Fallback (v2.7.2)**: Frontend catches 403/500 from backend project API calls and auto-degrades to approval request; non-admins never see raw 500 error for project operations
- **Full User Level Display (v2.7.2)**: `UserLevelService.listAll()` now queries all registered users from `users` table, defaulting to L1 for those without level records — admin sees complete user list
- **Requirement Change Impact Analysis (v2.15.0)**: Before approving a change, compute affected subtasks and downstream dependencies, Sprint scope, story-point/date deltas, completed-work rework risk, and explainable LOW/MEDIUM/HIGH risk reasons. The analysis is read-only and remains available for historical requests.
- **Personal Workbench (v2.15.1)**: Aggregates assigned open tasks across accessible projects, today/overdue subsets, deterministic task risk, pending change approvals, pending task reviews, rejected submissions, weekly work hours, and current Sprint effort.
- **Sprint Health Analytics (v2.15.2)**: Deterministic time/work progress comparison, remaining effort, overdue/blocked/pending-review counts, estimated delay, health score, explainable risk reasons, rule-based suggestions, live board refresh, and optional AI narrative fallback.
- **Delivery Analytics (v2.15.3)**: Sprint Velocity from committed/completed story points, daily TODO/IN_PROGRESS/PENDING_REVIEW/DONE snapshots, project/Sprint/date-filtered cumulative flow chart, and dedicated `/projects/:id/analytics` view.
- **AI Sprint Planning (v2.15.4)**: Advisory Backlog selection using priority, due dates, dependencies, story points and historical Velocity, with deterministic fallback, editable capacity, user-selected issue IDs, and explicit apply-to-planning-Sprint validation.
- **Project Knowledge RAG (v2.15.5)**: Project-scoped index over Wiki pages, Issues and comments, Chinese-friendly BM25 retrieval, evidence-only answers with clickable citations, no-evidence refusal, rebuild endpoint, and Recall@K/citation/unsupported-answer evaluation.
- **Access Control Hardening (v2.15.6)**: Sprint and Team read endpoints now enforce project/team membership; approved requirement changes can clear nullable fields with explicit SQL updates; stale known-issue documentation was corrected.

### Frontend deep links

| URL query | Behavior |
|-----------|----------|
| `?issue={id}` | Open issue detail dialog on board (used by notifications, dashboard todos, command palette) |
| `?create=1` | Open create-issue dialog on board (used by command palette) |

## Known Issues

### In-memory WebSocket broker

WebSocket currently uses Spring's in-memory broker. For multi-instance deployment, switch to a Redis-backed STOMP relay; a single instance is unaffected.

### `.Codex/settings.local.json`

This file defines allowed Bash commands for Codex in this repo — if you add new toolchains or scripts, update it so Codex can execute them.

### Planned AI Features

All v2.15 AI roadmap items are implemented. Future work may replace the lexical RAG MVP with an embedding/vector index while preserving the citation contract.

## WebSocket Architecture

- **Endpoint**: `/api/ws` (SockJS, with HTTP fallback)
- **Auth**: JWT Bearer token in STOMP CONNECT frame's `Authorization` header, validated by `WebSocketAuthInterceptor`
- **Broker**: Simple broker (`/topic` for broadcast, `/queue` for user-specific)。消息分发走 `WebSocketService` → `RealtimeBridge`：
  `LocalRealtimeBridge` 只投本进程，`RedisRealtimeBridge` 本地投一次 + 发到 Redis 频道 `mimo:bus:realtime`，
  各实例收到后再投给自己的连接，**消息里带 `origin` 把自己发的那份丢掉**（否则本机用户会收到两遍）。
- **Online presence**: `PresenceService` → `PresenceStore`（`InMemoryPresenceStore` / `RedisPresenceStore`）。
  Redis 键：`mimo:presence:sess:{sessionId}`（TTL 90s）、`mimo:presence:user:{userId}`（SET，TTL 90s）、
  `mimo:presence:online`（SET 索引，读取时顺手清死键）；本实例每 30 秒心跳续期一次。
- **Topics**:
  - `/topic/notifications/{userId}` — personal notification push
  - `/topic/board/{projectId}` — board sync events (ISSUE_MOVED/CREATED/DELETED)
  - `/topic/team-chat/{teamId}` — team group chat messages (CHAT_MESSAGE event)
- **Board Sync Flow**: Drag → `PUT /api/board/issue/move` → backend updates issue + logs + WebSocket broadcast → all board subscribers receive event → optimistic UI update
- **Deployment note**: 单实例部署什么都不用配（默认 `auto`，探不到 Redis 就用进程内实现）；
  多实例部署需要共享 Redis，否则在线人数与实时消息各算各的（接口会如实下发 `presenceShared=false`，界面写「仅本实例」）。

## Version History

### v2.22.4 — 量级验证：把「性能」从推理变成数字（BUG-126~133）(2026-11-27)

前几轮补的是"功能对不对"，这一轮问的是"人多了还站得住吗"。答案是动手之前**不知道**：
开发库常年 3 个用户 / 20 个工作项 / 1156 条活动日志，所有"分页了、批量查了"的结论都是读代码推的。

做法（每一步都能复现）：

1. `tools/seed-load.sql` 造 **100 用户 / 5 项目 / 500 工作项 / 1000 评论 / 5000 活动日志**，
   全挂在独立「压测团队」下，不掺演示数据；`tools/cleanup-load.sql` 一键删干净。
2. `e2e/perf-probe.mjs` 对 18 个核心读接口打 N 轮，输出 p50/p95/max，按 p95 倒序。
3. SQL 条数用 `SHOW GLOBAL STATUS LIKE 'Com_select'` 前后差值（`mimo` 账号没 SUPER，`general_log` 开不了）。
4. 热点用 `jcmd <pid> Thread.print` 在压测中采样线程栈。

结果（同一台机器、同一份数据、10 轮）：

| 接口 | 改前 p95 | 改后 p95 | 改前 SELECT | 改后 SELECT |
|---|---|---|---|---|
| `/workbench/team`（100 成员） | 540ms | **125ms** | 606 | **28** |
| `/reports/stats/{id}` | 108ms | **< 30ms** | 40 | **8** |
| 其余 16 个核心接口 | 均 < 60ms | 同量级 | ≤ 3 | 同 |

- **BUG-126**：`isTeamAdmin` 逐成员判定（每人 6 条 SELECT）→ 新增批量
  `AccessControlService#filterTeamAdmins(userIds, teamIds)`，口径不变（旧字段 ∪ RBAC），
  并写了"批量版必须等于逐人版"的等价性用例。
- **BUG-127**：项目统计按成员 `selectById`、按迭代 `selectCount×2` → 各改成一次批量；
  该接口此前零测试，补了 `ReportServiceTest`（4 条，既守数字也守条数）。
- **BUG-128**：近 7 天趋势把明细整批搬进 JVM 再数 → 下沉成 `GROUP BY DATE(created_at)`（最多 7 行）；
  终态判定仍只在 `BoardColumnRules`，等价性用真实数据验（改前改后逐日逐字段一致）。
- **BUG-129**：在线状态逐人问 Redis（每人 2 次往返）→ 先取在线集合再求交集。

顺带收口：计划书路线图快照 v2.19.5 → 当前版本；新增 `tools/cleanup-soft-deleted.sql`
物理清理逻辑删除的项目（**先自测**：造一个 → 跑 → 校验，实际清掉 3 个项目 / 31 个工作项）；
双实例（跨实例在线 + 跨实例广播）写进 CI（新 job `two-instance`，mysql + redis 两个 service，
两个后端共用一个 Redis），并把 `two-instance-presence.mjs` 写死的 Windows 绝对路径改成按脚本位置解析。

**BUG-130**（新 job 自己抓到的第一个问题）：`two-instance` 第一次在真实 runner 上跑就红了——
第 7 步「灌演示账号」1 秒内失败（`Table 'mimo.users' doesn't exist`），后三步 skipped。
原因是我把顺序排成了"先灌种子再起后端"，而空库的建表是后端启动时 `MigrationRunner` 干的活；
主 job 里同样的命令能跑，只是因为它先起后端。照主 job 的顺序重排
（启动 → 等就绪 → 灌种子 → 验证）后绿。

**BUG-131**（紧接着的第二个）：重排后主 job 反而红了——「建表结果自检」发现**只建出 25 张表**，
而「启动后端」那步是绿的。根因是 Spring Boot **先开 HTTP 端口、再跑 `ApplicationRunner`**，
迁移中途 `users` 表已存在、`AdminBootstrap` 与登录都正常，于是"能登录"被当成了"建完了"；
前三次的绿灯只是迁移够快（10 秒内跑完）凑出来的时序运气。修法：就绪改成
`grep -q "Started MimoApplication"`（这句在 Runner 全部跑完之后才打）**且** 登录能过；
自检改成"最多等 60 秒、最后一次不达标才红"，失败时打印 `MigrationRunner` 日志与现有表清单。

同一轮顺手把 `perf-probe.mjs` 从"永远退出 0"改成"慢不报错、跑挂必报错"——
前者那种写法就是 KNOWN-024 老毛病（闸门装了没人读结果）的翻版。

**补：索引这一层（BUG-132/133）。** 606 条 SELECT 压到 28 条之后接口还剩 125ms，
这层用 `Com_select` 看不见，得用 `EXPLAIN ANALYZE`：`activity_logs` 上有三个索引
（`idx_project` / `idx_created` / `idx_created_at`）但**全是单列**，而查询形状是
`project_id IN (...) AND action = ? AND created_at >= ?`，一条都吃不住，优化器退回全表扫。
新增 `V25__hot_query_indexes.sql` 建 `(project_id, action, created_at)`，同时删掉
`idx_created`——它和 `idx_created_at` 是同一个东西（V16 加的时候没注意 V0 已有）。
4 万行日志上的实测：新建趋势 31.4→16.4ms、移动趋势 63.7→47.2ms；
接口层 A/B（关掉/装回索引、缓存已热、12 轮）p95 **387→141ms**。
第一次 A/B 量出 759→177ms（4.3 倍）**没敢用**——那轮 MySQL 缓冲池是冷的，快慢里混了"磁盘读"，
反向再量一遍才确认稳的数是 387→141。同一轮还**量掉了两个看着该建的索引**：
`issues (project_id, deleted)` 在 17% 选择性下 6.45ms（扫表）vs 7.65ms（走索引），
加了更慢 → 不建；`issues (sprint_id, status)` 在 0.9% 选择性下 6.47→0.33ms，选择性差时
优化器自动忽略 → 建。**"有 WHERE 就建索引"是错的直觉。**
还有 **BUG-133**：压测数据的 `detail` 原本写成 `压测活动 LOAD1-1 #123`，
于是「移动到终态列」那条趋势查询在压测数据上一行都统计不到——我量到的"几毫秒"是**空转**的时间；
改成真实文案（`将 X 移动到 已完成`），并顺手堵掉 `cleanup-load.sql` 里那个
"按文案删"的 `OR`（一旦放宽就会连演示项目和 e2e 的正常日志一起删）。

后端 **234 项**测试（AccessControlServiceTest +5、新增 ReportServiceTest 4 条）；账本 **133 条 BUG 全修 + 0 条 KNOWN 未关闭**。

### v2.22.3 — 关闭 KNOWN-024：CI 在真实 runner 上第一次全绿 (2026-11-27)

这一版没有新 Bug，只有"把最后一步走完"和"把一句写错的记录改对"。

**结果**：`workflow_dispatch`（commit `5747663`）→
[run #8](https://github.com/Oo7350/mimo/actions/runs/45926155601)，job **8 分 36 秒**，
**35 个 step 全 success**（唯一 skipped 是「失败时留存日志」，它本来就只在 failure 时跑）。
随后 push 文档触发的 [run #9](https://github.com/Oo7350/mimo/actions/runs/45928085911)（commit `e9a1219`）
**再次全绿**：8 分 57 秒、e2e 步骤 5 分 43 秒。两种触发方式（手动 / push）都验过。

| 阶段 | 实测 |
|---|---|
| 后端单测（225 项） | 31 秒 |
| 启动后端（空库自建表 + 空库引导管理员） | 9 秒 |
| 建表结果自检（门槛 ≥30） | 通过 |
| 灌演示账号 / 预热项目 | 各 ≤1 秒 |
| 前端 `npm ci` + `vue-tsc` + `vite build` | 56 秒 |
| `playwright@1.65.1` + `--with-deps chromium` | 19 秒 |
| **跑全部 e2e（15 套 148 条 + 3 探针）** | **5 分 41 秒，通过** |

**顺手纠正一句记录**：KNOWN-024 一直写着"**从没**在真实 runner 上跑过"。翻 Actions 历史才发现
v2.22.0 那次（[#4](https://github.com/Oo7350/mimo/actions/runs/45775384583)）**跑过，并且红了**：
红在「启动后端」，180 秒等不到登录（正是 BUG-123/125 的症状），后面 4 个 step 全 skipped。
所以真相不是"没机会验证"，而是"**验证失败，但没人读结果**"。
这两句话的差别很大——"未验证"只是没证据，"验证失败"是有反证据；
把它写成前者，下一个人就会带着同一个错再推一遍。

**账目**：BUG **125 条全修**，KNOWN **0 条未关闭**（历史 KNOWN 已关闭 27 条）。

### v2.22.2 — 全新环境从零到绿：空库自建表 + 启动顺序 + 空库引导（BUG-123~125）(2026-11-26)

- **起点**：计划书里最后一条未闭合项是 KNOWN-024「e2e 没在真实 CI 上跑过」。原本打算改 YAML，
  改成先**在本机把 CI 的步骤原样执行一遍**（独立空 MySQL 实例、空库、同一条命令）。
- **第一个 30 秒就红了，而且不是 YAML 的问题**：空库启动后只有 23 张表，
  `users / teams / projects / issues` 一个都没有。根因是基础建表脚本 `db/init.sql`
  **从来没被任何程序执行过**（`MigrationRunner` 只扫 `db/migration/V*.sql`），
  它唯一的引用是文档里一条路径还写错的手工命令。
- **修法（三步，缺一不可）**：
  1. `init.sql` → `db/migration/V0__init.sql`（排序最前、只有 DDL、`IF NOT EXISTS` 幂等）；
     测试账号与演示团队拆成 `db/seed-demo.sql`，需要显式执行——建表任何环境都该自动，
     "默认口令的账号"不该。
  2. 建表与空库引导都是 `ApplicationRunner`，**谁先跑没写**，空库上引导抢在前面查表 → 启动崩。
     现在 `@Order(HIGHEST_PRECEDENCE)` / `@Order(LOWEST_PRECEDENCE)` 写死，并加单测断言顺序。
  3. 空库没有账号而 CI 第一步就要登录 → 新增 `mimo.bootstrap.admin-username/password`
     （`AdminBootstrap`，默认留空=不动手；只配用户名不配密码也拒绝）。
     建号时同时写旧字段 `users.role` 与 RBAC `user_roles(SUPER_ADMIN, GLOBAL)`，
     并先查后插（V5 迁移给 1/2/3 号用户预埋了铭牌，撞上就会崩）。
- **顺手纠正两处"配置在说谎"**：`application.yml` 的 `url` 改成 `${MIMO_DB_URL:…}`
  （以前写死，CI 里那行环境变量形同虚设）；workflow 改用 `vite` dev server
  而不是 `vite preview`（`preview` 不配代理，页面里的 `/api` 请求全 404）。
- **结果**：零起点环境 → 自动建 **38 张表** → 灌种子 → 建项目 → 全量 e2e
  **15 套 148 条 + 3 探针全绿**；后端 **225 项**测试（新增 `AdminBootstrapTest` 8 条）。
- **两条"用例假设本机脏数据"也一并修了**：`admin-flow` 依赖 zhangsan 历史上被手工加过的
  DEVELOPER 全局角色（种子改成 `team_members.role=ROLE_ADMIN` + 纠正 UPDATE）；
  `presence` 判总人数在连跑时必然飘（改成判"自己的 id 在不在 `onlineUserIds` 里"）。
- **教训**：没演练过的 CI 配置等于没写。这份 YAML 看着挺像样（服务容器、healthcheck、
  环境变量、探活、产物留存都有），但当真的一条条执行时，第一个 30 秒就撞死。

### v2.22.1 — 迭代规划的空态说清原因（BUG-122）+ 计划书两条过时备注改成实话 (2026-11-26)

- **起点**：查"计划书还剩什么没做"，结果三条里两条早就做完了。
- **BUG-122**：AI 规划面板候选为空时只有一句写死的「Backlog 暂无可规划工作项」，
  而候选为空有**三种**原因、下一步完全不同（没工作项 → 去建；都排期了 → 去干活；全完成了 → 建下一批）。
  后端本来就算出了 `summary` / `risks`，是前端空态把它们盖掉了。
  - `SprintPlanVO` 增 `emptyReason`：`NO_ISSUES` / `ALL_DONE` / `ALL_SCHEDULED` / `NOTHING_PLANNABLE`；
    `summary` 与 `risks` 按原因写具体数字；有候选时不下发（`NON_NULL`）。
  - 前端空态：主文案用后端 `summary`，下面「下一步：…」+ 「去项目看板建工作项」按钮。
- **文档诚实化**：计划书里「仍未做：Wiki 历史版本/差异」实际 v2.13.7/2.13.8 已完成；
  「仍未做：字号体系收敛（100+ 处 10–11px）」实测 **0 处**（BUG-116 那轮全抬到 12px）。
  两条就地改成"已做 + 版本号"——留着假待办会让下一个人照着重做一遍。
- **测试**：后端 **217 项**（`SprintPlanningServiceTest` 9 → 12，三种原因各一条 + 有候选时不设 `emptyReason`）；
  新 e2e `sprint-plan-empty.mjs` 6 条 → **15 套 148 条 + 3 探针**。
- **顺手加固**：`workflow-guard` 的登录页导航加重试（连跑十几套时 Vite 重编译首屏偶发 >20s，
  会让整套 0 条检查点就红掉——环境抖动不该被当成产品缺陷）。

### v2.22.0 — 工作流：自由节点 + 画布连线 + 「验收标准勾完才能完成」(2026-11-26)

- **起因**：计划书里挂了很久的「脱离看板列的自由节点」。以前 `WorkflowNode` 的主键就是 `columnId`，
  等于"流程里的一步必须先是一列"——想加「上线」步骤得先去建列，顺序是反的。
- **节点身份**：`WorkflowNode` 增 `nodeKey` / `x` / `y`，`columnId` 变可空。
  保存链路 `normalizeConfig`（补身份/坐标、连线两端 key 与 columnId 互相补齐）→
  `projectFreeNodes`（缺列节点建列并回填）→ `validateConfig`；读出时 `fillNodeIdentity`
  给历史配置补身份，**不动表结构**（config 是 JSON）。
- **不静默建列**：`WorkflowVO.createdColumns` 带回新建的列名，界面提示"为「上线」自动创建了看板列"。
  一个列只能属于一个节点（重复绑定 400）；删节点不删列（列里可能有工作项）。
- **画布**：节点绝对定位可拖（把手在卡片左上，因为卡片里全是输入控件）、SVG 贝塞尔连线 + 箭头，
  从右侧圆点拖到另一节点 = 建一条规则（也可点圆点再点目标）。点连线弹出条件面板。
- **前提条件**：`WorkflowTransition.prerequisites` 与 `conditions` 分开——
  前者"什么情况下"（**必须全部满足**），后者"谁"（**任一命中**）。首个取值 `ACCEPTANCE_DONE`，
  未勾完时拒绝并报`验收标准还有 2 项没勾完（共 2 项）`；**没写验收标准的工作项不受限制**。
  `GET /workflow/transitions` 增可选 `issueId`，给了才提前算得出这条提示。
- **BUG-121**：设计器的「终态」复选框勾了不生效（看板/工作台判终态用列名规则），改成只读徽标 + 说明。
- 后端 **214 项**单测（`WorkflowServiceTest` 25 → 32）；e2e 新增 `workflow-canvas.mjs` 10 条，
  **14 套 142 条 + 3 探针**全绿；`workflow-guard` 8 条回归通过（选择器改到 `[data-workflow-canvas]`）。

### v2.21.0 — Redis 真的用起来了：跨实例在线状态与实时广播（关闭 KNOWN-002）(2026-11-26)

- **起因（比记录更糟）**：KNOWN-002 写着"Redis 已引入但没实际使用"。实际是
  `MimoApplication` 排除了 `RedisAutoConfiguration` → `StringRedisTemplate` bean 从未存在 →
  `JwtAuthenticationFilter` / `UserService` 里两处 `@Autowired(required = false)` 恒为 null →
  "单设备登录校验"从未运行；`spring.redis.*` 是一段死配置。
- **在线状态**：新增 `service/PresenceStore`（接口）、`InMemoryPresenceStore`（原逻辑原样搬迁）、
  `RedisPresenceStore`。`PresenceService` 只负责"记住本实例持有哪些会话"+"30 秒心跳续期"，
  读写全部委托给 store，`TeamService` / `WorkbenchService` 的调用点一行没改。
  - Redis 键：`mimo:presence:sess:{sessionId}`（userId，TTL 90s）、
    `mimo:presence:user:{userId}`（SET sessionId，TTL 90s）、`mimo:presence:online`（SET userId 索引）。
  - `touch(Map<String,Long>)` 收"会话 → 用户"映射：TTL 过期但人还在时重新登记（自愈），
    避免"人还挂着却被判离线"。
  - 读取时顺手清理索引里的死键（用户集合已过期的条目），不让它越积越多。
- **实时广播**：新增 `service/RealtimeBridge`、`LocalRealtimeBridge`、`RedisRealtimeBridge`。
  `WebSocketService` 三个方法（通知 / 看板 / 团队聊天）改为走 bridge，业务调用点不变。
  跨实例消息形如 `{origin, destination, payload}`，发到频道 `mimo:bus:realtime`；
  各实例收到后按 `origin` 丢掉自己发的那份（否则本机用户会看到两遍）。
  payload 用 Spring 的 `ObjectMapper` 序列化，日期格式与本地投递一致。
- **装配**：`config/RedisReachableCondition`（`auto` 探测 300ms / `memory` 强制进程内 /
  `redis` 强制 Redis）+ `RedisUnreachableCondition`（取反），
  `RealtimeRedisConfig` / `RealtimeLocalConfig` 两套互斥配置。连接工厂自己建
  （`LettuceConnectionFactory`，命令超时 500ms），`RedisAutoConfiguration` **仍然排除**。
  **不启用 `@ConditionalOnMissingBean`**：它取决于配置类解析顺序，顺序一变就是"两套 bean 都在"。
- **降级**：`RedisPresenceStore` 任何 RuntimeException（连不上 / 超时）→ 标记 degraded →
  之后读写走进程内表，`shared()` 变 false；`RedisRealtimeBridge` 发布失败同理（本地投递仍成功）。
  接口 `GET /teams/{id}/presence` 新增 `presenceStore` / `presenceShared` / `presenceNote`，
  团队页写「N 人在线（仅本实例）」+ 说明图标。
- **单设备登录显式化**：新增 `mimo.security.single-device-login`（默认 `false`）。
  没有这个开关的话，接上 Redis 会让同账号多端互踢——开发调试与 e2e 立刻全崩。
- **构建坑（BUG-119 / BUG-120）**：`mimo-app/pom.xml` 补 `repackage` 执行绑定，
  `mvn clean package -DskipTests` 现在真的产出 69MB 可运行 jar；同时记一笔误操作教训——
  `mvn spring-boot:repackage` 在聚合工程里会打到每个模块，把 `mimo-common` 变成 22 字节空壳。
- **验证**：`PresenceServiceTest` 9 项、`RedisPresenceStoreTest` 7 项、`RedisRealtimeBridgeTest` 5 项
  （共 21 项新/改，后端 207 项全绿）；`e2e/two-instance-presence.mjs` 双实例真机 5/5
  （另有 `--local` 反向对照）；`e2e/presence-source-label.mjs` 6/6（已并入 `run-all.mjs`）；
  全量 `run-all.mjs --probes` → 13 套 132 条 + 3 探针全绿。

### v2.19.8 — 管理员视角：身份徽标 + 全员任务流 + 任务详情抽屉 (2026-11-24)

- **管理员身份可视化**：`components/common/AdminBadge.vue`（盾牌 + 琥珀渐变 + 一次性微光，
  `prefers-reduced-motion` 下不动）。顶栏用户名旁、工作台 Hero、团队成员列表、全员任务流成员列共用。
  头像加金色描边（`.el-avatar.is-admin`）。判定单一来源：旧 `users.role` / RBAC `role.manage` /
  `team.manage`，**前端只用来决定显示**，安全判断仍在后端。
- **身份模型回顾**：`AccessControlService.isSystemAdmin(userId)`（`role.manage`，旧字段兜底）、
  `isTeamAdmin(userId, teamId)`（TEAM 作用域的 `team.manage`，旧 `team_members.role` 兜底）。
- **全员任务流**：`GET /api/workbench/team`（`WorkbenchController#getTeamWorkbench` →
  `WorkbenchService#getTeamWorkbench`）。
  - 范围：系统管理员 → 全部团队（`scope=SYSTEM`）；团队管理员 → 只看自己管理的团队（`scope=TEAM`）；
    非管理员 → `BusinessException(FORBIDDEN, "只有管理员能查看全员任务流（…）")`，**不返回空列表**。
  - DTO（`WorkbenchDTO.java` 内嵌）：`TeamWorkbenchVO{scope, teamIds, teamNames, summary, members}`、
    `TeamWorkbenchSummaryVO{memberCount, busyMemberCount, openTaskCount, overdueTaskCount, doneTaskCount,
    inProgressTaskCount, unassignedTaskCount, onlineMemberCount, unassignedTasks}`、
    `MemberStreamVO{userId, username, avatar, role, admin, online, openCount, inProgressCount,
    overdueTaskCount, doneCount, tasks, loadLevel, loadReason}`、`UnassignedTaskVO`。
  - 负载规则（`applyLoad`）：`OVERLOADED` 逾期≥2 或未完成≥8；`BUSY` 逾期=1 或未完成≥5；
    `IDLE` 未完成=0；其余 `NORMAL`。逾期权重大于数量，且每条都带中文 `loadReason`。
  - 性能：项目/工作项/迭代/前置依赖/在线状态各按批查一次（不按成员逐个查）。
- **前端**：`components/workbench/TeamFlowBoard.vue`（成员列网格 + 交错入场 + 搜索/筛选/排序 +
  相对负载条 + 未指派栏）、`components/issue/TaskDetailDrawer.vue`（右侧抽屉 + 骨架→内容淡入 +
  Esc 关闭 + 「在项目里打开」出口）、工作台「我的 / 全员」滑动切换（全员视角面板
  `grid-column: 1 / -1` 占满整行）、看板「全员泳道」开关（`swimlaneMode='assignee'` 的显式入口）。
- **BUG-113（High，用户当场发现）**：前端自己用 `role === 'ROLE_ADMIN' || has('role.manage') || has('team.manage')`
  判断"是不是管理员"，漏掉 `team_members.role = ROLE_ADMIN` 这条旧字段兜底 ——
  开发库里 zhangsan 后端放行（scope=TEAM）、界面把徽标与入口整个藏了，用户看到的就是"这个功能没做"。
  修复：判定收敛到 `AccessControlService#resolveAdminScope`（返回 `SYSTEM / TEAM / NONE`），
  通过 `/api/user/profile` 与登录响应的 `adminScope` 字段给前端，`store/user.ts` 暴露 `isManager / managerLabel`，
  工作台/顶栏/看板三处只读这个结论。
- **KNOWN-025 收口（分页 + 服务端筛选 + 趋势）**：全员任务流原来是"一次铺开所有人"。
  补分页时发现原来的筛选是**假**的——分母是当前页 8 个人（"有逾期 3"点进去可能只有 1 个），
  于是一起搬到服务端。趋势取自 `activity_logs` 的真实 CREATE / MOVE 事件，
  不拿 `issues.updated_at` 近似（改个标题就会虚增完成数）。顺带把"终态列"判定抽成
  `util/BoardColumnRules`，看板拖拽与趋势共用一处。自己踩了两个坑：`vo.setMembers(streams)`
  残留会把分页结果覆盖成全量；新按钮把 `--color-primary-solid` 当文字色（暗色 2.82:1）。- **BUG-118（Low，测试基础设施）**：把 12 套 e2e 串成一条命令时暴露 —— `admin-flow` 用
  `.member-stream` 的**最后一个**当"有任务的成员列"，而本套件和别的套件都会注册空闲账号
  （`e2e-flowmember*`）排在最后，于是等一个永不出现的卡片：单独跑绿、连跑必红。
  改成挑"真正带 `.flow-task` 的列"并新增一条检查。同时新增 `e2e/run-all.mjs`（12 套 120 条
  + 3 探针，失败退出码 1）与 `.github/workflows/e2e.yml`（未在真实 runner 验证）。
  **顺带更正一个长期说错的数字**：仓库一直是 12 套，文档里写的"7 套"是只数了手敲的那几条命令。- **BUG-117（High）**：BUG-115 的同族问题——全后端 11 处"自己实现的写门槛"口径不一致：
  系统管理员进别人团队打不开迭代/团队页、团队管理员看不到待验收、知识库注释与代码矛盾、
  删别人工时的反而不含系统管理员。收敛到 `ProjectAccessService` 三个判定方法
  （`canAccessProject`/`canAccessTeam`/`canManageProject`），调用点保留各自文案。
  测试从 3 条扩到 9 条，关键判定改用真实实现而非 mock；真库双向验证（管理员 200 / 外人 403）。- **BUG-116（Low）**：历史遗留的 106 处 <12px 小字（9–11.5px，26 个文件）一次性抬到 12px；
  用裁切探针逐页验证后，13 个真实路由 **小字 0 / 横向溢出 0**。抬字号顺带暴露一个更隐蔽的问题：
  团队详情「关联项目」的删除按钮 `opacity: 0` 但仍占 ~60px 宽度，把项目名挤到 46px ——
  改成悬停浮层后回到 83px。教训写进 bug-log：全局改字号必须配布局探针，且探测脚本要用真实路由。
- **BUG-115（High，用户当场发现）**：用户问"管理员点进成员的项目面板后，进行操作怎么是成员的逻辑"——
  详情面板里真的写着「只有任务负责人（lisi）可以提交验收」。两条口径打架：读门槛在
  `ProjectAccessService`（系统管理员/团队成员/团队管理员都可），写门槛却在
  `IssueReviewService` 与 `IssueChangeRequestService` 里各写一遍、只认 `team_members` 行；
  同一个服务里"验收"给了管理员逃生口、"提交验收"没给。修复：两个 Service 的写门槛委托
  `ProjectAccessService`；`submit()` 抽 `assertCanSubmit`，负责人**或管理员**都能提交；
  前端 `isAdmin` 补 `userStore.isManager`（`adminScope`）。
- **BUG-114**：全站字号 12px 起（`el-tag`/`mimo-nav-label` 等从 11–11.5px 抬到 12px，小字 54→32），
  抬字号让 4 条历史低对比 tag 浮出水面（info `#909399` / danger `#f87171` / warning `#fbbf24` / sprint `#10b981`），
  逐条压深达标；新组件可点控件补 `:focus-visible`。
- **BUG-110**：Element 抽屉自带的 `close-on-press-escape` 依赖焦点在容器内，鼠标点开卡片后按 Esc
  无效 → 打开期间在 `window` 上挂捕获阶段 keydown，关闭/卸载摘掉。
- **BUG-111**：统计卡标签用 `--text-muted` 压 `--color-primary-lighter` 淡紫底，亮色 2.95:1 →
  改 `--text-secondary`。
- **BUG-112**：`getTeamWorkbench` 里在线状态问了两遍（`filterOnline` + `countOnlineAmong`），
  单测里 summary 恒为 0 → 只调一次，summary 取 `onlineUserIds.size()`。
- **KNOWN-025**（新登记）：全员任务流一次返回全部未完成任务、无分页、不含历史趋势；大团队再优化。
- **用例**：`e2e/admin-flow.mjs` 15 条（身份徽标有/无、视角切换、成员列数=接口人数、概览数字
  =接口数字、点卡片开抽屉且标题一致、字段非空、Esc 关闭、普通成员调接口 `code=403`、
  亮/暗两主题下新界面无低对比文本与横向溢出）。后端 `WorkbenchServiceTest` 4 条新增，共 171 项。

### v2.19.7 — 暗色配色系统性修正 + 在线人数落实 + Low 收口 (2026-11-24)

- **配色：四类系统性问题**（详见 bug-log BUG-105~109）
  - Element dark css-vars 的 `html.dark` 重置 `--el-color-primary` → 品牌色在暗色下漂移；
    现在 `[data-theme="dark"]` 与 `html.dark` 双写 primary 全家桶，`light-7/8/9` 换成低透明度。
  - 把 `--text-primary` 当实心块底色（登录按钮、看板标签页选中态、分段控件）→ 暗色下白字压白块
    1.05–1.18:1。新增 `--color-primary-solid`（两主题都深靛蓝，白字 4.6:1），约定 `--text-*` 只当文字色。
  - `ShinyText` 标题写死 `#0f172a` → 暗色下标题隐形（1.09:1）。改传 `var(--text-primary)`。
  - 头像三色系 `#f7b955/#00b96b` 白字 1.75/2.58:1 → 压深到 `#b45309/#047857/#2563eb`，
    另加 `readableInk()` 处理"颜色来自数据"（日历事件条）。
- **工具**：`tools/theme_literals.py`（中性色字面值 → 语义变量，`--check` 归零才算合格，
  本轮替换 82 处/10 文件）、`e2e/theme-audit.mjs`（真浏览器量实际底色与对比度，
  暗色 `浅色块 0 / 低对比 0`）、`tools/integrity-check.sql`（16 类引用关系体检）。
- **在线人数（KNOWN-013）**：`PresenceService` 按 STOMP 会话登记/注销，
  `WebSocketPresenceListener` 兜住无 DISCONNECT 帧的断线；`GET /api/teams/{id}/presence` 返回
  `onlineCount / totalMembers / onlineUserIds`；团队页「N 人在线 · M 位成员」+ 头像绿点。
  简单 broker 补 `setHeartbeatValue(10s)` + 调度器，否则关页面后在线数会长时间虚高。
- **其它 Low 收口**：`deploy.ps1` 加确认（`-Force` 跳过，给 CI）；无外键明确为设计选择 +
  体检工具；e2e 残留补 §8 通用孤儿清理。
- **BUG-104**：`integrity-check.sql` 扫出 6 行 `project_members` 挂在逻辑删除的项目上，
  已清理并把通用兜底写进 `cleanup-residue.sql`。
- **验证**：`mvn test` 167 项；e2e 5 套 43 条 + `presence.mjs` 4 条；`vue-tsc`、`npm run build` 通过。

### v2.19.6 — 工作流节点责任人 + 交接说明 (2026-11-24)

- **节点责任人**：工作流配置的节点新增 `ownerUserId`（谁负责把工作项从这一步交给下一步）。
  语义是"从这一步往外推要这个人点头"，不是"只能他来干活"；留空 = 不限制。
  项目管理员（系统管理员 / 项目负责人 / 团队管理员）永远能推进 —— 故意留的逃生口。
  设计器每节点一个「责任人」下拉（选项来自团队成员，可清空），只读概览多一列「这一步谁负责」。
- **`GET /workflow/transitions` 变诚实**：每条转换新增 `fromOwnerId` / `fromOwnerName` /
  `allowed` / `reason`，前端据此决定"给按钮"还是"给原因"。只对两种确定性情形下结论
  （责任人不是当前用户、规则写着仅管理员），`ASSIGNEE` / `REPORTER` 不猜，真正拦截在
  `validateTransition`。
- **保存校验**：责任人必须是真实用户，否则 `400 节点「X」的责任人不存在（userId=…）`。
- **自测教训**：开发库 admin / zhangsan / lisi 在 `team_members` 里全是 `ROLE_ADMIN`，
  拿他们当"普通成员"测权限会走到管理员放行路径；e2e 现场注册 `e2e-wfowner*` 纯成员账号来验。
- **验证**：后端 160 项全绿；e2e `workflow-guard` 8/8（真拖拽）、其余 4 套回归通过，合计 43 条；
  `vue-tsc` 与 `npm run build` 通过。

### v2.19.5 — 工作台就地验收 + 示例数据一键清除 (2026-11-24)

- **工作台「待我处理」直接处理**：`views/Dashboard.vue` 的待验收行加「通过 / 打回」，
  通过可留空说明、打回必须填原因；处理后就地刷新。变更审批仍只给「去审批 →」（要先看影响分析）。
  面板底部写明"验收通过不会自动完成，负责人回看板标记完成"，避免"点了通过就以为结束"。
- **示例数据一键清除**：`DemoDataService.clear()` + `DELETE /api/admin/demo-data`，
  只删生成器造的东西，团队按"是否只剩示例项目"决定去留；前端按钮按状态在「生成 / 清除」间切换。
- **BUG-103（Medium）**：清除后再生成 500（`Duplicate entry 'DEMO2'`）。根因是 `projects.uk_project_key`
  是唯一索引而项目是逻辑删除，`allocateProjectKey()` 只查未删除行 → 又挑到同一个 Key。
  修复：`ProjectMapper.selectAllKeysIncludingDeleted()` / `selectByNameIncludingDeleted()` 两条自定义 SQL，
  分配 Key 一律问前者；补 2 条单测把"清除后再分配"钉住。
- **清库脚本同步修**：`e2e/cleanup-residue.sql` 先删 `burndown_snapshots` 再删 `sprints`，
  并在末尾做全局孤儿收尾（通知 / 燃尽快照 / 交付快照），结果查询多两列孤儿计数。
- **验证**：后端 154 项全绿；e2e 5 套 **39 条**全绿（review-matrix 13 / main-chain 11 /
  next-step 6 / chain-links 5 / workflow-guard 4）；`vue-tsc` 与 `npm run build` 通过。
  接口实测：生成(23) → 清除 → `exists:false` → 再生成(24) 正常，孤儿数据 0。

### v2.19.4 — 验收按角色分工：界面别撒谎 + 多角色矩阵 e2e (2026-11-24)

目标是把 KNOWN-017 剩下的"多角色"那半补上，结果撞出一个真缺陷。

- **BUG-102（Medium）**：`IssueReviewPanel.vue` 只判了 `status === 'PENDING'`，没判"我是谁"，
  于是提交人自己、无关的普通成员都能看到「通过 / 打回」，「提交验收」对非负责人也显示，
  点了才被后端拒（`不能验收自己提交的结果` / `只有项目管理员或报告人可以验收` /
  `只有任务负责人可以提交验收`）。现在面板按后端同一口径（`canSubmit` / `canReview`）决定显示，
  没有按钮的人改为看到一句"在等谁"的说明；上下文（报告人、项目负责人）由面板自己取，
  拿不到就宁可不显示按钮。
- **不是 bug 的确认**：验收通过不会自动把卡搬进「已完成」。看板侧有 review gate：
  最新一轮未通过时拖进完成列会被 409 拦住；通过后由负责人拖/点「标记完成」收工，
  详情页「下一步」里就有这个按钮。用例把两头都钉住了。
- **新用例** `e2e/review-matrix.mjs`（12 条）：非负责人不能提交、负责人提交、提交人不能验自己、
  普通成员不能验、界面显示（提交人看不到 / 报告人看得到）、跳步被工作流挡、
  review gate 挡住未通过的完成、通过后可完成、已处理记录不能再操作、打回必须填原因且回退+进「被驳回」、
  团队管理员（RBAC `team.manage`）能验收。
- **账目**：关闭 KNOWN-017、新登记 KNOWN-024（e2e 未接 CI），并更正上一版把"017 部分已解决"
  当已关闭导致少算 1 的统计（现为 BUG 102 / KNOWN 未关闭 7 / 总计 109）。
  同时补上一直被引用却不存在的 `tools/buglog_stats.py`：`python tools/buglog_stats.py --check`
  会拿 `bug-log.md` 里那张统计表对账，不一致退出码 1（已用"故意改错总计"反向验证能报红）。
- **验证**：后端 149 项全绿（本轮无后端改动）；e2e 5 套 **38 条**全绿
  （review-matrix 12 / main-chain 11 / next-step 6 / chain-links 5 / workflow-guard 4）；
  `vue-tsc` 通过；`npm run build` 通过。

### v2.19.3 — 合并另一台机器的工作流自动生成，两边改动对上号 (2026-11-24)

另一台机器推了 4 个提交（`dc94d84` / `7c125ee` / `bc779e3` / `c51955d` 合并），
我这边的 v2.19.1、v2.19.2 已在远端，所以是**快进合并**（`5f77217 → c51955d`），没有冲突。

- **他们的功能**：`WorkflowService.ensureDefaultWorkflows` 在建项目时按看板列自动生成
  STORY/TASK/BUG 三套默认工作流（Scrum，前进+回退，`is_default=1`），
  `GET /api/workflow/project/{id}` 对存量项目懒补齐；`POST /api/issues/{id}/claim` 认领链路
  （管理员直接认领并进入「进行中」，普通成员走 `ISSUE_CLAIM` 审批，通过后 `IssueClaimService.applyClaim`
  自动指派推进）；审批通知不再每页重复弹；个人动态改掉存储型 XSS。
- **两边接口对上了**：他们的自动生成正好落在我 v2.19.1 加的只读概览上——示例项目首次访问生成 3 套、
  每套 4 节点 6 条规则，页面显示「自动生成」标签 + 「从→到→谁可以这么做」规则表；再查仍是 3 套。
- **本轮实际改动**：删掉合并后同一页重复的一段"自动生成"说明（`PageHint` 已经说过）；
  同步 `CLAUDE.md` 头部版本号（原来写 v2.18.1，比实际落后）。
- **验证（合并后重跑）**：后端 142 → **149 项**全绿（+他们的工作流 3 项、认领 4 项）；
  e2e `workflow-guard` 4/4、`main-chain` 11/11、`next-step` 6/6、`chain-links` 5/5；
  `vue-tsc` 通过；`npm run build` 通过。

### v2.19.2 — 空状态引导 + 演示数据入口 (2026-11-23)

清掉两条一直挂着的 Medium：KNOWN-019（空状态缺下一步）与 KNOWN-020（没有演示数据入口）。
病根是同一个：页面在"什么都还没有"的时候不给用户任何出路。

- **空状态（KNOWN-019）**：新增 `components/common/EmptyGuide.vue`，把空状态定成三段——
  这里会出现什么、为什么现在是空的／它有什么用、下一步点哪里。替换了公告（管理员才看到"发布"
  按钮）、邮箱绑定、Webhook、审批管理两个页签（改用 `el-table` 的 `#empty` 插槽）、工作台空库。
- **演示数据（KNOWN-020）**：`POST /api/admin/demo-data` + `DemoDataService`，一个事务生成
  团队 → 项目 → 4 看板列 → 3 迭代 → 15 工作项（四状态三类型，含逾期）→ 5 评论 → 6 工时
  → 3 验收（其中 2 条 PENDING 且提交人不是管理员，这样"待我处理"才有东西）→ 2 报告
  → 5 条审计 → 2 条通知。**幂等**（以项目名做标记）、**只给系统管理员**、**不碰已有数据**。
  前端 `composables/useDemoData.ts` 共用逻辑，入口在工作台空库末尾与项目列表页头。
- **自测撞到的坑**：第一版把项目 Key 写死 `DEMO`，`projects.key` 是**全局**唯一索引，
  dev 库的 demo 项目正占着它 → 一点就 500 `Duplicate entry 'DEMO'`。改为 `allocateProjectKey()`
  先读再取，并补单测钉住。
- **顺手清理**：dev 库里早期 e2e 跑挂留下的 8 条 `E2E任务*` 和一组「链路团队 / 链路项目」已按依赖顺序删除；
  同时提供一个可重复执行的 `e2e/cleanup-residue.sql`，并把"e2e 不清账号/团队/项目"登记为 KNOWN-023（Low）。
- **测试**：后端 138 → **142 项**（`DemoDataServiceTest` 4 项）。

### v2.19.1 — 工作流：配好了要看得懂，挡住了要指条路 (2026-11-23)

用户反馈六个字："工作流不知所云"。查下来是信息只在一个视角里存在。

- **BUG-101（Medium）**：
  1. `/projects/{id}/workflow` 对已配置的工作流只显示一行名字 + [编辑] [删除]，
     允许什么/禁止什么都没说；画布里又全是"节点、转换规则、终态、执行条件"这类没解释的词。
  2. 看板被挡时的提示只有「工作流不允许从「待办」转换到「已完成」」，
     不说还能往哪拖 —— 后端 `/api/workflow/transitions` 前端从来没调用过。
- 修复：设计器加只读概览（节点彩色块 + 终态标记 + 「从 → 到 → 谁可以这么做」规则表，
  条件翻成任何成员/仅管理员/仅指派人/仅报告人）+ `PageHint` 一句话说明"不配置=随便拖、
  配置后只能走这些路径、按类型分别生效、保存即生效"；保存与套用模板的提示改成有信息量的；
  看板侧接 `getAvailableTransitions`，被挡时合并成一条「…。当前可以从这里去：进行中」
  （`request.ts` 拦截器已弹过一条，必须先 `ElMessage.closeAll()` 否则叠两条）。
- 防线：`e2e/workflow-guard.mjs` 4 条 —— 只读概览可见、越界拖拽被拦且卡片回原位、
  提示里给出允许方向、允许的移动照常成功。

### v2.19.0 — 主线自证：新账号走完整条链 (2026-11-23)

这一轮不加功能：拿一个刚注册的账号，只走界面，从"建团队"走到"看报告"，
卡在哪一步就当成 bug 修。首轮 5/9 → 现在 **11/11**（`e2e/main-chain.mjs`）。

- **BUG-099（High）有资格验收的人在「待我处理」里看不到待验收**：两处口径不一致 ——
  `IssueReviewService.listPendingForUser` 的规则是"团队成员 + 非提交人 + （系统管理员 /
  项目负责人 / 团队管理员 / 报告人）"，而 `WorkbenchService.mapPendingReviews` 又按
  `projectService.listByUser`（我的项目）过滤了一遍；系统管理员、团队管理员通常不在
  项目成员表里，于是"能批的人看不到"。`getMyWorkbench` 开头的
  `if (projectIds.isEmpty()) return emptyWorkbench();` 还会让"不在任何项目里的人"整页空白。
  - 修复：两个 map 去掉"我的项目"二次过滤（权限只由 `listPendingForUser` 决定）；
    `projectName()` 现查未缓存的项目，`loadProjects` 改为可变 `HashMap`；
    "没有项目"不再整页返回空，只跳过需要项目列表的查询。
  - 防线：`WorkbenchServiceTest.shouldShowPendingReviewsFromProjectsImNotIn`；
    `main-chain.mjs` 第 8 步走工作台入口验收。
- **BUG-100（Medium）表单校验失败时 `validate()` 的 reject 没人接**：`SprintView` /
  `WorkLogTab` 都写成 `await validate()` 不接 reject —— 异常被吞（控制台 pageerror），
  而且**代码会继续往下跑**（工时弹窗校验没过照样发请求）。改为
  `const valid = await validate().catch(() => false); if (!valid) return`。
  > 更正说明：这条最初记成"点创建没有任何反应"，回查发现界面本来就有红字「请选择起止时间」，
  > 是被自己的用例（从没选日期、只断言接口）带偏的。校正过程写在该条目开头。
- **单人团队验收死锁**：验收规则要求"非提交人"，单人团队里提交人就是 owner + 报告人，
  唯一能验收的是自己 —— 任务永远停在待验收且毫无提示。详情页新增
  `.next-step-blocked` 警示（`checkReviewable()` 读团队成员）+"去邀请成员"入口。
- **e2e 可靠性**：引导遮罩的真 key 是 `localStorage.mimo_tour_state`（按主题存
  `{status, v}`），用例里之前写的 `sessionStorage` 假 key 完全无效，导致 sprint 页引导
  自动启动、遮罩吃掉点击；报告页断言前也不再读未挂载的 `body.innerText`。
- 验证：后端 `mvn test` **138 通过**；`vue-tsc` 通过；e2e `main-chain` 11/11、
  `next-step` 6/6、`chain-links` 5/5。

### v2.18.5 — 角色授权不再重复堆积 + 首屏别先下结论 (2026-11-23)

收尾时顺手数了一下 `user_roles`：89 行，而库里只有 13 个用户，每人本该一行。

- **BUG-098（High）每次启动重复插一遍角色授权**：
  1. `V11__rbac.sql` / `V12__fix_user_role_migration.sql` 用
     `INSERT IGNORE INTO user_roles … scope_id = NULL` 做幂等。唯一键
     `uk_user_role_scope` 虽然存在，但 MySQL 认为 NULL 互不相等，GLOBAL 行完全不受约束；
     而 `MigrationRunner` **每次启动都会重跑所有 `V*.sql`**，于是每启动一次 +1 行。
  2. `RoleService.assignRole` 的幂等检查 `.eq(req.getScopeId() == null, UserRole::getScopeId, null)`
     生成 `scope_id = NULL`（恒假），走接口分配同样每次插一条。
  - 修复：迁移脚本改 `INSERT … SELECT … WHERE NOT EXISTS (… scope_id IS NULL)`，`V12` 前置一段
    可重复执行的重复行清理；`assignRole` 改 `isNull` / `selectCount`。
  - **未新建迁移号**：`scope_id BIGINT NOT NULL DEFAULT 0` 的 schema 根治要占新编号，与今晚要推的
    工作流/面板那批迁移有撞号风险，改以代码层幂等覆盖两条写入路径，schema 变更后续统一排。
  - 防线：`RoleServiceTest` 新增两条断言生成 SQL 的用例（旧用例只 mock 返回值，看不到 SQL，
    所以这个 bug 一直没被拦住）；新增 `RoleMigrationScriptTest` 禁止迁移脚本再用 `INSERT IGNORE`
    写 `user_roles`。顺带关闭 KNOWN-022。
- **首屏「接下来做什么」的假结论（Medium）**：区块挂在首屏 dashboard 的加载状态上，但它依赖
  工作台的另一路请求；数据未到时 `nextUps` 为空就直接渲染"当前没有卡在你这儿的事"。
  改为加载中显示骨架屏 + "正在整理你今天该做的事…"，用例侧改为等待结论态。
- **验证**：`mvn test` **137 通过**（新增 4 条）；真实栈两次启动 `user_roles` 13 → 13 行，
  `POST /api/role/assign` 连续 3 次后分配记录仍为 1；`e2e/next-step.mjs` 6/6 连跑两次、
  `e2e/chain-links.mjs` 5/5、`vue-tsc` 通过。

### v2.18.4 — 主链路「下一步」+ 菜单/守卫权限口径统一 (2026-11-23)

v2.18.3 把断掉的线接上了，这一轮回答"接上之后每一步该点哪里"。

- **P1 工作项详情的「下一步」**：`IssueDetailDialog.vue` 顶部新增四段链路条
  `待办 → 进行中 → 待验收 → 已完成`（按 status + 最新验收记录高亮），并给出唯一主动作：
  开始处理 / 提交验收（直接拉起提交弹窗）/ 去验收处理 / 重新提交验收（带打回原因）/
  标记完成 / 查看项目报告。状态推进复用抽出的 `buildPayload()`，与「保存」同一条路径，
  看板列与 bugStatus 不会分叉；失败回滚界面状态。
  `IssueReviewPanel` 新增 `@loaded` 回传验收记录 + `defineExpose({ openSubmitDialog })`。
- **P2 工作台首屏「接下来做什么」**：`Dashboard.vue` 按"卡住别人 → 卡住自己 → 快到期"排序
  给最多三件事（被打回 / 待我处理 / 已逾期 / 进行中 / 待开始），每条直达对应工作项；
  一个项目都没有时切成三步上手清单（建项目 → 建迭代 → 建第一个任务）。
- **P3 每页一句话定位**：新增 `components/common/PageHint.vue`，为 Webhook 集成、操作审计、
  邮件发送日志、公告、日历、收件箱、邮箱绑定补上"这一页解决什么问题"；Webhook 那句如实写明
  目前只覆盖工作项新建/更新/删除三种事件。
- **BUG-097（High）菜单可见但点不进去**：菜单可见性判 `RBAC ∪ 旧字段 ROLE_ADMIN`，
  路由守卫的 `meta.requiresPermission` 却只判 RBAC；加上 `store/permission.ts` 的 `fetch()`
  在并发时直接 `return`，导致 `loaded` 仍为 false → 判无权限并弹回工作台。
  修复：守卫与后端 `AccessControlService.isSystemAdmin` 同口径（并集），`fetch()` 返回同一个
  在途 Promise。决定性对照：把 lisi 的 `users.role` 临时改成 `ROLE_ADMIN`（RBAC 仍 DEVELOPER）
  访问 `/admin/audit-logs`，旧行为 `url=/`、新行为正常渲染页面；验证后已改回。
- **验证**：新增 `e2e/next-step.mjs` 6/6 通过；回归 `e2e/chain-links.mjs` 5/5 通过；
  前端 `npm run build`（含 `vue-tsc`）通过。本轮无后端改动。

### v2.18.3 — 功能链路闭合：断链接上 / 假入口修真 / 切面 NPE 修复 (2026-11-23)

起因是一句使用感受："功能遍布很散，没有形成一股完整的链条……很多东西浮于形式"。
查下来功能都在，坏的是**页与页之间的那一步**。本轮不加新功能，只接线；证据全部来自真实浏览器。

- **BUG-093 权限切面 NPE（High）**：`PermissionAspect` 用 `@Around("@annotation(rp) || @within(rp)")`，
  两个切点用 `||` 合并时参数绑定只在命中的一支有效，另一支为 `null` → `RoleController` 的方法级注解
  走到 `rp.scopeType()` 直接 NPE，角色新增/删除/分配/撤销**四个写接口全部 500**。
  改为全限定名切点 + `resolveAnnotation()`（方法 → 实现方法 → 声明类），取不到则放行。
  新增 `PermissionAspectTest` 5 项；真实栈 `POST/DELETE /api/role/assign` 由 500 变 200。
- **BUG-094 邮件死链（High）**：`EmailNotificationService#buildActionUrl` 拼的 `/issues/{id}`
  在前端根本没有对应路由（也没有 `addRoute`），点开被 catch-all 兜到登录页/工作台。
  新增 `views/IssueRedirect.vue` + 路由 `issues/:id`：反查 projectId 后 `replace` 到
  看板并打开详情，403/404 给可执行提示。后端无需改动。
- **BUG-095 假入口（Medium）**：工作台「看板视图 / Sprint / 项目报告」三个快捷入口
  `route` 全是 `/projects`（KNOWN-014 收口）。改为 `computed`，带上「我的项目」第一个的 id。
- **BUG-096 审批通知落地（Medium）**：`handleNotifClick` 只认 `ISSUE`，其余 `push('/')`。
  现在 ISSUE → `/issues/{id}`（与邮件同一入口）、`APPROVAL_REQUEST` → `/admin/approvals?id=`，
  审批页支持 `?id=` 高亮。
- **前端菜单权限口径**：`MainLayout` 的「设置」分组从 `users.role === 'ROLE_ADMIN'` 改为
  `usePermissionStore` 逐项判权限码（旧字段仅作权限码未加载时的兜底）。
- **等级 ≠ 权限**：`UserLevel` 无任何业务消费方，页面副标题却写"权限体系"；
  改为"权益铭牌（只影响展示）" + 顶部说明条 + 跳「角色权限」入口。功能未删，只是不再误导。
- **验证**：`mvn test` 133 通过（原 128）；`npm run build` 通过；
  新增 `e2e/chain-links.mjs` 5 项真实浏览器用例全绿，同一文件在基线代码上仅 2/4（用例 1、2 必红）。

### v2.18.2 — 技术债清理：权限单一入口 / AI 参数可追溯 / 评论 @提及 (2026-11-23)

计划书 §七「技术债务修复」的收尾：三条遗留的结构性问题，都是"不修就会持续产生 bug"的欠账。

- **① 新旧权限判断并存 → `AccessControlService`**：11 处各写各的 `"ROLE_ADMIN".equals(user.getRole())`
  （公告甚至读 JWT 的授权项），同一句"是不是管理员"在 8 个 Service + 3 个 Controller 上口径不同。
  新增 `com.mimo.service.AccessControlService`，只暴露 `isSystemAdmin`（RBAC `role.manage`，V11 仅授 SUPER_ADMIN）
  与 `isTeamAdmin`（系统管理员 ∪ RBAC `team.manage` 的 TEAM 作用域 ∪ 旧字段）两个口径，两套**求并集**保证零回归，
  `mimo.access.legacy-role-fallback=false` 可切纯 RBAC。旧字段先短路，避免给每个读接口多加两次 SQL。
- **② AI 输出不可复现**：取证发现两个硬伤 —— `AiChatRequest.temperature/maxTokens` 被整段丢弃
  （`DeepSeekProvider` 硬编码 0.7/1024，Controller 里 `.temperature(0.3)` 那句注释从未生效），
  且 `AiChatResponse.model` 从没被赋值。现在 `AiProvider` 增加带采样参数的重载与 `getModel()`，
  `AiService` 每次调用记 `[AI] model=… prompt=… temperature=… maxTokens=…` 并填进响应；
  新增 `ai/prompt/PromptRegistry`（6 个提示词，`CODE@vN` + 正文指纹，温度与提示词同处定义），
  `AiController` 里 6 处内联 systemMessage 与 `buildParseIssueSystemPrompt()` 全部删除。
- **③ 通知触发点遗漏**：`CommentService` 原来只通知指派人，评论里的 `@某人` 毫无反应。
  现在解析提及（收窄字符集，`@张三，谢谢` → 「张三」，邮箱不算）→ 站内 `MENTION` 通知 + WebSocket 推送，
  跳过作者本人与刚收到「新评论」的指派人；`mapEmailType` 归入既有 `comment` 邮件偏好，不新增开关。
- **验证**：`mvn test` **128 项全绿**（+25：AccessControlService 11、PromptRegistry 7、AiService +3、
  CommentService +4）。计划书 §七 表格中「新旧权限判断并存」「AI 输出不可复现」标记已解决，
  仅「WebSocket 内存 broker」按设计留待多实例部署时处理（KNOWN-002）。

### v2.18.1 — Markdown / 邮件正文 XSS 修复 + 浏览器级取证用例 (2026-11-23)

KNOWN-011 一直写着"用 v-html 渲染用户输入、建议上 DOMPurify"，这次补了取证才发现它不只是"没防护"，
而是**现有的正则黑名单本身能被绕过**。

- **BUG-092 [High] 存储型 XSS**：`utils/markdown.ts` 的 `sanitize()` 是 8 条正则黑名单
  （删 `<script>`、删带引号的 `on\w+="…"`、删 `javascript:`）。用真实 Chromium 实测：
  `<details open ontoggle=…>` **直接执行**（事件处理器不带引号就不匹配），
  `[点我](jav&#x61;script:…)` 实体解码后留下可点的 `javascript:` 链接，
  mXSS 载荷（`<math><mtext><table><mglyph><style>…`）把 `<style>` 留在 DOM 里。
  受影响面也比原记录的 2 处大 —— 实际 **8 处 `v-html`**，其中 `EmailInbox.vue` 另抄了一份更弱的正则清洗
  （连 `javascript:` 都不删），而邮件正文来自团队外发信人。
- **修复**：`sanitizeHtml()` 改用 DOMPurify（真实 DOM 解析 + 白名单重建），Markdown 与邮件正文共用同一个出口；
  `dompurify` 由 `jspdf`/`html2pdf.js` 的间接依赖提为直接依赖（`^3.4.8`）。
- **新增用例 `e2e/xss-markdown.mjs`**：不需要后端/前端服务，Playwright 起空白页 + 真实 Chromium，
  把仓库里真实的 `markdown.ts` 用 esbuild 转译后原样执行，渲染结果按 `v-html` 的方式注入 `innerHTML`，
  用 `window.__xssFired` 判断脚本**是否真的执行**（不是 grep 关键词）。
  用例自带"修复前基线"管线（`git show HEAD:…markdown.ts`），修复前 6/10、现在 10/10，基线不进退出码。
- **顺手校对过期 KNOWN**：复核发现 6 条早就修好却没标（001 `@PreAuthorize` 已整体移除、
  003 上传目录已可配、008 快速建 Sprint 已可配、010 默认列名已可配、012 已无 `window.location.href`、
  015 取消审批已有 `reason === null` 提前返回），未关闭 KNOWN 15 → 8；统计口径与三处数字不一致一并说明。
- **验证**：`node e2e/xss-markdown.mjs` 10/10 PASS；`npm run build`（vue-tsc + vite）通过，
  markdown 共享块 69.6 kB / gzip 23 kB。

### v2.18.0 — KNOWN 遗留四连修：编号冲突 / 团队孤儿 / 审批补偿 / 更新端点 (2026-11-23)

按 bug-log「已知未修复」清单里 Medium/High 的条目批量收口，后端测试 83 → 103 全绿。

- **KNOWN-004 任务编号并发冲突**：新增 `IssueKeyAllocator`，把「取号 + 插入」收成一个可重试单元，
  撞 `uk_issue_key` 就重新取号（最多 5 次），超限返回 `409 任务编号分配冲突`，
  不再把 `DuplicateKeyException` 抛成 500。`IssueService.create()` 改为调用它。
- **KNOWN-005 删团队留孤儿项目**：`TeamService.deleteTeam()` 先遍历团队下的项目逐个
  `projectService.deleteProject()`（复用既有级联：看板列/任务/项目成员/报告/Sprint），再删成员与团队。
  此前项目失去团队后谁都看不见也删不掉（`listByUser` 按团队过滤）。
- **KNOWN-006 审批执行无补偿**：审批结果与执行结果拆成两个状态，`approval_requests` 新增
  `exec_status` / `exec_error` / `exec_attempts` / `last_exec_at`（`V24`）。
  执行挪进 `ApprovalExecutionService` 并用 `REQUIRES_NEW` 独立事务——失败只回滚执行自己，
  不再出现「项目建了一半」；失败原因落库、日志走 SLF4J，新增补偿入口
  `POST /api/approvals/{id}/retry-execution`（管理员，仅「已通过且未成功」可重试）。
- **KNOWN-009 缺更新端点**：补 `PUT /api/teams/{id}`（管理员）、`PUT /api/projects/{projectId}`（管理员）、
  `PUT /api/sprints/{id}`（成员，日期不可倒挂、已结束不可改）、`PUT /api/comments/{id}`（仅作者）。
  项目 **Key 刻意不可改**（任务编号前缀就是它），评论不做管理员代改。前端编辑 UI 尚未接入。
- **测试**：新增 `IssueKeyAllocatorTest`(3) / `TeamServiceTest`(4) / `ApprovalServiceTest`(6) /
  `CommentServiceTest`(3) / `SprintServiceTest`(4)，共 20 条；统计口径上「已知未修复 KNOWN」19 → 15。

### v2.17.0 — 新手引导重做：分主题 + 锚点自解析 + 干净退出 (2026-11-20)

用户反馈"引导太简陋而且有逻辑问题"。用真实浏览器把引导的完整生命周期跑了一遍，
确认并修掉 4 个逻辑问题（BUG-078~081），另登记 2 个文档/工具链问题（BUG-082~083）。

- **分主题**：`welcome`（快速上手 5 步）/ `board`（看板实操 6 步）/ `collab`（协作与治理 6 步），
  覆盖顶栏、Ctrl+K、侧边栏、项目切换、AI 助手、看板列/卡片/筛选/计时、Sprint 健康度、交付分析、知识库、验收与变更流程。
  旧版只有 3 步，且第 3 步锚点在「创建任务」按钮上却讲 Sprint（BUG-078）。
- **锚点自解析**：每步用选择器或按文案定位真实元素，找不到就跳过该步；整场都没有锚点时提示"当前页面没有可引导的内容"。
  不再让 driver.js 退化到屏幕中央的 dummy 元素上讲错位置（BUG-081）。
- **页面守卫**：`board` 要求路径含 `/board`、`collab` 要求 `/projects/`，不满足只提示、不启动、也不记为已完成。
- **干净退出**：完成 / 跳过 / Esc / 关闭 / 路由切走统一走 `safeDestroy()`，
  清掉 `driver-active`/`driver-fade`、残留遮罩与 `pointer-events`（BUG-079：以前残留会让整站点不动）。
- **状态按主题记录**：`mimo_tour_state = { topic: { status: 'done'|'skipped', v, at } }`，
  跳过不等于完成，入口常驻（账号菜单三个主题都可重播）；旧的 `mimo_tour_completed` 自动迁移（BUG-080）。
- **自动触发更克制**：按路由选主题、等锚点渲染、同一会话只自动弹一次，路由来回切换不再反复骚扰。
- **用例**：新增 `e2e/tour.mjs` 9 条断言，覆盖自动触发 / 跳过 / 页面恢复可点 / 入口常驻 / 重播 /
  Esc 收尾 / 错页面提示 / 走完记 done，当前 9/9 PASS。

### v2.16.2 — Wiki 目录越权修复 + 请求参数校验 (2026-11-20)

KNOWN-017 的"越权场景未验证"补齐后首次运行即发现跨租户泄漏。

- **BUG-076 [High]**：`GET /api/wiki/tree?projectId=` 只校验"项目存在"，不校验项目成员，
  任何登录用户换个 projectId 就能列出别人项目的知识库目录（标题/作者/时间）。
  改为 `getTree(projectId, userId)` + `requireMember()`，Controller 传入当前用户。
- **BUG-077 [Medium]**：`GlobalExceptionHandler` 只接管了少数异常，缺参数、参数类型不对、
  请求体不是合法 JSON、`@Valid` 失败全部落到兜底分支返回 500「服务器内部错误」。
  现在分别返回 400 + 具体原因（`缺少必需参数: q` / `参数类型不正确: id`）。
- **新增用例**：`e2e/authz.mjs`（接口级，25 条断言）——注册一个不在团队里的外人账号，
  打 23 个项目/团队/Sprint/任务/管理端读接口，全部 403；同时断言成员自己访问仍为 200、
  缺参数返回 400。另加 `WikiServiceTest` 2 项、`GlobalExceptionHandlerTest` 3 项，
  后端测试 78 → 83 全绿。

### v2.16.1 — AI 降级链路修复（无 Key 场景）+ 前端解包修正 (2026-11-20)

KNOWN-018「未配置 AI Key 时的用户提示未验证」实测后发现降级路径不是降级，而是把错误当成结果。

- **后端契约修正**：新增 `AiUnavailableException`（message 面向用户，细节只进日志）、
  `AiProvider.isConfigured()`、`AiChatResponse.error` 标记。provider 未配置/调用失败一律抛异常
  （流式则推送同一句说明后结束），不再把错误说明当内容返回。
- **润色不再写坏正文**：`POST /api/ai/polish` 出现 `hasError()` 时返回 `503`；
  此前它返回 `200` + 错误文案，前端点「应用」会把任务描述整段替换成错误提示（BUG-072）。
- **解析类接口不再泄漏内部信息**：`parse-issue` / `suggest-tasks` 的 500 消息改为通用中文文案，
  上游原文只进日志；`chatJson` 复用静态 `ObjectMapper`；新增 `ResultCode.SERVICE_UNAVAILABLE(503)`。
- **用量只在成功时计数**：`chatWithQuota` / `chatStreamWithQuota` 先判 `isConfigured()`，
  未配置或失败不扣当日配额（此前失败也 +1）。
- **前端 `api/ai.ts` 解包修正**：axios 拦截器已返回 `Result` 本体，6 处 `res.data?.data`
  多解一层导致 AI 面板 Provider 永远 `Unknown`、润色返回空串、优先级永远落回 MEDIUM（BUG-074）。
- **AI 面板提示条**：`/api/ai/usage` 新增 `configured`，面板顶部直接提示"后端未配置模型 Key"，
  不让用户点一次失败一次。
- **补回死代码入口**：`IssueDetailDialog` 的「AI 润色描述」按钮此前从未渲染（BUG-075），
  现在放在基本信息页签，带结果预览卡（取消 / 应用到描述），三种工作项类型统一生效。
- 测试：新增 `AiServiceTest` 7 项（未配置标记、失败不扣配额、流式推送说明、chatJson 不泄漏原文），
  后端 71 → 78 全绿；新增 `e2e/ai-degrade.mjs` 3 条浏览器断言，3/3 PASS。

### v2.16.0 — 看板「进行中」自动计时 (2026-11-20)

需求："看板只要在进行中就计时。"实现上没有引入定时任务、也没有往 work_logs 写流水，而是**状态驱动**：

- **V23 迁移**（`V23__issue_progress_timer.sql`）：`issues` 新增
  `in_progress_started_at`（本轮开始时间，NULL=未计时）与 `in_progress_seconds`（已累计秒数），
  并给存量"进行中但没开始时间"的任务用 `updated_at` 兜底。
- **计时规则**（`com.mimo.util.IssueTimerUtil`）：
  进入 `IN_PROGRESS` 写 startedAt；离开（完成/回退/关闭）把 `now - startedAt` 累加进 seconds 并清空 startedAt；
  状态没变化（如同列排序）不动。`BoardService.moveIssue` 与 `IssueService.updateInternal` 都接了同一个入口。
- **不需要后端 tick**：数据库只存"开始时间 + 已累计"，前端用 1s 的全局单例 ticker
  （`composables/useIssueTimer.ts`）算 `已累计 + 本轮已跑`，刷新页面/换设备/多人同看都不丢时间。
- **看板展示**：`IssueCard.vue` 右下角计时胶囊——进行中为蓝色并闪烁，离开进行中后显示灰色累计时长，
  hover 显示"进行中，本轮已计时 mm:ss"。
- **接口字段**：`IssueVO` 新增 `inProgressStartedAt` / `inProgressSeconds`（注意 `spring.jackson.default-property-inclusion: non_null`，未计时时 startedAt 字段会整个不出现，前端一律用可选读取）。
- 单测：`IssueTimerUtilTest` 5 项（开始/累加并清空/再次进入保留累计/状态不变不动/总时长含本轮）。
  后端测试 66 → 71 全绿。

### v2.15.8 — 信息架构重构：三层导航 + 项目内导航 (2026-11-20)

真实浏览器审计（25 个页面 + 7 条核心流程，脚本见工作区 `toolchain/e2e/`）后修复的问题：

- **断链修复**：`ProjectOverview` 里 `request.get('/api/reports/stats/:id')` 与 axios `baseURL: '/api'`
  叠加成 `/api/api/reports/stats/:id` → 404，项目概览右上角直接弹「Not Found」；改为 `/reports/stats/:id`。
- **断链修复**：`Webhooks.vue` 用 `GET /projects` 拉项目下拉，后端无此接口（405），
  「生效范围=项目」下拉永远为空；改为 `/projects/my`。
- **导航重构**：左侧菜单从 13 项平铺改为四组 —— 我的工作（工作台/日历/公告/收件箱/邮箱绑定）、
  项目（项目列表）、团队（团队管理）、设置（等级/角色/审批/审计/Webhook/邮件日志，仅管理员）。
  个人设置移到右上角头像菜单。
- **项目内导航**：进入项目后左侧切换为 概览/看板/迭代/时间线/报表/知识库/交付分析/工作流/故事地图，
  带「全部项目」返回与当前项目名；`activeMenu` 按最长前缀匹配，燃尽图等深层路由也能高亮。
- **入口修正**：项目列表卡片点击改为进入项目概览（原来直接跳看板，概览页没有入口）；
  项目概览 hero 只保留动作按钮（新建工作项/日程/导入/导出），跳转类收进侧边栏。
- **引导修正**：driver.js 首次引导新增「跳过引导」，之前遮罩拦截点击且无跳过入口，用户会以为页面卡死。

验证：`npm run build` 通过；Playwright 冒烟 25 页全 PASS、0 控制台告警；
断言全局侧边栏 4 组、项目内导航 9 项、跳过引导后遮罩数为 0、项目概览无错误提示。

### v2.15.7 — 前端产物体积优化：Element Plus / ECharts 按需引入 + html2pdf 懒加载 (2026-11-20)

- `vite.config.ts` 接入 `unplugin-vue-components` + `ElementPlusResolver({ importStyle: false })`，`el-*` 组件与 `v-loading` 指令按需 import；样式仍由 `main.ts` 统一引入 `element-plus/dist/index.css`。
- `main.ts` 移除 `app.use(ElementPlus)`；中文语言包改由 `App.vue` 的 `<el-config-provider :locale="zhCn">` 提供；图标仍全量注册（LevelManage / Profile 用字符串图标名 `<component :is="'Bell'" />`，依赖运行时解析）。
- 新增 `src/utils/echarts.ts`：`echarts/core` 只注册 Line/Bar/Pie/Custom/Graph + Grid/Tooltip/Legend/Title/MarkLine/AxisPointer/VisualMap + CanvasRenderer，6 个图表组件统一改从 `@/utils/echarts` 导入；**新增图表类型必须在此注册**。
- `ReportList.vue` 导出 PDF 改为 `await import("html2pdf.js")`，约 960 kB 改为首次导出时下载。
- 实测（生产构建 kB）：入口 1227.8 → 414.9（−66%）；ECharts 共享块 1011.3 → 617.0（−39%）；ReportList 971.3 → 11.0（html2pdf 拆为 960 kB 懒加载块）；JS 总量 3842 → 3171（−17%）；CSS 519 不变。
- 修复：`BoardService.getBoard` 对 `column_id = null` 的任务抛 `NullPointerException: element cannot be mapped to a null key` 导致看板接口 500（空列任务改归首列 + `sortOrder` 空值排序保护），后端测试 64 → 66；`MainLayout.vue` 用了不存在的 `<Inbox />` 图标改 `Postcard`；`el-tag` 传 `type=""` 与 12 处模板类型不匹配统一修正（`components.d.ts` 生效后 `vue-tsc` 会检查 Element Plus 组件 prop 类型）。
- 验证：`npm run build` 通过；Playwright 冒烟 25 个页面全 PASS、0 控制台告警。

### v2.15.6 — 权限与变更治理加固 (2026-11-12)

- Sprint 详情、Sprint 列表、燃尽图、统计接口增加项目团队成员校验。
- Team 详情和成员列表增加团队成员/管理员校验，阻止跨团队读取。
- Project、Board、Issue、Comment、Attachment、Gantt、WorkLog、Report 核心读取入口统一接入 `ProjectAccessService`。
- Issue 全局查询按用户可访问项目集合过滤，避免跨团队返回数据。
- 审计日志全量查询、邮件发送日志、Webhook 配置与投递日志增加系统管理员校验。
- 数据导入导出由“仅登录校验”改为项目访问控制，防止跨项目导出和写入。
- Issue 历史动态仍允许项目成员按所属工作项查看。
- 需求变更审批支持显式清空截止日期、故事点、Sprint、排期、依赖、验收标准及可选需求字段。
- 空字段清理使用 `LambdaUpdateWrapper.set(..., null)`，避免 MyBatis-Plus 默认忽略 null 导致更新不生效。
- 移除文档中已经解决的 `hasRole('ROLE_ADMIN')`、Redis 未使用和上传目录未配置等过期已知问题。
- 新增 Sprint/Team/Project/管理接口越权访问测试与空字段治理相关回归，当前测试总数为 22。

### v2.15.5 — AI RAG 项目知识检索 (2026-11-12)

Phase 3 D02 落地：项目知识问答具备检索、引用、拒答和评测闭环。

**Backend**:
- V22 新增 `rag_documents`，按项目/来源/chunk 建立索引。
- 新增 `POST /api/ai/rag/index/{projectId}`、`POST /api/ai/rag/ask`、`POST /api/ai/rag/evaluate`。
- 中文使用单字 + 二元词，英文使用完整词项，执行 BM25 风格排序和来源多样性限制。
- 无证据时直接拒答且不调用 AI；AI 失败时回退为检索原文摘要。
- 回答强制引用 `[编号]`，并返回可点击的 `sourceUrl`。
- 评测支持 Recall@K、引用正确率和无依据回答率。

**Frontend**:
- 全局 AI 面板新增「项目知识库」模式。
- 支持重建项目索引、知识库问答和可点击引用。
- Wiki 支持 `?page={id}` 深链打开引用页面。

### v2.15.4 — AI Sprint 规划助手 (2026-11-12)

Phase 3 N5 落地：为规划中的 Sprint 提供可解释、可人工调整的 Backlog 推荐。

**Backend**:
- 新增 `POST /api/ai/suggest-sprint-plan`。
- 新增 `POST /api/sprints/{sprintId}/apply-plan`。
- 推荐读取项目 Backlog、依赖就绪度、优先级、截止日期、故事点和历史 Velocity。
- AiService 不可用或返回无效结果时自动使用确定性排序建议。
- apply-plan 只接受用户显式提交的 issueIds，并在写入前重新校验 Sprint 状态、项目归属、完成状态和其他 Sprint 占用。

**Frontend**:
- Sprint 规划卡片增加「AI 规划」入口。
- 规划抽屉支持容量调整、重新生成、候选勾选、理由/依赖/超容量提示。
- 推荐默认勾选但绝不自动写入；用户点击「加入选中任务」后才提交。

### v2.15.3 — Velocity 与累积流图 (2026-11-12)

Phase 3 N4 落地：补充 Sprint 历史交付节奏与每日状态流动分析。

**Backend**:
- V21 新增 `delivery_daily_snapshots`。
- 新增 `GET /api/analytics/project/{projectId}/velocity`。
- 新增 `GET /api/analytics/project/{projectId}/cfd`。
- Velocity 以首日燃尽快照计算承诺点，并区分当前完成点；平均值仅统计已结束 Sprint。
- CFD 按项目、Sprint、日期和四类状态聚合；存在待验收记录时优先计入 `PENDING_REVIEW`。
- 任务移动、验收变更和 CFD 读取都会刷新当天快照。

**Frontend**:
- 新增 `/projects/:id/analytics` 交付分析页和项目概览入口。
- 新增 Velocity 双柱状图、平均速度标识和 Sprint 明细表。
- 新增 CFD 堆叠面积图，支持 Sprint 和日期范围筛选。

### v2.15.2 — Sprint 健康度分析 (2026-11-12)

Phase 2.1 N3 落地：Sprint 执行过程中提供确定性、可解释的健康度判断。

**Backend**:
- 新增 `GET /api/sprints/{id}/health` 和 `POST /api/sprints/{id}/health/ai-summary`。
- 计算时间进度、工作进度、进度差、剩余故事点、预计延期、逾期、阻塞和待验收数量。
- 无故事点时自动回退到任务数量计算工作进度。
- 健康分数与 `HEALTHY` / `WATCH` / `AT_RISK` 等级由进度差、逾期、阻塞、验收和延期共同决定。
- AI 解读失败时自动返回确定性建议，基础健康度不受影响。

**Frontend**:
- Sprint 详情新增健康卡、双进度条、指标网格、可展开风险原因和建议动作。
- 订阅看板 WebSocket 事件实时刷新，并保留 60 秒兜底刷新。
- AI 解读按钮按需调用，失败时在卡片内回退提示。

### v2.15.1 — 个人工作台 (2026-11-12)

Phase 2.1 N2 落地：成员登录后通过统一入口处理跨项目个人工作。

**Backend**:
- 新增 `GET /api/workbench/my` 聚合接口和 `WorkbenchService`。
- 跨项目汇总本人未完成任务，按截止日期升序、优先级降序排列。
- 逾期或存在未完成前置依赖时标记高风险；高优先级且两天内到期时标记需关注。
- 聚合待审批变更、待验收任务、本人被驳回事项。
- 本周工时按周一至周日聚合；当前 Sprint 投入按本人任务的故事点和工时计算。
- 所有聚合结果限定在用户可访问项目和本人数据范围内。

**Frontend**:
- 首页升级为个人工作台，增加「全部 / 今日到期 / 已逾期」任务筛选。
- 增加待我处理、最近被驳回、本周总结和当前 Sprint 投入面板。
- 任务、审批、验收和驳回事项支持一键跳转；无数据时展示独立空状态。

### v2.15.0 — 需求变更影响分析 (2026-11-12)

Phase 2.1 N1 落地：审批需求变更前形成可解释的影响分析。

**Backend**:
- 新增 `GET /api/issue-change-requests/{id}/impact` 只读接口。
- `IssueImpactService` 沿 `parent_id` 收集子任务，沿 `dependencies` 反向遍历下游依赖，并识别新增前置工作项。
- 返回受影响工作项数、任务数、已完成数、Sprint 列表、故事点差、日期差和风险原因。
- 风险分级：活跃 Sprint 工作量增幅超过 20% 或已完成项存在返工风险时为 HIGH；范围、排期、依赖或验收标准变化时为 MEDIUM；低影响字段调整为 LOW。
- 影响分析不写入业务数据，历史变更申请仍可查看。

**Frontend**:
- 变更卡片新增「影响分析」入口。
- 展示风险等级、汇总指标、Sprint 与日期范围、风险原因和受影响工作项。
- 支持跳转到受影响工作项看板及 Sprint 燃尽图。

### v2.14.0 — Issue 版本治理 + 变更审批 + 任务验收 (2026-11-08)

Phase 2.1 P0 落地：工作项从创建到完成具备版本、变更和验收闭环。

**Backend**:
- V20 迁移新增 `issue_versions` / `issue_change_requests` / `issue_reviews`。
- Issue 创建自动生成 v1；直接编辑自动递增版本。
- 已进入 Sprint 的 Issue 禁止直接修改需求字段，必须提交变更申请。
- 变更申请支持字段 Diff、`baseVersion` 乐观锁、通过/驳回/撤回、禁止自审、通知与审计。
- 任务验收支持多轮提交、证据链接、通过/打回、禁止自验收；打回后自动回到进行中列。
- 完成列增加验收门禁：存在未通过的验收轮次时禁止移入完成/关闭列。
- `WorkflowService` 真正执行 `ASSIGNEE` / `REPORTER` / `ROLE_ADMIN` 转换条件。
- 修复 Issue Key 生成未包含软删除记录导致的唯一键冲突。

**Frontend**:
- `IssueDetailDialog` 新增「版本与变更」「验收」页签。
- 支持版本历史、任意版本 Diff、变更申请、审批/驳回/撤回。
- 支持提交验收、查看证据和多轮验收记录。

### v2.10.0 — Configurable Workflow Engine + Security Hardening + ESLint/Prettier (2026-06-24)

Phase 2 start: configurable workflow engine replacing hardcoded 3-state kanban. Security hardening for sensitive config. Frontend tooling added.

**Backend — Workflow Engine**:
1. **Entity**: `Workflow.java` — `workflows` table (projectId, issueType, name, config JSON, isActive, isDefault)
2. **DTO**: `WorkflowDTO.java` — `WorkflowNode` (columnId/name/color/isTerminal), `WorkflowTransition` (fromColumnId/toColumnId/conditions), `WorkflowConfig` (nodes+transitions), `SaveWorkflowRequest`, `WorkflowVO`, `AvailableTransitionVO`
3. **Mapper**: `WorkflowMapper.java` — MyBatis-Plus BaseMapper
4. **Service**: `WorkflowService.java` — CRUD + state machine validation:
   - `saveWorkflow()` — create/update with config JSON serialization + node validation
   - `validateTransition(projectId, issueType, fromColumnId, toColumnId)` — checks if transition is allowed by workflow rules
   - `getAvailableTransitions(projectId, issueType, currentColumnId)` — returns valid target columns
   - `applyTemplate(projectId, issueType, templateName)` — 3 presets: Scrum (bidirectional), Kanban (forward-only), Bug (forward-only)
   - Backward compatible: no workflow defined → all transitions allowed
5. **Controller**: `WorkflowController.java` — 6 endpoints:
   - `GET /api/workflow/project/{projectId}` — list all workflows
   - `GET /api/workflow/project/{projectId}/type/{issueType}` — get active workflow for type
   - `POST /api/workflow` — create or update workflow
   - `DELETE /api/workflow/{workflowId}` — delete workflow
   - `POST /api/workflow/template` — apply preset template
   - `GET /api/workflow/transitions` — get available transitions for current state
6. **BoardService integration**: `moveIssue()` now calls `workflowService.validateTransition()` before updating issue column — invalid transitions throw `BusinessException` with descriptive message
7. **DB migration**: `V10__workflows.sql` — creates `workflows` table with unique constraint on (project_id, issue_type)
8. **MigrationRunner**: Updated to include V10 script

**Backend — Security Hardening**:
- `application.yml`: `datasource.password` changed from hardcoded `mimo123` → `${MIMO_DB_PASSWORD:}`
- `application.yml`: `datasource.username` changed from hardcoded `mimo` → `${MIMO_DB_USERNAME:mimo}`
- `application.yml`: `jwt.secret` default value removed — now `${JWT_SECRET:}` (no hardcoded fallback)
- All sensitive config now reads from environment variables, same pattern as DeepSeek API Key

**Frontend — Workflow Designer**:
1. `api/workflow.ts` — 6 API functions + TypeScript interfaces
2. `views/WorkflowDesigner.vue` — Visual workflow designer:
   - Issue type selector (STORY/TASK/BUG)
   - Template selector (Scrum/Kanban/Bug tracking)
   - Drag-and-drop board columns into workflow nodes
   - Transition rule editor (from → to + conditions: ROLE_ADMIN/ASSIGNEE/REPORTER)
   - Terminal node marker
   - Save/delete/apply template actions
3. Router: `projects/:id/workflow` route added
4. `ProjectOverview.vue`: "工作流" button added to hero actions

**Frontend — Tooling**:
1. ESLint 8 + Prettier 3 installed and configured
   - `.eslintrc.cjs` — Vue 3 + TypeScript recommended rules, Prettier integration
   - `.prettierrc` — 100 char width, single quotes, no semicolons, trailing commas
   - `.prettierignore` — dist, node_modules, assets
2. `package.json` scripts: `npm run lint` (eslint --fix), `npm run format` (prettier --write)
3. Fixed 2 ESLint errors: `no-constant-condition` in `ai.ts`, unused `index` var in `ProjectBoard.vue`

**Files created (7)**:
- Backend: `Workflow.java`, `WorkflowMapper.java`, `WorkflowDTO.java`, `WorkflowService.java`, `WorkflowController.java`, `V10__workflows.sql`
- Frontend: `workflow.ts`, `WorkflowDesigner.vue`, `.eslintrc.cjs`, `.prettierrc`, `.prettierignore`

**Files modified (6)**:
- Backend: `application.yml`, `MigrationRunner.java`, `BoardService.java`
- Frontend: `package.json`, `router/index.ts`, `ProjectOverview.vue`

### v2.11.0 — Fine-grained RBAC: User-Role-Permission Model + Button-level Access Control (2026-08-20)

Phase 2 §4.2: Replace single-string `User.role` with a three-layer RBAC model (User → Role → Permission) supporting GLOBAL/TEAM/PROJECT scoped role assignments and button-level permission codes.

**Backend — RBAC Core**:
1. **Entities**: `Permission.java`, `Role.java`, `RolePermission.java`, `UserRole.java`
2. **Mappers**: `PermissionMapper` (custom SQL joins user_roles + role_permissions to find user permissions at scope), `RoleMapper`, `RolePermissionMapper`, `UserRoleMapper`
3. **Service**: `RoleService.java` —
   - Role CRUD (system roles protected from deletion)
   - `replaceRolePermissions()` — atomic permission reassignment
   - `hasPermission(userId, code, scopeType, scopeId)` — SUPER_ADMIN bypasses all checks
   - `assignRole()` / `revokeRole()` — idempotent scoped assignment
4. **Annotation**: `@RequirePermission(value, scopeType, scopeId)` — supports SpEL for scope resolution
5. **AOP**: `PermissionAspect.java` — intercepts `@RequirePermission` annotations, SpEL evaluates scope, falls back to `RoleService.hasPermission()`
6. **Controller**: `RoleController.java` — 8 endpoints:
   - `GET /api/role` — list all roles
   - `GET /api/role/{id}` — get role detail
   - `GET /api/role/permissions` — list all 45 permission points
   - `POST /api/role` — create/update role (with `@RequirePermission("role.manage")`)
   - `DELETE /api/role/{id}` — delete non-system role
   - `POST /api/role/assign` — assign role to user with scope
   - `DELETE /api/role/assign` — revoke role
   - `GET /api/role/user/{userId}` — user's role list
   - `GET /api/role/me/permissions` — current user's permission codes (for frontend button control)
7. **DB migrations**: `V11__rbac.sql` + `V12__fix_user_role_migration.sql`
   - 4 tables: `permissions`, `roles`, `role_permissions`, `user_roles` (scoped)
   - 45 preset permission points across 8 modules (project/issue/sprint/board/report/team/system/wiki)
   - 7 preset roles: SUPER_ADMIN / PROJECT_MANAGER / PRODUCT_OWNER / DEVELOPER / TESTER / VIEWER / REPORTER
   - Role-permission mappings for all system roles
   - V12 fixes V11's migration condition (User.role stored as `ROLE_ADMIN` with prefix, not `ADMIN`)
8. **Dependency**: `spring-boot-starter-aop` added to `pom.xml`

**Frontend — RBAC Integration**:
1. `api/role.ts` — 8 typed API functions + TypeScript interfaces
2. `store/permission.ts` — Pinia store managing current user's permission codes:
   - `fetch()` pulls `GET /role/me/permissions`
   - `has(code)` / `hasAny(codes[])` — SUPER_ADMIN auto-bypasses (codes.length >= 45)
   - `reset()` on logout
3. `directives/permission.ts` — `v-permission` directive removes element if user lacks permission
4. `composables/usePermission.ts` — reactive `has()` / `hasAny()` / `isSuperAdmin`
5. `main.ts` — registers `v-permission` directive globally
6. `store/user.ts` — login triggers `permissionStore.fetch()`, logout calls `reset()`
7. `router/index.ts` — route guard supports `meta.requiresPermission`, blocks access and redirects to Dashboard
8. `views/RoleManage.vue` — two-tab page:
   - Roles tab: searchable list with avatar/description/permission count; create/edit dialog with grouped permission checkbox selector (select-all per module); delete (non-system only); permission viewer drawer
   - Permissions tab: all 45 permissions grouped by module in card grid
9. `components/layout/MainLayout.vue` — adds "角色权限" menu item under admin section

**Verification**:
- Backend compiles clean (`mvn clean compile` exit 0)
- V11 + V12 migrations executed successfully on startup
- API test: `admin` user → 45 permissions (SUPER_ADMIN), `zhangsan` user → 14 permissions (DEVELOPER)
- Frontend `vue-tsc --noEmit` exit 0, `vite build` success (RoleManage chunk 8.34 kB)

**Files created (13)**:
- Backend: `Permission.java`, `Role.java`, `RolePermission.java`, `UserRole.java`, `PermissionMapper.java`, `RoleMapper.java`, `RolePermissionMapper.java`, `UserRoleMapper.java`, `RoleService.java`, `RoleController.java`, `RequirePermission.java`, `PermissionAspect.java`, `V11__rbac.sql`, `V12__fix_user_role_migration.sql`
- Frontend: `role.ts`, `permission.ts`, `directives/permission.ts`, `usePermission.ts`, `RoleManage.vue`

**Files modified (6)**:
- Backend: `pom.xml`, `MigrationRunner.java`
- Frontend: `main.ts`, `store/user.ts`, `router/index.ts`, `MainLayout.vue`

### v2.11.1 — Multi-scope Stacked Permission: PROJECT 校验自动叠加 TEAM (2026-08-20)

补充 v2.11.0 的 scope 叠加能力，落地用户场景："测试组 a/b 在不同项目，既有组级测试权限，又有各自项目权限"。

**问题**：v2.11.0 的 `findByUserAndScope(userId, scopeId)` 在 PROJECT 校验时只匹配 `scope_id=projectId` 的 PROJECT 行，查不到 TEAM 行（TEAM 行 scope_id 是 team_id，不等于 projectId），导致测试组通用权限在项目内无法生效。

**设计确认**（与用户对齐）：
- 测试组 = Team 实体（teams 表一行），team_id 实存
- a 在项目 B 未分配 PROJECT 角色时拒绝访问
- TEAM scope 角色权限**仅对该组所属项目生效**（不会跨组）

**改造 — 三层叠加 SQL**：
- `PermissionMapper.findByUserForProject(userId, projectId)` 新方法 — LEFT JOIN projects 取 `team_id`，叠加查询：
  - GLOBAL scope 角色权限
  - TEAM scope 且 `scope_id = project.team_id`（仅本组项目生效）
  - PROJECT scope 且 `scope_id = projectId`
- `RoleService.hasPermission()` 分支：
  - `PROJECT` scope → 调用 `findByUserForProject`（三层叠加）
  - `TEAM` scope → 调用 `findByUserAndScope`（GLOBAL + TEAM）
  - `GLOBAL` scope → 调用 `findByUserAndScope(scopeId=0L)`（仅 GLOBAL）

**用户角色分配 — 后端**：
- 新增 DTO：`RoleDTO.UserRoleAssignmentVO` — 含 role 信息 + scope 信息（userRoleId/userId/scopeType/scopeId/assignedAt）
- 新增 Service：`RoleService.listUserAssignments(userId)` — 返回带 scope 的全量分配记录
- 新增端点：`GET /api/role/user/{userId}/assignments`

**前端 — RoleManage 新增"用户角色分配"Tab**：
1. 用户搜索（el-select 远程搜索，复用 `searchUsers`）
2. 已分配角色列表：展示 role + scope + scopeId（用 teamOptions/projectOptions 把 id 翻译成名称）
3. 分配新角色表单：选 role + scopeType（GLOBAL/TEAM/PROJECT 单选按钮）+ scopeId 下拉（按 scopeType 切换 teamOptions/projectOptions，GLOBAL 时隐藏）
4. 撤销按钮：调用 `revokeRole(userId, roleId, scopeType, scopeId)`，精准定位单条分配

**验证**：
- 后端 `mvn clean compile` exit 0
- 前端 `vue-tsc --noEmit` exit 0、`vite build` 成功（RoleManage chunk 增至约 12 kB）

**Files modified (4)**:
- Backend: `PermissionMapper.java`, `RoleService.java`, `RoleController.java`, `RoleDTO.java`
- Frontend: `api/role.ts`（新增 `UserRoleAssignmentVO` + `listUserAssignments`）、`views/RoleManage.vue`（新增 Tab + 函数）

### v2.13.8 — Wiki 增强：目录拖拽排序 + 版本 Diff 对比 + 图片粘贴上传 (2026-08-24)

v2.13.7 Wiki 首版的体验补强，落地完善计划 §4.3 标注的三项差距。

**一、目录拖拽排序**（[WikiService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/WikiService.java) `movePage`，[WikiController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/WikiController.java) `PUT /api/wiki/pages/{id}/move`）：
- 参数：`targetId` + `position`（`before`/`after`/`inner`），inner 时为目标节点的子节点追加到末尾，before/after 参照兄弟节点
- 逻辑：加载新父级全部兄弟 → 从列表移除自身 → 按位置插入 → 压实 `sort_order` 0..n-1 全量落库
- **坑**：MyBatis-Plus `updateById` 默认忽略 null 字段，`parentId=null`（移回根级）不生效 → 改用 `LambdaUpdateWrapper.set()` 显式更新
- 前端：[Wiki.vue](file:///e:/桌面/Mimo/frontend/src/views/Wiki.vue) el-tree 加 `draggable` + `allow-drop` + `@node-drop`，拖拽后调用 move API 重载树

**二、版本 Diff 对比**（[utils/diff.ts](file:///e:/桌面/Mimo/frontend/src/utils/diff.ts)）：
- LCS 行级 diff 算法（自底向上 DP + 回溯），零依赖
- 版本历史抽屉新增「版本对比」弹窗：A（可选"当前内容"）→ B 两个下拉选版本，实时渲染 diff（+ 绿 / - 红 / 空行），无差异时提示

**三、图片粘贴上传**：
- 后端：`GET /api/wiki/attachments/{id}/download` 新增可选 `?token=` query 认证（`<img>` 无法携带 Authorization header），SecurityConfig 放行该路径，controller 内 `resolveUserId` 优先 header、其次解析 query token；无效 token 返回 code=401
- 前端：编辑区 textarea `@paste` 监听剪贴板图片 → 自动上传为附件 → 在光标处插入 `![文件名](/api/wiki/attachments/{id}/download?token=...)`
- 说明：token 进 URL 会被服务器日志记录，仅适合内部部署场景（团队工具可接受）

**四、验证结果**：
- move 7 项场景全 PASS（before/after/inner/移回根级/子级↔根级往返/移动到自身拒绝）
- token 下载：好 token 200 文件内容 / 无 token、假 token 均返回 `{"code":401}`（业务 code，HTTP 200 为 Result 惯例）
- 原 16 项回归全 PASS；前端 vue-tsc + vite build 通过

**Files created (1)**: `utils/diff.ts`
**Files modified (6)**: `WikiService.java`, `WikiController.java`, `SecurityConfig.java`, `api/wiki.ts`, `views/Wiki.vue`, `AGENTS.md`

### v2.13.7 — Wiki 文档系统（项目级知识库）(2026-08-24)

项目级知识沉淀模块，对标 v3.0 路线图 §6「Wiki 知识库」的 MVP：三级层次中的两级（页面 + 子页面树形目录）+ Markdown 编辑 + 版本历史 + 全文检索 + 附件。

**一、V19 数据库迁移**（[V19__wiki_pages.sql](file:///e:/桌面/Mimo/backend/mimo-app/src/main/resources/db/migration/V19__wiki_pages.sql)）：
- `wiki_pages`：页面主表（project_id / parent_id / title / slug 保留 / content LONGTEXT / content_html 缓存位 / author_id / editor_id / version / sort_order / is_pinned / view_count），索引覆盖 project+parent 树查询与 author/updated 检索
- `wiki_versions`：版本快照表（page_id / version / title+content 快照 / editor_id / change_summary），每次编辑前将旧内容入历史
- `wiki_attachments`：附件元数据表（page_id / file_name / file_path / file_size / mime_type / uploader_id）

**二、后端**（[WikiService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/WikiService.java)，[WikiController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/WikiController.java)，[WikiDTO.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/dto/WikiDTO.java)）：
- 树形目录 `GET /api/wiki/tree`：Mapper 原生 SQL 排除 LONGTEXT 字段轻量拉取，批量补作者名，两遍组树
- 页面 CRUD：创建自动排 sort_order；更新前保存当前版本快照 → version+1；删除递归清子页面 + 版本 + 附件
- 版本历史：列表 / 单版本详情 / 回滚（回滚本身也算一次编辑，先快照再恢复目标版本）
- 全文检索 `GET /api/wiki/search`：title + content LIKE（兼容 MySQL 5.x，后续可换 FULLTEXT）
- 附件：multipart 上传（UUID 重命名防覆盖）/ 列表 / 流式下载 / 删除（连物理文件）
- 权限：项目所属团队成员可读写删（`TeamService.isTeamMember`），系统管理员直通；未认证/非成员 401/403
- 附件存储绝对路径解析（`Paths.get(uploadDir).toAbsolutePath()`），避免 Tomcat CWD 漂移导致 FileNotFoundException

**三、前端**（[Wiki.vue](file:///e:/桌面/Mimo/frontend/src/views/Wiki.vue)，[api/wiki.ts](file:///e:/桌面/Mimo/frontend/src/api/wiki.ts)）：
- 双栏布局：左侧目录树（el-tree 全展开 + 节点 hover 新建子页/删除 + 搜索切换）+ 右侧内容区
- 查看模式：marked 渲染（复用 markdown.ts XSS 过滤）+ 元信息（版本/作者/浏览量）+ 附件列表
- 编辑模式：标题 + Markdown 正文 + 变更摘要，保存后刷新树与版本
- 版本历史 drawer：版本列表 → 预览 → 一键回滚（二次确认）
- 路由 `/projects/:id/wiki` + ProjectOverview hero「知识库」按钮（Notebook 图标）

**四、验证结果**：
- V19 三张表迁移成功；后端 `mvn compile` BUILD SUCCESS
- 16 项 API 回归全 PASS：创建/子页面/树(轻量)/详情(浏览量+1)/更新(version+1)/快照/版本详情/回滚(version=3)/搜索/未认证拒绝/级联删除/附件上传列表下载删除
- 前端 `vue-tsc --noEmit` + `vite build` 通过

**Files created (6)**: `WikiDTO.java`, `WikiService.java`, `WikiController.java`, `api/wiki.ts`, `views/Wiki.vue`
**Files modified (3)**: `router/index.ts`, `ProjectOverview.vue`, `AGENTS.md`

### v2.13.6 — 邮件通知收尾（发送日志 + 模板化）(2026-08-21)

接 v2.13.5 Webhook 集成后，补齐邮件通知链路的可观测性和正文模板化，便于排查送达失败和统一品牌样式。

**一、V18 邮件发送日志表**（[V18__email_send_logs.sql](file:///e:/桌面/Mimo/backend/mimo-app/src/main/resources/db/migration/V18__email_send_logs.sql)）：
- `email_send_logs` 表 11 列：`user_id` / `to_email` / `account_id` / `from_email` / `subject` / `notify_type` / `related_id` / `success` / `error` / `duration_ms` / `created_at`
- 三个索引：`idx_user` / `idx_created` / `idx_success`，便于按用户、时间、结果筛选

**二、MailSenderService 结构化结果**（[MailSenderService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailSenderService.java)）：
- 新增 `SendResult` 静态类（`success` / `error` / `durationMs`）
- 新增 `sendWithLog(account, toEmail, subject, htmlBody)` 返回 SendResult，原 `send` 方法保留兼容
- 失败时记录异常类型 + 消息到 `error` 字段，方便日志检索

**三、EmailTemplateService HTML 模板**（[EmailTemplateService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/EmailTemplateService.java)）：
- `render(Notification, type, actionUrl)` 输出品牌色邮件正文
- 包含：Mimo 品牌头 / 标题 / 类型徽章（任务指派 / @提及 / 审批通知 / 评论回复 / 任务状态变更）/ 时间戳 / 内容卡片 / 查看详情按钮 / 页脚
- 不引入 Thymeleaf 等额外依赖，纯 String.format + escape，避免依赖膨胀
- 转义所有用户输入，防止邮件正文注入

**四、EmailNotificationService 落库日志**（[EmailNotificationService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/EmailNotificationService.java)）：
- 改用 `sendWithLog` 拿到 SendResult 后调用 `writeLog` 落库
- `writeLog` 字段映射：`userId` / `toEmail` / `accountId` / `fromEmail` / `subject` / `notifyType` / `relatedId` / `success` / `error` / `durationMs`
- `buildActionUrl` 按事件类型生成跳转链接：approval → `/admin/approvals?id=`，其余 → `/issues/`
- 日志落库失败降级 warn 不抛异常，不阻塞主流程

**五、EmailSendLogController 查询端点**（[EmailSendLogController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/EmailSendLogController.java)）：
- `GET /api/email-send-logs`：分页查询，支持 userId / notifyType / success / startTime / endTime 筛选
- `GET /api/email-send-logs/stats`：统计总数 / 成功数 / 失败数 / 送达率（百分比）
- 与 AuditLogController 风格保持一致，便于运维侧排查

**六、前端 EmailSendLogs 页面**（[EmailSendLogs.vue](file:///e:/桌面/Mimo/frontend/src/views/EmailSendLogs.vue)，[router/index.ts](file:///e:/桌面/Mimo/frontend/src/router/index.ts)，[MainLayout.vue](file:///e:/桌面/Mimo/frontend/src/components/layout/MainLayout.vue)）：
- 统计卡片（总数 / 成功 / 失败 / 送达率）+ 筛选表单（用户ID / 类型 / 结果 / 时间范围）
- 表格展示收件人 / 发件人 / 主题 / 类型 / 结果 / 耗时 / 错误 / 时间
- 路由 `/admin/email-send-logs`，菜单「邮件发送日志」放在 ROLE_ADMIN 分组

**验证结果**：
- V18 迁移 ok=1 表已创建
- `GET /api/email-send-logs` 返回空列表（当前无发送记录，符合预期）
- `GET /api/email-send-logs/stats` 返回 `deliveryRate=0.0 total=0 success=0 failed=0`
- 前端 build 通过

### v2.13.5 — 第三方 Webhook 集成 (2026-08-21)

新增"事件触发外部系统回调"通道，支持钉钉/飞书/Jenkins 等 IM 机器人和 CI/CD 触发器接入。

**一、V17 Webhook 表迁移**（[V17__webhook_integrations.sql](file:///e:/桌面/Mimo/backend/mimo-app/src/main/resources/db/migration/V17__webhook_integrations.sql)）：
- `webhook_configs` 表：项目/团队级配置 + 名称 + URL + 签名密钥 + 订阅事件 + 启用开关
- `webhook_delivery_logs` 表：投递日志含 webhook_id / event_type / payload / status_code / response_body / success / duration_ms / error
- 三重索引覆盖项目/团队/启用维度查询

**二、MigrationRunner 修复注释剥离**（[MigrationRunner.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/config/MigrationRunner.java)）：
- **问题**：V17 SQL 文件以 `-- v2.13.5：...` 注释开头，原 splitStatements 后 trim 整条语句首字符是 `-`，被 `startsWith("--")` 判定为注释跳过，导致 ok=0 skipped=0 failed=0
- **修复**：用正则 `^(\\s*--[^\\n]*[\\r\\n]*)+` 剥离语句开头的所有行注释，再 trim 判空
- **验证**：重启后 V17 ok=2 skipped=0 failed=0，两张表正常创建

**三、WebhookDispatcherService 异步派发**（[WebhookDispatcherService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/WebhookDispatcherService.java)）：
- `dispatch(eventType, projectId, teamId, payload)` 按订阅事件过滤后异步投递
- `deliverTest(hook, payload)` 测试投递入口，绕过 events 过滤直接发送
- `deliver` 方法：序列化 body → 加 X-Mimo-Signature/X-Mimo-Event Header → POST → 落库日志
- `sign` 方法用 HmacSHA256 计算签名，hex 输出供对方校验来源
- `findMatching` 过滤：enabled=1 + 项目/团队范围 + events 列表包含 eventType
- ObjectMapper 配置 JavaTimeModule + 禁用 WRITE_DATES_AS_TIMESTAMPS，支持 LocalDateTime 序列化

**四、AsyncConfig 专用线程池**（[AsyncConfig.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/config/AsyncConfig.java)）：
- `webhookTaskExecutor` Bean：core=4 / max=16 / queue=200，前缀 `webhook-`
- RejectedExecutionHandler 用 CallerRunsPolicy，避免任务丢失
- WaitForTasksToCompleteOnShutdown + 30s 等待，确保投递不丢

**五、WebhookConfigController CRUD + 测试**（[WebhookConfigController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/WebhookConfigController.java)）：
- `GET /api/webhooks` 分页列表，支持 projectId / teamId / includeDisabled 筛选
- `GET /{id}` / `POST` / `PUT /{id}` / `DELETE /{id}` 标准 CRUD
- `POST /{id}/test` 测试投递，调用 `deliverTest` 直接发到该 Webhook，绕过 events 过滤
- `GET /{id}/deliveries` 查询投递日志
- 全部用 @AuditLog 注解记录操作审计

**六、IssueController 事件触发**（[IssueController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/IssueController.java)）：
- create / update / delete 方法注入 WebhookDispatcherService
- 任务创建发 ISSUE_CREATED、更新发 ISSUE_UPDATED、删除发 ISSUE_DELETED
- projectId / teamId 从 Project 表关联查询

**七、前端 Webhooks 配置页面**（[Webhooks.vue](file:///e:/桌面/Mimo/frontend/src/views/Webhooks.vue)，[api/webhook.ts](file:///e:/桌面/Mimo/frontend/src/api/webhook.ts)，[router/index.ts](file:///e:/桌面/Mimo/frontend/src/router/index.ts)，[MainLayout.vue](file:///e:/桌面/Mimo/frontend/src/components/layout/MainLayout.vue)）：
- 列表页：名称 / URL / 范围标签 / 事件订阅标签 / 启用状态 / 操作
- 创建/编辑弹窗：名称 / URL / 签名密钥 / 订阅事件多选 / 生效范围（全局/项目）/ 项目选择 / 启用开关
- 投递日志弹窗：事件类型 / 结果标签 / HTTP / 耗时 / 错误 / 时间，点查看详情看 payload + 响应体
- 测试投递按钮 + loading 状态 + 结果 toast 提示
- 路由 `/admin/webhooks`，菜单「Webhook 集成」放在 ROLE_ADMIN 分组

**验证结果**：
- mock HTTP server 跑在 9999 端口接收测试投递，返回 `{"ok":true}`
- `POST /api/webhooks/1/test` 返回 `success=1 statusCode=200 responseBody={"ok":true}`
- 创建任务触发 ISSUE_CREATED 事件，mock server 收到完整 Issue 数据 JSON
- 错误场景：连不上 9999 时记录 `I/O error on POST... Connection refused`，success=0 落库

### v2.13.4 — 操作审计日志 + 数据导入导出 (2026-08-21)

补齐运维侧可观测性和数据迁移通道：操作审计日志 AOP 切面 + 任务数据 Excel/CSV/JSON 导入导出。

**一、V16 审计日志扩展字段**（[V16__audit_log_extension.sql](file:///e:/桌面/Mimo/backend/mimo-app/src/main/resources/db/migration/V16__audit_log_extension.sql)）：
- `activity_logs` 表新增 ip_address / user_agent / diff_json / request_id 四列
- `target_id` 字段改为 NULL（部分操作无明确对象 ID）

**二、AuditLog 注解 + AOP 切面**（[AuditLog.java](file:///e:/桌面/Mimo/backend/mimo-common/src/main/java/com/mimo/common/AuditLog.java)，[AuditLogAspect.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/config/AuditLogAspect.java)）：
- `@AuditLog(targetType=, targetId=, action=, detail=)` 注解方法自动记录
- AOP 切面 SpEL 解析表达式取参数值，自动落库到 activity_logs
- RequestContextHolder 取 IP / User-Agent，RequestId 关联同一请求的操作链

**三、AuditLogController 查询端点**（[AuditLogController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/AuditLogController.java)）：
- `GET /api/audit-logs` 分页 + 多条件筛选（userId / targetType / action / targetId / requestId / 时间范围）
- `GET /api/audit-logs/target?targetType=&targetId=` 按对象查历史动态

**四、DataPortController 数据导入导出**（[DataPortController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/DataPortController.java)）：
- `GET /api/data-port/export/issues?projectId=&format=excel|csv|json` 导出任务数据
- `POST /api/data-port/import/issues` Excel/CSV 批量导入任务，自动校验字段、跳过错误行
- `GET /api/data-port/template` 下载导入模板
- CSV 处理：UTF-8 BOM 检测并移除，避免表头识别失败

**五、前端 AuditLogs.vue + ProjectOverview 导入导出**（[AuditLogs.vue](file:///e:/桌面/Mimo/frontend/src/views/AuditLogs.vue)，[ProjectOverview.vue](file:///e:/桌面/Mimo/frontend/src/views/ProjectOverview.vue)）：
- 审计日志查看页：筛选表单 + 表格 + CSV 导出按钮
- ProjectOverview 顶部新增"导入"和"导出"按钮，弹出操作弹窗
- 三种格式下拉选择，导出直接触发文件下载

### v2.13.3 — 邮件通知通道 + MigrationRunner 连接泄漏修复 (2026-08-21)

接 v2.13.2 邮箱收件箱稳定后，新增"事件触发邮件通知"通道，并修复 MigrationRunner 的 HikariPool 连接泄漏问题。

**一、MigrationRunner 自动扫描 + 连接泄漏修复**（[MigrationRunner.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/config/MigrationRunner.java)）：
- **现象**：新增 V15 迁移后启动，`/api/email/accounts` 报 500，login 也报 500，HikariPool-1 Connection 超时 30s
- **根因**：MigrationRunner 原实现用 `dataSource.getConnection()` 但没用 try-with-resources，每个迁移脚本拿一个连接后没归还，连续 13 个迁移把池子占满（默认 maximum-pool-size=10），导致后续业务请求超时
- **修复一**：硬编码列表改为 `PathMatchingResourcePatternResolver` 自动扫描 `classpath:db/migration/V*.sql`，按字典序执行，避免以后再追加
- **修复二**：try-with-resources 包裹 `Connection conn`，每个脚本用完立即归还连接池
- **兼容性**：IF NOT EXISTS / Duplicate 类的重跑错误继续被识别为"已执行过"跳过

**二、V15 数据库迁移**（[V15__email_notification.sql](file:///e:/桌面/Mimo/backend/mimo-app/src/main/resources/db/migration/V15__email_notification.sql)）：
- `user_email_accounts` 表新增 4 列：
  - `smtp_host VARCHAR(200)`：SMTP 服务器主机，NULL 时按 imap_host 推断（imap.x → smtp.x）
  - `smtp_port INT`：SMTP 端口，NULL 时默认 465（SSL）
  - `notify_types VARCHAR(200) DEFAULT 'assignment,mention,approval'`：触发邮件通知的事件类型（逗号分隔）
  - `notify_enabled TINYINT(1) DEFAULT 0`：是否启用邮件通知，0 关闭 1 启用

**三、MailSenderService SMTP 发送**（[MailSenderService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailSenderService.java)）：
- `send(UserEmailAccount, toEmail, subject, htmlBody)` 用用户绑定的邮箱账户发送邮件
- SMTP 配置：使用 `smtps` 协议，SSL 启用，host/port 从账户取或按 IMAP 推断
- 密码复用 `EmailCryptoUtil.decrypt(imapPasswordEnc)` 解密，IMAP/SMTP 共用授权码
- 发件人显示名 "Mimo 项目协作"，正文格式 `text/html; charset=UTF-8`
- 失败降级返回 false，不抛异常，由上层通知派发处理

**四、EmailNotificationService 异步派发**（[EmailNotificationService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/EmailNotificationService.java)）：
- `@Async dispatch(recipientUserId, type, Notification)` 异步派发邮件通知
- 流程：取收件人 `User.email` → 取该用户所有"启用了该 type"的邮箱账户 → 依次发送
- 用户偏好过滤：`EmailAccountService.listNotifyEnabled(userId, type)` 按 `notify_types` 字段匹配
- 失败降级：单封失败不影响其他账户；整体异常不影响站内通知（NotificationService 包了 try-catch Throwable）

**五、NotificationService 触发点接入**（[NotificationService.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/NotificationService.java)）：
- `create(Notification)` 方法落库后异步派发邮件通知
- `mapEmailType(Notification)` 映射通知到邮件偏好类型：
  - `relatedType=APPROVAL_REQUEST` → `approval`
  - `type=ASSIGNED` → `assignment`
  - `type=STATUS_CHANGED` + content 含"评论" → `comment`
  - `type=STATUS_CHANGED` + 其他 → `issue_status`
  - 其余返回 null（不触发邮件）

**六、EmailController 新增端点**（[EmailController.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/EmailController.java)）：
- `PUT /api/email/accounts/{id}/notification-settings`：更新通知偏好（enabled/types/smtpHost/smtpPort，字段都可选）
- `POST /api/email/accounts/{id}/test-notification`：用该账户 SMTP 给本人发测试邮件，不依赖偏好开关

**七、@EnableAsync 启用异步**（[MimoApplication.java](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/MimoApplication.java)）：
- 添加 `@EnableAsync` 注解，启用 Spring 异步任务支持，让 `@Async` 注解生效

**八、前端 EmailAccounts 通知偏好弹窗**（[EmailAccounts.vue](file:///e:/桌面/Mimo/frontend/src/views/EmailAccounts.vue)，[email.ts](file:///e:/桌面/Mimo/frontend/src/api/email.ts)）：
- 表格新增"邮件通知"列，显示已开启/关闭状态标签
- 操作列新增"通知偏好"按钮，打开偏好设置弹窗
- 弹窗包含：启用开关、5 个触发事件 checkbox、SMTP 主机/端口输入、发送测试邮件按钮
- `updateNotifySettings` / `sendTestNotification` 两个 API 方法，单独设置 30s 超时

**九、验证结果**：
- 后端编译通过，V15 迁移成功执行
- `/api/email/accounts` 接口返回新字段：notifyEnabled、notifyTypes、smtpHost、smtpPort
- `PUT /notification-settings` 更新偏好返回 200，新字段正确回填
- `POST /test-notification` 测试邮件发送返回 200（admin 用户的 email 字段是测试假邮箱，实际收不到但说明 SMTP 链路通）

### v2.13.2 — 收件箱拉取链路三大 Bug 修复 (2026-08-21)

紧接 v2.13.1 之后，用户反馈"报错啊打不开啊"与"拉取收件箱失败"两个问题，深度排查后修复三个独立 Bug。

**一、JavaMail API 误用导致 NegativeArraySizeException（错误码 -120/-150/-154）**（[MailReceiverService.java#L55-L84](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailReceiverService.java#L55-L84)）：
- **现象**：所有 listInbox 调用都抛 `java.lang.NegativeArraySizeException`，错误消息就是 `-120`/`-150`/`-154` 这种负数（与 limit 取值有关，limit=20 → -120，limit=11 → -150，limit=3 → -154）
- **根因**：`Folder.getMessages(int start, int end)` 的第二个参数是**结束序号（含）**，不是数量。之前传了数量 `count`，导致数组大小 `count - start + 1` 变负数
- **修复**：改为 `inbox.getMessages(start, total)`，传结束序号
- **教训**：不要被错误消息中的"邮箱连接失败：-120"误导，看起来像 QQ 邮箱的协议错误码，其实是 Java 负数组大小异常的消息字符串
- **调试过程**：开启 JavaMail 协议调试（`session.setDebug(true)` + `setDebugOut(System.err)`）后看到 SELECT/CAPABILITY/ID 全部成功，问题必在客户端代码，最终定位到 getMessages 参数

**二、收件箱列表超时（18s 超过前端 15s 超时）**（[MailReceiverService.java#L68-L73](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailReceiverService.java#L68-L73)，[#L278](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailReceiverService.java#L278)）：
- **现象**：limit=5 仅 1s，limit=20 耗时 18.45s，超过前端 15s 超时
- **根因**：`toSummary` 调用 `extractSnippet(m, 200)` 给每封邮件拉取 text/plain 正文做预览，20 封邮件 = 20 次 FETCH BODY 请求，每次都有网络往返
- **修复**：
  - listInbox 用 `FetchProfile` 批量预取 ENVELOPE + FLAGS + SIZE，一次 FETCH 拉所有元数据
  - `toSummary` 不再调 `extractSnippet`，snippet 字段留空字符串
  - 注释明确：如需正文预览，调用 `getMailDetail` 拿单封详情（按需懒加载）
- **效果**：limit=20 耗时从 18.45s → 1.25s，提升约 15 倍
- **前端配套**：[email.ts#L75-L78](file:///e:/桌面/Mimo/frontend/src/api/email.ts#L75-L78) `getInbox` 单独设置 30s 超时做兜底

**三、预设自动填充下拉无数据 + 浏览器侧 403**（[EmailAccounts.vue#L149-L157](file:///e:/桌面/Mimo/frontend/src/views/EmailAccounts.vue#L149-L157)，[#L173-L184](file:///e:/桌面/Mimo/frontend/src/views/EmailAccounts.vue#L173-L184)）：
- **现象 A**：邮箱账户页"绑定新邮箱"弹窗的"选择预设自动填充"下拉框无数据
- **根因 A**：`loadPresets` 用 `catch {}` 静默吞错，且只在 `onMounted` 调一次。若当时后端未启动或 token 未就绪，presets 一直空，后续打开弹窗也不会重拉
- **修复 A**：catch 改为 `console.warn` 暴露错误；`openBind` 里检测 presets 为空时自动补拉一次
- **现象 B**：前端请求返回 403，但 PowerShell 直连同 token 返回 200
- **根因 B**：浏览器 localStorage 里存的旧 token 与后端新 JWT_SECRET 不匹配（后端重启过换过密钥），jwt filter 解析失败返回 403
- **修复 B**：用户清掉 localStorage 的 token 重新登录即可（非代码问题）

**四、IMAP ID 命令（RFC 2971）支持**（[MailReceiverService.java#L146-L171](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailReceiverService.java#L146-L171)）：
- QQ/163 等邮箱要求客户端登录后立即发送 IMAP ID 命令报告身份
- 不发 ID 命令可能触发"Unsafe Login"限制，导致后续 FETCH/EXAMINE 被拒
- 新增 `sendImapId(store)` 私有方法，connect 后立即调用 `IMAPStore.id(Map)`
- 客户端身份字段：name=Mimo, version=1.0.0, vendor=MimoProject, support-email=noreply@mimo.local
- 服务器不支持 ID 命令或非 IMAPStore 时静默跳过，不影响主流程
- `EmailController.deepDebug` 端点也同步加上 ID 命令发送逻辑

**五、调试日志增强**（[MailReceiverService.java#L150-L153](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailReceiverService.java#L150-L153)）：
- `runImap` 的 catch 块把异常类名也打印出来：`type={} err={}`，方便区分协议错误、API 误用、网络异常
- listInbox 的 toSummary 失败不再拖垮整个收件箱：单封解析失败用占位 summary 替代，其他邮件继续返回

**六、涉及文件**：
- 后端修改：`MailReceiverService.java`、`EmailController.java`
- 前端修改：`EmailAccounts.vue`、`api/email.ts`

### v2.13.1 — 邮箱错误友好化 + 测试连接 + 诊断端点 (2026-08-21)

针对 QQ 邮箱返回 `-120` 错误码（用户已开 IMAP 仍失败）的排障需求，对邮箱模块做体验升级。

**一、底层异常 → 可操作中文提示**（[MailReceiverService.java#L118-L191](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service/MailReceiverService.java#L118-L191)）：
- 原 catch 块把 `e.getMessage()` 直接透传，QQ 服务器返回的 `-120`（只 3 个字符）让用户一脸懵
- 新增 `buildFriendlyError(acc, e)` 方法，按**异常类型 + 关键字 + 错误码**三重识别：
  - `AuthenticationFailedException` 类型 → 判定为认证失败
  - 错误码 `-120` 直接作为 QQ 邮箱登录失败强信号
  - 按 `host`/`email` 区分 QQ/163/126/Gmail/Outlook，给出针对性操作指引 + 官方帮助链接
  - 网络类（Unknown host / Connection refused / connect timed out）→ 提示检查主机端口和网络
  - SSL 类 → 提示端口 993 + SSL
- 兜底仍返回原始 msg，避免漏判

**二、测试连接端点**（[EmailController.java#L46-L84](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/EmailController.java#L46-L84)）：
- 新增 `POST /api/email/accounts/test`
- 绑定前先验证 IMAP 凭据是否有效，不保存到数据库
- 重构 `MailReceiverService.runImap`：原来内部 `emailCryptoUtil.decrypt` 解密，现改为接收外部传入明文密码，测试连接直接用明文，绕开加解密
- 新增 `testConnection(account, plainPassword)` 方法：只打开 INBOX 读 count，不拉邮件
- 前端 [EmailAccounts.vue](file:///e:/桌面/Mimo/frontend/src/views/EmailAccounts.vue) 绑定弹窗底部新增「测试连接」按钮（与「绑定」并列）

**三、诊断端点**（[EmailController.java#L36-L50](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/controller/EmailController.java#L36-L50)）：
- 新增 `GET /api/email/accounts/{id}/debug`
- 返回解密后的密码长度 + 前 2 位 + 末 2 位（脱敏）
- 用于排查"测试通过但拉取失败"这类加解密链路问题
- `EmailAccountService` 新增 `decryptPassword(acc)` public 方法供 controller 调用
- 前端表格「操作」列新增「诊断」按钮（黄色文字按钮）

**四、典型 -120 排障路径**（QQ 邮箱登录失败）：
1. IMAP 用户名必须是**完整邮箱地址**（`xxx@qq.com`），不是 QQ 号
2. 密码必须是 **16 位授权码**，不是 QQ 登录密码
3. 授权码失效 → 重新生成新的
4. 频率限制 → 短时间内多次 IMAP 登录会触发 -120，等 10-15 分钟自动解除
5. 账户异常 → 去 QQ 邮箱网页版做安全验证
6. 用「测试连接」按钮预先验证凭据；用「诊断」按钮验证加解密链路

**五、涉及文件**：
- 后端修改：`MailReceiverService.java`、`EmailAccountService.java`、`EmailController.java`
- 前端修改：`EmailAccounts.vue`、`api/email.ts`

### v2.13.0 — 公告模块 + 真实邮箱绑定与收件箱 (2026-08-21)

新增「公告」和「邮箱」两大功能域，左侧导航新增对应入口。

**一、公告模块（Announcements）**：
- 数据库：V13 `announcements` 表（id / title / content Markdown / pinned / created_by / 时间戳 / 逻辑删除）
- 后端：
  - `entity/Announcement.java` + `mapper/AnnouncementMapper.java`（MyBatis-Plus BaseMapper）
  - `dto/AnnouncementDTO.java`：CreateRequest / UpdateRequest / VO
  - `service/AnnouncementService.java`：发布/编辑/删除/分页列表/详情，创建时自动填充 createdBy
  - `controller/AnnouncementController.java`：5 个端点
    - `GET  /api/announcements?page=&size=` 分页列表（按 pinned DESC + createdAt DESC）
    - `GET  /api/announcements/{id}` 详情
    - `POST /api/announcements` 新建（仅 ROLE_ADMIN）
    - `PUT  /api/announcements/{id}` 编辑（仅 ROLE_ADMIN）
    - `DELETE /api/announcements/{id}` 删除（仅 ROLE_ADMIN）
  - 管理员权限校验：从 `Authentication.getAuthorities()` 提取 ROLE_ADMIN
- 前端：
  - `api/announcement.ts`：5 个接口 + AnnouncementVO 类型
  - `views/Announcements.vue`：公告列表（卡片样式）+ 发布/编辑弹窗 + Markdown 详情弹窗
  - 简易 Markdown 渲染：标题 / 列表 / 引用 / 粗体 / 行内 code
  - 管理员可见「发布/编辑/删除」按钮，普通用户只读

**二、邮箱绑定 + 收件箱（Email Bind & Inbox via IMAP）**：
- 数据库：V14 `user_email_accounts` 表（user_id / email_address / imap_host / imap_port / imap_username / **imap_password_enc 加密存储** / is_default / last_synced_at）
- 后端：
  - `entity/UserEmailAccount.java` + `mapper/UserEmailAccountMapper.java`
  - `util/EmailCryptoUtil.java`：**AES-GCM 加解密**，IV 12 字节 + TAG 128 bit，密文格式 base64(iv || ciphertext+tag)，密钥取自 `${email.encryption-key}` 配置项（默认开发密钥，生产环境必须覆盖）
  - `dto/EmailAccountDTO.java`：BindRequest / AccountVO / PresetVO
  - `dto/MailDTO.java`：MailSummaryVO / MailDetailVO / InboxVO
  - `service/EmailAccountService.java`：绑定（唯一性校验 + 加密存储 + 首个账户自动默认）/ 解绑 / 列表 / 取默认账户 / 5 个内置 IMAP 预设（QQ/163/Gmail/Outlook/126）
  - `service/MailReceiverService.java`：基于 `com.sun.mail:jakarta.mail` 的 IMAP 收件箱拉取
    - `listInbox(account, limit)`：INBOX 最后 N 封按时间倒序
    - `fetchDetail(account, seq)`：单封详情，提取 text/plain + text/html 双体
    - 递归 walkParts 解析 Multipart
    - 连接超时 10s，读取超时 15s，SSL trust 全部
  - `controller/EmailController.java`：6 个端点
    - `GET    /api/email/presets` 常见邮箱 IMAP 预设
    - `GET    /api/email/accounts` 已绑定账户列表
    - `POST   /api/email/accounts` 绑定新邮箱
    - `DELETE /api/email/accounts/{id}` 解绑
    - `GET    /api/email/inbox?accountId=&limit=` 收件箱（accountId 省略走默认账户）
    - `GET    /api/email/accounts/{accountId}/messages/{seq}` 邮件详情
- 前端：
  - `api/email.ts`：6 个接口 + 全部 TS 类型
  - `views/EmailAccounts.vue`：邮箱绑定页
    - 已绑定账户表（邮箱/IMAP/默认/最近同步/操作）
    - 预设表（5 个常见邮箱 IMAP 配置参考）
    - 绑定弹窗：快速选择预设自动填 IMAP 主机/端口
  - `views/EmailInbox.vue`：收件箱双栏布局
    - 左侧邮件列表（未读高亮 + 主题/发件人/时间/摘要）
    - 右侧邮件详情（textBody 优先，否则渲染 htmlBody，含简易 XSS 过滤：去 script/iframe/object/embed/on* 事件）
    - 多账户切换 / 数量限制 / 一键刷新
  - 路由：`/announcements`、`/email/inbox`、`/email/accounts`

**三、MainLayout 左侧导航新增**：
- 「公告」入口（BellFilled 图标）→ /announcements
- 「邮箱」子菜单（Message 图标）展开为：
  - 「收件箱」（Inbox 图标）→ /email/inbox
  - 「邮箱绑定」（Connection 图标）→ /email/accounts

**四、构建链调整**：
- `mimo-app/pom.xml` 新增 `spring-boot-starter-mail` 依赖
- `config/MigrationRunner.java` 列表新增 V13 / V14 两个迁移脚本

**五、安全考量**：
- IMAP 密码**绝不明文存储**，AES-GCM 加密 + base64 入库，加密密钥取自配置项（生产环境必须覆盖）
- 解密只在 `MailReceiverService` 内部进行，返回给前端的接口不暴露密码
- 公告发布/编辑/删除仅 ROLE_ADMIN 可调用，普通用户只读
- 邮箱账户的操作（解绑/拉收件箱/看详情）严格校验 `userId` 归属，跨用户操作直接 403
- 前端 HTML 邮件正文做了基础 XSS 过滤（script/iframe/object/embed/事件属性全部移除）

**六、涉及文件**：
- 后端新增 12 个文件 + 修改 2 个（MigrationRunner / mimo-app pom.xml）+ 2 个 SQL 迁移
- 前端新增 5 个文件（announcement.ts / email.ts / Announcements.vue / EmailAccounts.vue / EmailInbox.vue）+ 修改 2 个（MainLayout.vue / router/index.ts）

### v2.12.1 — 阶段 4 (mimo-project) 调研结论与决策 (2026-08-21)

阶段 4 调研抽取 `mimo-project` 子模块（Project/Issue/Board/Sprint/Approval/Attachment/Comment/WorkLog/Workflow/Gantt/ActivityLog 等共 33 个文件）。

**结论**：暂缓阶段 4，保持当前 mimo-common + mimo-system + mimo-app 三模块架构。

**关键阻塞 — 循环依赖**（Grep [mimo-app/src/main/java/com/mimo/service](file:///e:/桌面/Mimo/backend/mimo-app/src/main/java/com/mimo/service) 发现）：
- `ApprovalService` → `NotificationService` (mimo-app)
- `BoardService` → `NotificationService`, `WebSocketService` (mimo-app)
- `IssueService` → `NotificationService`, `WebSocketService` (mimo-app)
- `CommentService` → `NotificationService`, `WebSocketService` (mimo-app)
- `WorkLogService` → `TeamService` (mimo-app)

直接抽 mimo-project 会形成 mimo-project ↔ mimo-app 循环依赖，Maven 不允许。

**三个候选方案**：
- 方案 A：同时抽 mimo-infra（Notification/WebSocket/Email）模块，链路 mimo-common ← mimo-system ← mimo-infra ← mimo-project ← mimo-app。彻底解耦，工作量翻倍。
- 方案 B：Spring 事件机制，IssueService 发 `IssueCreatedEvent`，mimo-app 监听后调 NotificationService。无需新模块，但要改造 4 个 service。
- **方案 C（采纳）**：暂缓阶段 4。前三阶段是纯下沉边界清晰；project 域与 Notification/WebSocket 在业务上强耦合，强行拆开偏离"代码组织清晰化"初衷。当前三模块架构已足够支撑后续扩展。未来如需拆分（例如 project 单独发版），再做方案 A 或 B。

### v2.12.0 — Maven Multi-Module Refactor Phase 1-3 (2026-08-20)

将单模块 `mimo-backend` 拆分为多 Maven 模块，先沉淀**基础层 + RBAC 子系统**两块独立 jar，业务模块（mimo-project 等）下次继续抽取。

**模块拓扑**：
```
mimo-backend (parent pom)
  ├── mimo-common   ← 基础类库（异常/Result/注解/分页 DTO），零业务依赖
  ├── mimo-system   ← RBAC 子系统，依赖 mimo-common
  └── mimo-app      ← 启动器 + 业务实现（暂含 Project/Issue/Board/Sprint/Chat/Calendar/Notification/Approval/Dashboard/Report 等），依赖 mimo-common + mimo-system
```

**阶段 1 — 父 pom + mimo-app 子模块**：
- `backend/pom.xml` 改为 `<packaging>pom</packaging>`，`<modules>` 声明子模块
- `properties` 提升到父 pom：`jjwt.version=0.11.5`、`mybatis-plus.version=3.5.3.1`、`knife4j.version=4.1.0`、`java.version=17`
- `pluginManagement` 集中管理 `maven-compiler-plugin`（source/target=17）
- 创建 `backend/mimo-app/pom.xml`：继承父 pom，打包 jar，`spring-boot-maven-plugin` 指定 `mainClass=com.mimo.MimoApplication`
- 原 `backend/src` 整体迁移至 `backend/mimo-app/src`（零代码改动）

**阶段 2 — mimo-common 抽取（6 个基础类）**：
- `common/BusinessException.java`、`common/Result.java`、`common/ResultCode.java`、`common/RequirePermission.java`
- `dto/common/IdRequest.java`、`dto/common/PageRequest.java`
- 依赖：`lombok` + `spring-boot-starter-validation`（IdRequest 用 `@NotNull`）
- 不含 Spring Web / Security / AOP（保持轻量基础库特性）
- 父 pom `dependencyManagement` 声明 `mimo-common` 版本，`mimo-app` 直接引用

**阶段 3 — mimo-system 抽取（RBAC 子系统，12 个文件）**：
- `entity/`: `Permission`、`Role`、`RolePermission`、`UserRole`
- `mapper/`: `PermissionMapper`、`RoleMapper`、`RolePermissionMapper`、`UserRoleMapper`
- `service/RoleService`、`controller/RoleController`、`dto/RoleDTO`
- `config/PermissionAspect`（@Aspect，依赖 `mimo-common` 的 `RequirePermission` + `BusinessException`）
- 依赖：`mimo-common` + `spring-boot-starter-web` + `spring-boot-starter-security` + `spring-boot-starter-aop` + `mybatis-plus-boot-starter` + `lombok`
- 故意未搬 User/Team/UserLevel/ApprovalRequest：避免与 Project 业务域形成循环依赖（ApprovalService 依赖 ProjectService），待 mimo-project 阶段统一处理

**GlobalExceptionHandler 处理**：
- 留在 `mimo-app`（`@RestControllerAdvice(basePackages = "com.mimo.controller")` + 含 SSE 端点特殊逻辑），与 `com.mimo.controller` 包结构耦合
- 若 mimo-system 也需要全局异常处理，后续单独抽 `mimo-system` 模块的 `@RestControllerAdvice`

**Maven 构建链**：
- `cd backend && mvn clean install -q` → 三模块全部编译 + 安装本地仓库 exit 0
- `mimo-common` 先 install → `mimo-system` 解析依赖 → `mimo-app` 解析依赖 → `mimo-app` 打包
- IDEA 打开 backend/pom.xml 可识别多模块结构，自动按模块组织 Project 视图

**Files created/modified**：
- Created: `backend/mimo-app/pom.xml`、`backend/mimo-common/pom.xml`、`backend/mimo-system/pom.xml`
- Modified: `backend/pom.xml`（packaging=pom + modules + dependencyManagement）
- File moves: 6 个 common 类 → `mimo-common/`；12 个 RBAC 类 → `mimo-system/`；其余业务代码 → `mimo-app/src`

### v2.8.0 — AI Copilot Integration: DeepSeek Streaming Chat + Smart Issue Creator + Task Splitter (2026-06-16)

Major AI feature upgrade from 2 thin endpoints to a full AI Copilot system.

**Backend — New AI Module (`com.mimo.ai`)**:
1. **AiProvider** (interface) — Abstracts LLM provider, defines `chatJson()` and `chatStream(callback)` contracts
2. **DeepSeekProvider** — SSE streaming implementation using Java 11 `HttpClient`, calls `https://api.deepseek.com/chat/completions`
3. **AiService** — Orchestration layer with usage quota tracking; `chatStreamWithQuota()` wraps provider call
4. **AiController** — 7 endpoints:
   - `POST /api/ai/chat/stream` (SSE) — 流式对话，body 传 `message` / `systemPrompt`（POST 是为了避开 URL 长度限制）
   - `POST /api/ai/polish` — 润色描述文本
   - `POST /api/ai/priority` — 推荐任务优先级
   - `POST /api/ai/parse-issue` — 自然语言解析为结构化任务
   - `POST /api/ai/suggest-tasks` — Story拆分子任务建议
   - `POST /api/ai/estimate-story-points` — AI估算故事点
   - `GET /api/ai/usage` — 查询AI使用量配额

**Frontend — New Components**:
5. **AiChatPanel.vue** — 全局AI助手悬浮窗（右下角），SSE流式输出，Markdown渲染，支持DeepSeek模型选择
6. **AiIssueCreator.vue** — 看板页AI对话式创建任务入口，自然语言→结构化Issue
7. **AiTaskSplitter.vue** — 任务详情页Story拆分面板，AI建议子任务列表

**Frontend — API & Config**:
8. **ai.ts** — 完整重写：移除双重`/api`前缀(修复404)，新增6个接口函数+fetch SSE实现
9. **vite.config.ts** — 添加SSE代理配置：禁用压缩、超时60秒、noBuffering
10. **MainLayout.vue** — 集成AiChatPanel全局悬浮窗入口

**ProjectBoard.vue 全面修复 (40+处)**:
11. 中文乱码修复：注释、标签文本、状态标签等全部恢复为正确中文
12. SCSS样式修复：括号不匹配、选择器嵌套错误、孤立CSS代码块整合
13. 语法修复：模板标签未闭合、函数名拼写错误(`han]leBoardSync`→`handleBoardSync`)
14. IssueCard.vue从git commit恢复（模板和脚本完全丢失）

**Other Fixes**:
15. application.yml添加`deepseek.api-key`配置项
16. TeamController移除`@PreAuthorize("hasRole('ROLE_ADMIN')")`的ROLE_重复前缀bug

**Environment Variables Required**:
```bash
DEEPSEEK_API_KEY=sk-xxxxx  # DeepSeek API密钥
```

**Files changed (17 files, +2170/-181)**:
- Backend: `ai/` package (5 new files), AiController.java, TeamController.java, application.yml
- Frontend: ai.ts, AiChatPanel.vue, AiIssueCreator.vue, AiTaskSplitter.vue, MainLayout.vue, ProjectBoard.vue, IssueCard.vue, vite.config.ts, IssueDetailDialog.vue

### v2.7.3 — Code Quality Hardening: N+1 Fix, Type Safety, Configurable Params + XSS Protection (2026-06-15)

Comprehensive code quality audit and fix across 16 Controllers, 15 Services, all frontend views/APIs, and database schema.

**P0 — Critical Fixes (5/5)**:
1. **Exception Swallowing**: 15+ locations in IssueService(9), ApprovalService(2), BoardService(1), CommentService(1), AttachmentService(1), AiController(1) where exceptions were silently caught — added `log.error` at minimum; critical paths now throw or notify frontend
2. **N+1 Query Storm**: `IssueService.toVO()` made 6 independent DB queries per issue (column/user/labels/parent/children); `TeamService.listMembers()` queried user names one-by-one; same pattern in ApprovalService/ReportService. Fixed with `BatchContext` utility class + `selectBatchIds()` batch preloading — 30 cards = ~180 queries → ~6 queries
3. **AiController parseSimpleJson() Fragile**: Hand-written JSON parser failed on commas/colons/nesting. Replaced with Jackson `ObjectMapper.readValue()`
4. **Unsafe (Long) auth.getPrincipal() Cast**: 6 controllers used unsafe cast that would throw ClassCastException if principal type changed. Unified to `getLongPrincipal()` helper with `instanceof Number` check across DashboardController, ProjectController, TeamController(10 places), UserController(3 places)
5. **init.sql Missing 3 Tables**: `chat_messages`, `approval_requests`, `user_levels` only existed in migration scripts — new deployments running init.sql directly got 500 errors. Synced migrations into init.sql

**P1 — Important Fixes (7/7)**:
6. **Missing Frontend API Functions**: Added `dashboard.ts` (GET /api/dashboard); sprint.ts gained snapshot/complete-migration/addToSprint; user-level.ts gained getById
7. **Frontend API Dead Code Audit**: Reviewed 15 "unused" functions — all kept as valid backend endpoints awaiting UI integration
8. **NotificationService SQL Injection Risk**: `.last("LIMIT " + limit)` replaced with MyBatis-Plus `Page<Notification>` pagination
9. **Hardcoded Sprint Params**: Default days(14) and quick-create tasks(5) → `@Value("${sprint.default-days:14}")` / `${sprint.quickCreate-tasks:5}`
10. **Hardcoded Approval Expiry**: 7-day timeout → `@Value("${approval.expire-days:7}")`
11. **Hardcoded Chat Recall Window**: 2-minute limit → `@Value("${chat.recall-minutes:2}")`
12. **Comment Deletion No UI Entry**: Added `deleteComment()` API + delete button in CommentSection.vue with confirmation dialog

**P2 — Experience Optimizations (6/6)**:
13. Online count label corrected ("X 位成员" instead of "X 人在线" — real online tracking needs WS connection counting)
14. `window.location.href` → `router.push('/teams')` for team leave action
15. Dashboard quick actions fixed duplicate routes → `/projects/board`, `/projects/sprint`, `/reports`
16. Kanban column names hardcoded Chinese → `@Value("${board.default-columns:...}")` configurable
17. XSS protection strengthened: `renderMention()` now escapes HTML before @highlighting; `sanitize()` adds embed/javascript:/data: protocol filtering
18. Chat recall window already covered by P1-11 config

**New Configurable Parameters** (in `application.yml`):
```yaml
sprint:
  default-days: 14
  quick-create-tasks: 5
approval:
  expire-days: 7
chat:
  recall-minutes: 2
board:
  default-columns: "待办|#909399,进行中|#409EFF,已完成|#67C23A"
```

**Files modified (25+)**:
- Backend: AiController, IssueService, TeamService, ApprovalService, NotificationService, SprintService, ApprovalController, ChatService, ProjectService, init.sql, BatchContext.java (new)
- Frontend: api/dashboard.ts (new), api/sprint.ts, api/user-level.ts, api/comment.ts, TeamDetail.vue, Dashboard.vue, CommentSection.vue, markdown.ts
- Docs: All four docs synchronized

### v2.7.2 — Approval Notification Feedback + Auto-Degrade Fallback + Full User Level Display (2026-06-14)

Three bug fixes and one new feature to improve approval workflow UX.

**Fix 1 — Team Detail Project 500 Error (Auto-Degrade)**:
- Root cause: `TeamDetail.vue` `handleCreateProject()` / `handleDeleteProject()` had no error handling for backend 403/500 responses — non-admins saw raw "服务器错误"
- Fix: Three-layer defense:
  - Layer 1: Non-admins directly call `createApproval()` (skip backend API)
  - Layer 2: Admins call API, but if backend returns **403 or 500**, auto-degrade to approval request
  - Layer 3: Approval request failure shows friendly message instead of generic 500 toast
- Same fix applied to `ProjectList.vue` handleDelete()

**Fix 2 — Approval Result Notification**:
- Backend: `ApprovalService.approve()` / `reject()` now calls `sendApprovalNotification()` to write notification to DB
- New notification types: `APPROVAL_APPROVED` (green), `APPROVAL_REJECTED` (orange with reason)
- Frontend: `MainLayout.vue` `checkApprovalNotifications()` on mount → fetches notifications → shows ElNotification popup for unread approval notifs
- Bell icon badge already works via existing `useNotifications.ts` composable

**Fix 3 — User Level List Missing Users**:
- Root cause: `UserLevelService.listAll()` only queried `user_levels` table — users without level records were invisible
- Fix: Now queries all users from `users` table first, then left joins `user_levels`; no-level users default to L1

**Fix 4 — ApprovalController Principal Type Safety**:
- Root cause: `(Long) auth.getPrincipal()` could fail if principal type changed
- Fix: Changed to `instanceof Number` check with step-by-step validation (principal → userId → user → role)
- Split into three distinct exceptions: UNAUTHORIZED(401), USER_NOT_FOUND(1003), FORBIDDEN(403)
- Same fix applied to `cleanupExpired()` method

**Fix 5 — UserBadge UI Size**:
- Adjusted badge sizing: medium=12px (was 11px), small=11px (was 10px), large=14px (was 13px)
- Added `line-height: 1.6` for better vertical alignment

**Files modified (8)**:
- Backend: `ApprovalService.java` (notification send), `ApprovalController.java` (type safety), `UserLevelService.java` (full user list)
- Frontend: `TeamDetail.vue` (auto-degrade), `ProjectList.vue` (auto-degrade), `MainLayout.vue` (approval notification popup), `UserBadge.vue` (size fix)
- Docs: All three docs synchronized

### v2.7.1 — Approval Permission Enforcement + Global Approval List (2026-06-13)

Critical bug fixes to harden the approval system — previously non-admins could bypass approval by calling backend APIs directly.

**Bug 1 — Frontend API Path Errors (reject/withdraw/cleanup)**:
- `approval.ts`: Fixed 3 API endpoints that had duplicate `/api` prefix (`/api/api/approvals/...` → `/api/approvals/...`)
- Affected: `rejectRequest`, `withdrawApproval`, `cleanupExpiredApprovals`

**Bug 2 — Admin Approval List Empty**:
- Root cause: `ApprovalList.vue` was calling `getPendingApprovals(0)` with teamId=0, which never returns results
- Fix: New backend endpoint `GET /api/approvals/pending` (admin-only, returns all pending approvals across all teams)
- Frontend: Changed to call `getAllPendingApprovals()` instead
- Backend: Added `ApprovalService.listAllPending()` + `ApprovalController.listAllPending()`

**Bug 3 — Delete Project Bypassed Approval**:
- `ProjectController.create()` and `deleteProject()` had no permission check — anyone could create/delete directly
- Fix: Both endpoints now check `teamService.isTeamAdmin(teamId, userId)` before proceeding
- Non-admins get 403 Forbidden with message "只有管理员可以创建/删除项目，请通过审批申请"

**Bug 4 — Compilation Error in ApprovalService.java:214**:
- `projectService.create(createReq)` missing second parameter `requesterId`
- Type mismatch: return type is `ProjectVO`, not `Project`
- Fixed both issues

**Files modified (6)**:
- Backend: `ProjectController.java` (permission checks), `ApprovalService.java` (listAllPending + fix), `ApprovalController.java` (global pending endpoint)
- Frontend: `approval.ts` (path fix + getAllPendingApprovals), `ApprovalList.vue` (use global API)
- Docs: All three docs synchronized (接口文档.md, 操作手册.md, AGENTS.md)

### v2.7.0 — Team Admin Permission + Approval System + User Level L1-L4 (2026-06-12)

Major feature release with three new subsystems: team-based permission control, approval workflow, and user level management.

**Feature 1 — Team Project Visibility Fix**:
- `ProjectService.listByUser()`: Changed from querying user-associated projects (via project_members) to querying all projects from teams the user belongs to
- Project list now shows all team projects regardless of creator
- Auto-adds team members as project members when creating new projects

**Feature 2 — Approval System**:
- New entity: `ApprovalRequest` — tracks approval requests with status (PENDING/APPROVED/REJECTED)
- New service: `ApprovalService` — handles request creation, approval, rejection logic
- New controller: `ApprovalController` — REST endpoints for approval workflow
- New table: `approval_requests` (migration V5)
- API endpoints: POST /api/approvals, GET /api/approvals/team/{id}/pending, PUT /api/approvals/{id}/approve, PUT /api/approvals/{id}/reject
- Target types: PROJECT_CREATE, PROJECT_UPDATE, PROJECT_DELETE, PROJECT_MEMBER_ADD

**Feature 3 — User Level System**:
- New entity: `UserLevel` — stores user level (1-4), badge color, icon
- New service: `UserLevelService` — CRUD operations for user levels
- New controller: `UserLevelController` — admin-only endpoints for level management
- New table: `user_levels` (migration V5) with initial data (admin=L4, others=L1)
- API endpoints: GET /api/user-levels/my, GET /api/user-levels/list, PUT /api/user-levels/{userId}/level

**Frontend changes**:
- `LevelManage.vue` — new page at `/admin/levels` for admin to manage user levels
- `UserBadge.vue` — reusable component showing level badge with progressive styling
- `Profile.vue` — integrated user badge display in profile hero area
- `api/approval.ts` — new API module for approval and level operations
- Router updated with `/admin/levels` route (admin-only)

**Database migration**: `V5__team_admin_approval_user_level.sql`
- Creates `approval_requests` table (11 columns)
- Creates `user_levels` table (7 columns)
- Initializes default user levels for existing users

### v2.6.0 — Kanban Tab Switch, Delete/Move Fix (2026-06-12)

Two critical bug fixes and one UI enhancement for kanban board.

**Bug 1 — Issue Delete 500 Error**:
- Root cause: `IssueService.delete()` didn't clean up related notifications (notifications table has related_id referencing issue)
- Fix: Added cleanup of notifications (`relatedType='ISSUE'`) and child tasks (set parentId to null) before deleting the issue
- Full cascade delete order: comments → attachments → labels → notifications → child task parent reset → issue itself
- All operations in single `@Transactional` — any failure rolls back entire deletion

**Bug 2 — Issue Move 500 Error for BUG type**:
- Root cause: `BoardService.moveIssue()` directly set bugStatus without validating state transition rules
- Fix: Added `isValidBugTransition()` method with full transition matrix validation
- If transition is invalid: only update position (columnId + sortOrder), skip status update — no error thrown
- Extracted `logMoveActivity()` and `broadcastMove()` helper methods for cleaner code

**Feature — Kanban Tab Switch**:
- `ProjectBoard.vue`: Added tab bar above kanban columns to switch between "工作项" (Tasks) and "缺陷" (Bugs)
- Tasks tab: Shows STORY + TASK in classic 3-column layout (TODO/IN_PROGRESS/DONE)
- Bugs tab: Shows BUG items in 6 dedicated status columns (NEW/CONFIRMED/IN_PROGRESS/RESOLVED/VERIFIED/CLOSED), full-width layout
- Tab switch auto-syncs with type filter buttons
- Each tab shows count badge of its item type

**Files modified (3)**:
- Backend: `IssueService.java` (delete cascade), `BoardService.java` (move validation)
- Frontend: `ProjectBoard.vue` (tab switching UI)

### v2.5.0 — Chat Recall, Single-Session Login, @Mention (2026-06-11)

Four bug fixes and feature enhancements discovered through team chat usage.

**Bug 1 — Message Recall**:
- `ChatMessage` entity: added `recalled` Boolean field (DB migration V4)
- `ChatService.recallMessage(messageId, userId)`: validates sender ownership + 2-minute time limit + WS broadcast recall event
- `ChatController`: `PUT /api/chat/recall/{messageId}` endpoint
- `TeamDetail.vue`: hover shows "撤回" button on own messages; recalled messages rendered as gray italic "消息已撤回"

**Bug 2 — Single-Session Login (Kick Previous Session)**:
- `UserService.login()`: writes new token to Redis key `mimo:token:{userId}` (24h TTL)
- `JwtAuthenticationFilter`: on each request, compares request token with Redis-stored token; mismatch → 401 JSON "账号已在其他设备登录"
- Frontend `request.ts` 401 interceptor: clears user localStorage on 401

**Bug 3 — Fix Misleading 403**:
- Root cause: invalid/expired JWT was passed through to Spring Security's authorization phase, which returned 403 instead of 401
- Fix: `JwtAuthenticationFilter` now returns `Result.fail(401, ...)` directly via response writer for invalid/expired tokens

**Bug 4 — @Mention in Team Chat**:
- `TeamDetail.vue` chat input: typing `@` triggers member popover (fuzzy search, max 8 results)
- Clicking member inserts `@username ` at cursor position
- Message rendering: `renderMention()` regex replaces `@xxx` with green highlighted `<span>`

## Future Roadmap — v3.0 (参考伏羲管理系统对标)

对标对象：[伏羲管理系统](https://fuxi.xiaomalezhi.com) — MIT 开源企业级一体化平台 (Spring Boot + Vue 3 + Flowable + 多租户)。模块化拆分：`fuxi-module-system/infra/mall/crm/erp/oa`。核心特性：统一身份认证、多租户隔离、动态菜单+按钮级权限、Flowable 工作流（动态表单/在线设计器/会签或签）、钉钉/企微/公众号集成、代码生成器、MinIO/阿里云/腾讯云/七牛云文件存储、Redis+Redisson 缓存与分布式协作、RabbitMQ/Kafka/RocketMQ 消息队列、Spring WebSocket 集群。

### 借鉴点与改造方向

**1. 工作流引擎升级 — 从状态机到 BPMN 流程引擎**
- 现状：Phase 2 已实现看板状态机（节点+转换规则），仅支持状态流转校验
- 伏羲：使用 [Flowable](https://www.flowable.com/open-source)（BPMN 2.0），支持动态表单、在线流程设计器、会签/或签、服务任务、流程图可视化、历史归档
- 改造方向：保留现有看板状态机（用于 Issue 状态流转），**新增独立 BPM 模块**用 Flowable 处理审批类业务（请假、报销、采购、合同、需求评审、Bug 修复确认）
- 复杂度：中（引入 Flowable Starter + BPMN 设计器 + 待办/我的申请/流程监控页面）

**2. 多租户隔离 — 从单团队到 SaaS 多租户**
- 现状：Team + Project 两层组织，无租户概念，单组织部署
- 伏羲：租户隔离在平台层，所有业务模块共用，权限按租户配置
- 改造方向：引入 `tenant_id` 字段贯穿所有业务表 + MyBatis-Plus 多租户拦截器自动注入 + 租户成为最高隔离单元
- 复杂度：中（数据库改动较大，需补数据迁移脚本）

**3. RBAC 细粒度权限 — 从角色到按钮级权限**
- 现状：`Role{ADMIN,MEMBER}` + Team 成员两层，前端 `v-if` 按角色控制可见性
- 伏羲：用户-角色-菜单-部门-岗位-字典，动态菜单+按钮级权限码
- 改造方向：补 `menu` + `permission` 表（权限码如 `project:issue:delete`），前端用 `v-permission` 指令，后端 `@PreAuthorize` 注解
- 复杂度：低（纯加法，不破坏现有逻辑）

**4. 钉钉/企微集成 — 从零集成到公司内可替代钉钉**
- 现状：用户名密码 + JWT，无第三方登录
- 伏羲：外部登录（公众号/小程序/企业微信/钉钉）、支付（支付宝/微信）、文件存储多云端
- 改造方向：
  - P0：钉钉扫码登录 + 免登（企业内部应用）
  - P0：钉钉机器人推送（任务到期/评论提及/Sprint 开始/工作流审批通知）
  - P1：钉钉工作通知 API（替代站内消息）
- 复杂度：低（钉钉开放平台文档完善，SDK 稳定）

**5. 模块化拆分 — 从单模块到多模块 Maven**
- 现状：所有代码在一个 Maven module
- 伏羲：`fuxi-module-system` / `fuxi-module-infra` / `fuxi-module-crm` / `fuxi-module-erp` / `fuxi-module-mall` / `fuxi-module-oa` 独立模块
- 改造方向：拆分为
  - `mimo-common`（实体基类、工具、异常、Result）
  - `mimo-system`（用户/角色/权限/租户/部门）
  - `mimo-project`（项目/任务/看板/Sprint/Gantt）
  - `mimo-bpm`（Flowable 工作流）
  - `mimo-wiki`（知识库）
  - `mimo-app`（启动器，聚合所有模块）
- 复杂度：中（纯结构重构，无业务逻辑变更，但影响面大）

**6. Wiki 知识库 — 新增模块**
- 现状：**v2.13.7 已落地 MVP**（项目级两级目录 + Markdown 编辑 + 版本历史回滚 + 全文检索 + 附件）
- 伏羲：知识库支持多级权限（库级/文件夹级/文档级），可管理/可编辑/可查看/仅可查看
- 改造方向：三级层次（知识库 → 文件夹 → 文档）+ Markdown 编辑器 + 三级权限 + 全文搜索
- 复杂度：中（参考钉钉知识库 API 设计）

**7. 绩效考核 — 完全新增**
- 现状：无 KPI 模块
- 伏羲：通过钉钉 Agoal 关联，OKR + KPI 双轨、360 度评估、校准会议、雷达图
- 改造方向：MVP 从三个数据源做最小可用版本
  - Issue 工时统计（work_logs 表已有数据）
  - Sprint 完成率（Sprint 已有数据）
  - 360 度评审表（新增表 + 问卷分发回收）
- 复杂度：高（业务设计比技术实现更难）

### 执行优先级

| 优先级 | 模块 | 复杂度 | 收益 | 说明 |
|--------|------|--------|------|------|
| P0 | 模块化拆分 + RBAC 按钮权限 | 低-中 | 高 | 企业级基础，后续所有模块的前置条件 |
| P0 | 多租户隔离 | 中 | 高 | SaaS 化基础，越早做改动越小 |
| P1 | Flowable 审批流 | 中 | 高 | 对齐伏羲核心卖点，从 Issue 工作流扩展到审批工作流 |
| P1 | Wiki 知识库 | 中 | 高 | 缩小与伏羲差距，Phase 2 已规划 |
| P2 | 钉钉登录 + 机器人推送 | 低 | 中 | 公司内可用门槛，免费集成 |
| P2 | 绩效考核 MVP | 高 | 中 | 差异化能力，但依赖前面模块先成型 |

### 不借鉴的部分

- **CRM / ERP / 商城**：Mimo 定位项目管理 + 团队协作，不做经营业务
- **代码生成器**：投入产出比低，Mimo 规模不需要
- **AI Copilot 留作记录**：当前 Phase 已搁置，未来在 Wiki 和 BPM 模块成型后再考虑（智能审批建议、文档总结、知识库问答）


**Files modified (10)**:
- Backend: `ChatMessage.java`, `ChatService.java`, `ChatController.java`, `UserService.java`, `JwtAuthenticationFilter.java`
- Frontend: `TeamDetail.vue`, `request.ts`
- DB: `V4__chat_recall.sql`

### v2.4.0 — Emoji, Enhanced Reports, Audit Log (2026-06-10)

Feature enhancements: emoji picker in team chat, richer report content, and operation audit logging for delete actions.

**Frontend changes**:
- `TeamDetail.vue` — added emoji picker (4 categories: 表情/手势/物品/符号, 200+ emojis) with popover, cursor position preservation
- Report generation now includes: task counts in headers, assignee info, todo preview (top 10), bug summary, project overview stats

**Backend changes**:
- `ReportService.generateDraft()` — enhanced with todo list, bug statistics, project overview section; uses stream filter instead of DB query for completed issues
- `TeamService.deleteTeam()` — adds audit log record before deletion (targetType=TEAM, action=DELETE)
- `ProjectService.deleteProject(projectId, operatorId)` — adds audit log record; updated signature to accept operatorId
- `ProjectController.deleteProject()` — passes userId to service for audit trail

**Audit log fields**: userId, username, projectId, targetType (TEAM/PROJECT), targetId, action (DELETE), detail

### v2.3.1 — Kanban Drag & Status Fix (2026-06-10)

Fixed three critical kanban board issues: status button not moving columns, drag always failing, and Linux deployment drag incompatibility.

**Root causes & fixes**:
- **quickComplete not moving to done column**: `updateIssue` only sent `status='DONE'` without `columnId` → now finds done column by name and sends both `status` + `columnId` (+ `bugStatus='CLOSED'` for BUG type)
- **Drag always showing "移动失败" toast**: Filtered items used `display: none` which broke sortablejs index calculation → changed to `visibility: hidden; height: 0; overflow: hidden` to keep items in document flow
- **Linux drag not working**: Removed `force-fallback: true` (HTML5 DnD fallback mode) → using native HTML5 Drag and Drop API for better cross-platform compatibility

**Files modified**:
- `ProjectBoard.vue` — fixed quickComplete, handleDragChange, draggable config, is-hidden CSS

### v2.3.0 — Delete Operations (2026-06-10)

Added delete functionality for teams and projects across all relevant UI locations.

**Backend changes**:
- `TeamService.java` — added `deleteTeam(teamId, operatorId)` (owner-only, cascades team members)
- `TeamController.java` — added `DELETE /api/teams/{teamId}`
- `ProjectService.java` — added `deleteProject(projectId)` (cascades board columns, issues, members, reports, sprints)
- `ProjectController.java` — added `DELETE /api/projects/{projectId}`

**Frontend changes**:
- `TeamList.vue` — added delete button on each team card (with confirmation dialog)
- `ProjectList.vue` — added delete button on each project card (with confirmation dialog)
- `TeamDetail.vue` — added delete button on each project card in team's project list
- `api/team.ts` — added `deleteTeam(teamId)`
- `api/project.ts` — added `deleteProject(projectId)`

### v2.2.0 — Team Chat & UI Differentiation (2026-06-10)

Team collaboration enhancement with real-time group chat, report-kanban integration, and visual differentiation between team and project pages.

**Backend new files**:
- `entity/ChatMessage.java` — chat message entity (teamId, senderId, content, createdAt)
- `mapper/ChatMessageMapper.java` — mapper with `findRecentByTeamId()` (last 100 messages)
- `dto/ChatMessageDTO.java` — SendRequest / ChatMessageVO / ChatEvent (WS broadcast DTO)
- `service/ChatService.java` — send message (persist + WS broadcast) + load history
- `controller/ChatController.java` — `POST /api/chat/send`, `GET /api/chat/history/{teamId}`
- `db/migration/V3__chat_messages.sql` — `chat_messages` table

**Backend modified**:
- `WebSocketService` — added `sendTeamChat(teamId, event)` for `/topic/team-chat/{teamId}` broadcast

**Frontend — team UI redesign**:
- `TeamList.vue` — complete redesign: **green theme** hero with decorative orbs, horizontal card layout with left accent bar, avatar ring, hover glow effects
- `TeamDetail.vue` — **dual-column layout**: left = members + projects; right = sticky **group chat panel**; green-gradient hero header
- Team pages now visually distinct from project pages (green vs indigo palette)

**Frontend — kanban-report integration**:
- `ProjectBoard.vue` toolbar: "报告" button with **recent 7-day report count badge**; "新建工作项" → dropdown menu (新建/生成日报/生成周报)
- `ProjectBoard.vue` bottom: **project overview stats bar** showing total/todo/in-progress/done/bug counts with color coding + "查看报告与数据看板" link

**Frontend — group chat panel** (`TeamDetail.vue`):
- Real-time message list via WebSocket subscription to `/topic/team-chat/{teamId}`
- Message bubbles: self-sent (green gradient, right-aligned) vs others (gray, left-aligned)
- Date divider lines between different days; sender name + timestamp per message
- Unread badge on Hero "群聊" button; auto-clear on scroll-to-bottom
- Enter to send; textarea autosize up to 4 lines; loading states
- Responsive: chat panel moves below on narrow screens (<900px)

**Database**: New `chat_messages` table (InnoDB, indexed on team_id+created_at)

### v2.1.0 — UI Enhancement & Command Palette (2026-06-10)

Full-stack UI polish and productivity features.

**Backend**:
- `IssueVO.projectId` — returned on create/get/query; enables notification deep-link and global search navigation

**Frontend — visual**:
- `AuthLayout.vue` — split-screen login/register with product preview
- Dashboard hero banner, 4 stat cards (incl. completion rate), sprint banner, activity timeline
- `PageHeader.vue`, list-card grids for TeamList / ProjectList / TeamDetail
- Profile hero + settings cards; dark mode via `[data-theme="dark"]` and `appStore.toggleDarkMode()`
- ProjectOverview stat pills + chart card headers; ReportList table card wrapper
- Design tokens in `variables.css` (Indigo palette, gradients); Inter font; custom favicon

**Frontend — interaction**:
- `CommandPalette.vue` + `useCommandPalette.ts` — Ctrl+K global search (issues via `POST /api/issues/query`)
- Board swimlanes (assignee / epic), filter presets (全部/缺陷/故事/我的)
- `IssueCard` hover actions: quick complete + edit
- Notification click → `/projects/{projectId}/board?issue={id}`
- Board deeplink `?create=1` for command palette "新建工作项"

### v2.0.0 — Issue Type Specialization (2026-06-08)

Full-stack differentiation of STORY/TASK/BUG with per-type data models, workflows, and UI.

**Database**: Added 13 columns to `issues` table:
- STORY: `user_role`, `user_goal`, `business_value`, `acceptance_criteria` (JSON), `epic`, `parent_id`
- BUG: `environment`, `expected_result`, `actual_result`, `found_version`, `fixed_version`, `bug_status`
- Migration: `backend/src/main/resources/db/migration/V2__issue_type_specialization.sql`

**Backend new endpoints**:
- `POST /api/issues/{id}/acceptance-criteria` — add AC to STORY
- `PUT /api/issues/{id}/acceptance-criteria/{criteriaId}` — update/toggle AC
- `DELETE /api/issues/{id}/acceptance-criteria/{criteriaId}` — remove AC
- `PUT /api/issues/{id}/bug-status` — BUG status transition (with validation matrix)

**Frontend new components**:
- `StoryCard` / `TaskCard` / `BugCard` — color-coded type-specific cards
- `StoryDialog` / `TaskDialog` / `BugDialog` — per-type form dialogs
- `AcceptanceCriteria` — checkable criteria list for STORY
- `BugTableView` — table view for bug tracking
- `StoryMap` — epic-grouped story map view
- `CommentSection` — reusable comment component

**View modes** (ProjectBoard): Kanban / Bug Table / Story Map, with type quick-filter buttons

**st2 test suite**: 38 API test cases + 11 UI test cases covering all three types

**Bug fixes included**:
- Unified priority selector (removed duplicates from sub-dialogs)
- Fixed BUG environment field not loading back on edit
- Enabled acceptance criteria in create mode (was disabled)
- Cleaned garbage `board_columns` rows from database
- Unified "新建工作项" button naming across pages

### v1.x (e810514 and earlier)
Core platform: auth, team/project management, basic kanban board, comments, notifications, reports, sprint/burndown, attachment upload, WebSocket real-time sync, AI polish/priority analysis.
