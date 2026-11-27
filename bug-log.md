# Mimo Bug Log

记录 Mimo 项目自开发以来遇到并修复的所有 Bug，按版本倒序排列。

---

## v2.22.4 — 量级验证：把「性能」从推理变成数字（BUG-126~135）(2026-11-27)

> 上一轮把闸门修好了（CI 真实 runner 全绿）。这一轮问的是另一个问题：
> **"功能都对"之后，这些接口在人多的团队里还站得住吗？**
>
> 答案在动手之前是"不知道"——开发库常年是 **3 个用户 / 20 个工作项 / 1156 条活动日志**，
> 所有关于"分页了""批量查了"的结论都是读代码推出来的，没人量过。这一轮先造量，
> 再量，再改，最后给改前/改后的数字。

### 先说怎么量（这一轮的方法本身要能复现）

| 环节 | 做法 | 文件 |
|---|---|---|
| 造量 | 100 用户 / 1 团队 / 5 项目 / 500 工作项 / 1000 评论 / **5000 活动日志**，全部挂在一个独立「压测团队」下，**不掺进演示数据** | `tools/seed-load.sql`（幂等，751ms 灌完） |
| 清量 | 按团队/项目整块删干净，跑完 e2e 前必须清（否则 100 个成员会把全员任务流撑大） | `tools/cleanup-load.sql` |
| 量延迟 | 对 18 个核心读接口打 N 轮，给出 p50 / p95 / max，按 p95 倒序 | `e2e/perf-probe.mjs` |
| 量 SQL 条数 | `SHOW GLOBAL STATUS LIKE 'Com_select'` 前后差值（`mimo` 账号没有 SUPER，`general_log` 开不了，这条路能走通） | 手工，命令见下 |
| 找热点 | 一边压一边用 `jcmd Thread.print` 采样后端线程栈 | 手工 |

```bash
# SQL 条数：改前/改后用的都是这一条
mysql -umimo -p -N -B -e "SHOW GLOBAL STATUS LIKE 'Com_select';"   # 请求前
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/workbench/team
mysql -umimo -p -N -B -e "SHOW GLOBAL STATUS LIKE 'Com_select';"   # 请求后，相减
```

### BUG-126: 全员任务流给每个成员算一次「是不是管理员」，100 人 = 606 条 SELECT [Medium]

- **现象**：`/api/workbench/team?memberPage=1&pageSize=8` 在 100 人的团队上 **433ms、606 条 SELECT**；
  同一个接口在 3 个成员的开发库上是 30ms 上下，所以一直"看着挺好"。
- **根因**：`WorkbenchService` 里每个成员调一次 `accessControlService.isTeamAdmin(userId, teamId)`，
  单人判定要 6 条 SELECT（旧字段 `users.role` 1 条 + 旧字段 `team_members.role` 1 条 +
  RBAC 角色 2 条 + RBAC 权限 2 条）。101 个成员 × 6 = 606，条数随人数线性涨。
- **修复**：新增批量判定 `AccessControlService#filterTeamAdmins(userIds, teamIds)`，条数固定
  （旧字段 2 条 + 系统管理员口径 2 条 + 每个团队 2 条，与人数无关）。
  口径**一个字没改**：旧字段 ∪ RBAC，命中即管理员——并且专门写了一条"批量版必须等于逐人版"的
  等价性用例（`batchShouldAgreeWithSingleUserCheck`），四个用户分别覆盖旧字段 / RBAC 全局 /
  RBAC 团队作用域 / 纯成员，两条路径逐人比对。
- **效果**：**606 条 → 28 条 SELECT，p95 540ms → 125ms**。

### BUG-127: 项目统计卡片按成员/按迭代逐条查，40 条 SELECT 只为画 2KB 的图 [Medium]

- **现象**：`/api/reports/stats/{projectId}` 在 100 人项目上 **40 条 SELECT / 108ms**，
  而响应体只有 1.9KB（三张小图）。
- **根因**：成员分布是 `for (成员) userMapper.selectById(...)`；迭代速度是
  `for (迭代) selectCount × 2`。
- **修复**：成员一次 `selectBatchIds`；迭代那几轮的工作项一次取回内存里数。
  这个接口此前**一个测试都没有**，本轮补了 `ReportServiceTest`（4 条），既守数字也守条数
  （`verify(userMapper, never()).selectById(...)`、`verify(issueMapper, never()).selectCount(...)`）。
- **效果**：**40 条 → 8 条 SELECT**；迭代速度与库里真值对齐（100 条 / 33 条 DONE 都对上）。

### BUG-128: 近 7 天趋势把明细整批搬进 JVM，5 千条日志就卡在这儿 [Medium]

- **现象**：改完上面两条，接口还剩 250ms 左右；线程栈采样 6 次里有 **4 次**停在
  `WorkbenchService.buildTrend` 的那条 `activityLogMapper.selectList` 上。
- **根因**：为了画 7 个点，把近 7 天所有 `CREATE`/`MOVE` 明细（含 `detail` 文本）全查进内存再数。
  开发库一天几十条看不出来，5000 条活动日志时就是几百毫秒。
- **修复**：聚到下推到库里（`GROUP BY DATE(created_at)`，最多回 7 行）。
  「完成」的判定仍然只在 Java 侧一处（`BoardColumnRules.isTerminalName`）：终态列名由 Java 算好传给 SQL，
  SQL 只做"描述里的目标列名 = 该项目现有的某个终态列名"，并要求该项目**当前确实存在**这个列
  ——列被改名后更早的历史事件照旧跳过，少算不多算，与改造前一致。
  等价性用真实数据验：改前改后 7 天趋势**逐日逐字段完全相同**
  （11-21 新建 83、11-23 100、11-24 119、11-25 37、11-26 123/完成 6、11-27 96/完成 2）。
- **效果**：这一段从"几百行明细"变成 2 条聚合查询、共 7 行返回值。

### BUG-129: 在线状态逐人问 Redis，100 个成员 = 200 次往返 [Low]

- **现象**：`PresenceService#filterOnline` 对每个成员调一次 `isOnline`，Redis 实现每次 2 次往返
  （`SISMEMBER` + `SCARD`）。100 个成员就是 200 次网络调用，比查库还贵。
- **修复**：先取一次在线集合 `store.onlineUserIds()`（在线人数天然很小），再在内存里求交集。
- **说明**：这条在内存实现下几乎无感，是 Redis 模式下才显现的成本。

### BUG-130: 双实例 CI job 先灌种子再起后端，空库里没有表可灌 [Medium]

- **现象**：新加的 `two-instance` job 第一次在真实 runner 上跑，
  第 7 步「灌演示账号」**1 秒内红**（`Table 'mimo.users' doesn't exist`），后面三步全 skipped。
  同一份 workflow 里的主 job 是绿的，所以这不是"CI 环境不行"，是**我新写的那一半不对**。
- **根因**：步骤顺序写反了。`seed-demo.sql` 灌的是 `users` / `teams` / `team_members`，
  而空库里**一张表都还没有**——建表是后端启动时 `MigrationRunner` 干的活。
  主 job 里同样的命令能跑，是因为主 job 的顺序是「启动后端（建表）→ 灌种子」。
  我排新 job 时凭印象写，没照着旁边那个已经绿了三次的 job 抄。
- **修复**：`前端依赖 → 启动两个实例（等登录接口就绪）→ 灌种子 → 双实例验证`。
  管理员由 `MIMO_BOOTSTRAP_ADMIN_*` 在启动时自动建出，所以探活不依赖种子；
  种子里才有的是 `lisi` 与团队 1，双实例用例要它们，所以放在启动之后。
- **顺带修的一个更隐蔽的问题**：`perf-probe.mjs` 第一版"永远退出 0"——登录失败、
  接口全 404 也是一片绿，**这正是 KNOWN-024 那个老毛病（闸门装了没人读结果）的翻版**，
  只不过这次闸门是我自己刚焊上去的。改成：慢不报错（仍然不设门槛），
  但"登录失败 / 有接口没跑通 / `--only` 没匹配"一律非 0；
  另外没找到「压测团队」时明确打印"本次只代表空库基线，别拿它下结论"。
- **教训**：CI 步骤的**顺序就是依赖关系**（建表 → 种子 → 用例），不是排版。
  旁边已经有绿的 job 时，抄它的顺序比"自己再想一遍"快得多，也安全得多。

### BUG-131: 「后端就绪」其实是假的——HTTP 端口比迁移先开，登录能在建表中途成功 [Medium]

- **现象**：修完 BUG-130 再推，主 job 反而红了，红在「建表结果自检」：
  **只建出 25 张表**（应为 38）。而前一步「启动后端」是绿的——它靠登录接口探活，
  也就是说**接口能登录、库却没建完**。同一份 workflow 前三次都过了，只有这次撞上。
- **根因**：Spring Boot 的启动顺序是「先开 HTTP 端口 → 再跑 `ApplicationRunner`」，
  而建表恰好写在 `ApplicationRunner`（`MigrationRunner`）里。迁移跑到一半时 `users` 表已经存在，
  `AdminBootstrap` 与登录都正常，于是"探活通过"和"库建完"不是一回事。
  之前的绿灯全是靠迁移够快（CI 上 10 秒内跑完）凑出来的，属于**时序运气**，不是正确性。
- **修复**：就绪判定改成两个条件同时成立——
  ① `grep -q "Started MimoApplication" backend.log`（这句日志在 Runner 全部跑完之后才打，
  等于"迁移完成"）；② 登录接口能过。另外把「建表结果自检」改成"最多等 60 秒再看，
  最后一次不达标才红"，并在失败时打印 `MigrationRunner` 日志与现有表清单
  （`MigrationRunner` **不写迁移记录表**，只打日志，所以翻日志才是真相）。
  双实例 job 的两个实例同步改成同样的判定。
- **教训**：**"服务能响应"不等于"服务准备好了"。** 就绪信号要选那个"依赖全部完成之后才会出现"的证据
  （本例是一句日志），而不是"最早出现的那个能用的东西"（本例是 HTTP 端口）。
- **⚠️ 补记（v2.22.4，BUG-134）：这条的修法是错的。** 当时认定
  `Started MimoApplication` "在 ApplicationRunner 之后才打"，据此把它当成就绪信号——
  本地拿空库实测，这句在 **14:25:07.737** 打出，最后一条迁移
  `[MigrationRunner] 全部迁移完成` 在 **14:25:15.345** 才出现：**早了 7.6 秒，方向完全反了**。
  也就是说 CI 的"启动后端"这步从那以后一直在假就绪放行；#14/#15 能绿，靠的是下一步
  「建表结果自检」自己带了 60 秒轮询在兜底，不是这个信号起的作用。
  真信号见 BUG-134。

### BUG-132: 索引"有"但用不上——三条热点查询全表扫，另有一个索引和它的兄弟完全重复 [Medium]

- **现象**：N+1 都拆完之后 `/workbench/team` 还剩 125ms。用 `EXPLAIN ANALYZE` 把趋势那两条聚合查询的
  真实计划拉出来，看到的是 `type=ALL`（扫 40000 行 `activity_logs` 再筛）、
  「移动」那条按项目 join 时每个项目先捞 7000+ 行。
- **根因**：`activity_logs` 上其实有三个相关索引（`idx_project` / `idx_created` / `idx_created_at`），
  但**都是单列的**。查询形状是 `project_id IN (...) AND action = ? AND created_at >= ?`，
  一条单列索引只能命中其中一个条件，剩下两个还得逐行筛——优化器干脆挑"扫全表"。
  顺带看出另一件事：`idx_created` 与 `idx_created_at` 是**同一个索引**（都只含 `created_at`），
  V16 加索引时没注意到 V0 里已经有一个同构的兄弟，白写一份。
- **修复**：`V25__hot_query_indexes.sql`——加 `(project_id, action, created_at)` 复合索引，
  删掉重复的 `idx_created`。合并了"加该加的"和"删不该留的"两件事。
- **效果**（同一份量级数据：4 万行活动日志，近 7 天窗口内 3.6 万行）：

  | 量什么 | 改前 | 改后 |
  |---|---|---|
  | 新建趋势（EXPLAIN ANALYZE 实测） | 31.4ms（扫 40104 行） | **16.4ms**（覆盖索引定位） |
  | 移动趋势（同一份数据） | 63.7ms（每项目捞 7774 行） | **47.2ms**（索引条件下推，每项目 4008 行） |
  | 接口 `/workbench/team`（A/B 关掉/装回索引，缓存已热，12 轮） | p95 **387ms** / p50 369ms | p95 **141ms** / p50 116ms |

  第一次 A/B 量出的是 759ms → 177ms（4.3 倍），**不敢用**：那一轮 MySQL 缓冲池还是冷的，
  慢的一部分是"从磁盘读"，不是"没索引"。反向再做一遍（先撤索引、再装回）才确认：
  稳的是 387 → 141。**量出来的数要先问一句"这是不是我刚改的那个东西造成的"。**
- **教训**：**索引不是"加到就有用"**。同一轮里有两个看着更该建的，量完决定不建（见下）。

### 顺带：量完之后**决定不建**的两个索引（留证据，省得下次再来一轮）

| 候选 | 直觉理由 | 实测 | 结论 |
|---|---|---|---|
| `issues (project_id, deleted)` | 看板/报表/工作台都带 `project_id = ? AND deleted = 0` | 1.7 万行副本上：选中 17%（单项目）时全表扫 6.45ms、走索引 **7.65ms**（更慢）；选中 87%（5 个项目）时两者都在 7~8ms，优化器直接忽略索引 | **不建**，留 `idx_project` |
| `issues (sprint_id, status)` | 迭代速率统计只要 3 列，能做成覆盖索引 | 选中 0.9% 的迭代时 6.47ms → **0.33ms**（约 20 倍）；选中 87% 时优化器正确改回全表扫 | **建**（选择性好就赚、差也不亏） |

原因很实在：压测数据里同一项目的行是连续插入的，聚簇索引本来就把它们摆在相邻页上——
顺序扫比"3000 次索引查找 + 回表"更便宜。**"这条 SQL 有 WHERE 就建索引"是错的直觉。**

### BUG-133: 压测数据不像真数据，量出来的是"空转"的时间 [Low]

- **现象**：第一版 `tools/seed-load.sql` 把活动日志的 `detail` 写成了 `压测活动 LOAD1-1 #123`。
  于是「移动到终态列」那条趋势查询在压测数据上**一行都统计不到**——它靠
  `TRIM(SUBSTRING_INDEX(detail,'移动到',-1)) = 列名` 判定"完成"。
  我拿这条查询量了耗时，得到"几毫秒、挺快"的结论：**那是查询立刻返回空的时间。**
- **根因**：造数据时只想着"数量要够"，没想"文案要被代码认得"。压测数据不进代码路径，量出来的就不是那条路径。
- **修复**：`detail` 改成真实文案（`创建工作项 X` / `将 X 移动到 已完成|进行中|待办`），
  并在 seed 末尾多打一列 `move_to_done`，让人一眼看到"这条判定路径到底有没有数据"
  （现在是 5000 条里 556 条移到已完成，近 7 天查出 28/28/14/42 的真实日分布）。
- **连带补掉一个危险条件**：`tools/cleanup-load.sql` 原本是
  `WHERE project_id IN (压测项目) OR detail LIKE '压测活动 %'`——那个 `OR` 是"按文案删"，
  靠的是只有压测数据才有的前缀。一旦把新文案塞进去，这个 `OR` 就会把**演示项目和 e2e 的正常活动日志一起删掉**。
  改成：先把压测项目 id 抓进临时表，全部按 id 删，一句文案匹配都不留。
- **教训**：**压测数据的形状也是被测代码的一部分。** 数据不像真的，量出来的"快"可能是没干活。

### BUG-134: 就绪信号指错了一行日志——`Started MimoApplication` 比迁移跑完还早 7.6 秒 [Medium]

- **现象**：BUG-131 之后 CI 一直绿，所以没人再看这个判定。这一轮本地起空库时顺手看了一眼日志顺序：

  ```
  14:25:07.737  Started MimoApplication in 13.848 seconds
  14:25:15.345  [MigrationRunner] 完成: db/migration/V9__issue_gantt.sql
  14:25:15.345  [MigrationRunner] 全部迁移完成: 25 个脚本
  ```

  也就是说 `grep "Started MimoApplication"` 在迁移**开始之前**就匹配上了。
- **根因**：Spring Boot 的启动顺序是「刷新上下文（打完 `Started …` 日志）→ 跑 `ApplicationRunner`」，
  而建表在 `ApplicationRunner` 里。BUG-131 的推断（"这句在 Runner 之后才打"）是错的——
  那轮之所以看起来修好了，是因为下一步「建表结果自检」自己带了最多 60 秒轮询，
  把真相盖住了。**一个假信号被另一个假信号替换，然后被第三步的耐心救了。**
- **修复**：`MigrationRunner` 在循环结束后打一行
  `[MigrationRunner] 全部迁移完成: N 个脚本`，CI 的两个 job 都改成等这一行 + 登录能过；
  失败时除了 tail 日志，还会打印"这行出现次数"和"最后 10 条迁移日志"，
  下次再红能直接看出是"没跑完"还是"登录不通"。
  不按 `完成: V9…` 判：脚本按**字典序**执行，最后一个可能是 `V9`，中间插一个 `V26` 就又不准了。
- **教训**：**判"准备好了"，就去读那个组件的收尾日志，别用框架的通用启动日志猜。**
  而且断言必须能自证：`全部迁移完成: 25 个脚本` 这句话里就带着"跑了几个"，比"出现过某句"结实。

### BUG-135: 双实例同时跑迁移链，8081 180 秒没起来 [Medium]

- **现象**：推上索引那一版（`c495897`）后，[run #16](https://github.com/Oo7350/mimo/actions/runs/45948776881)
  主 job 绿（8 分 16 秒），`two-instance` job 红在第 8 步「启动两个实例」：
  `实例 8081 180 秒内未就绪`。同一份 workflow 前两次（#14/#15）都是绿的。
- **根因（可疑但无法 100% 证实，如实记录）**：两个实例**同时**对着空库跑同一条迁移链，
  两边都在 `CREATE TABLE` / `CREATE INDEX`，撞上就按"已存在"跳过（`MigrationRunner` 的设计），
  谁也不能保证自己那份是完整的；再叠加"就绪判的是假信号"（BUG-134），
  8081 很容易在 180 秒里一直"看起来没起来"。
  本地用同一个 jar、同一个空库、同样的并发顺序起了两遍都成功（各 21 秒就绪），
  所以**没有复现**——只能说这是竞态，不是必现。CI 的日志没下载下来（无 token），
  只能靠现象推断，这一点不含糊其辞。
- **修复**：两个实例**串行启动**——先起 8080、等它 `全部迁移完成` + 能登录，再起 8081。
  多花约 20 秒，换掉一个随机红。另外把就绪判定换成 BUG-134 的真信号。
- **教训**：**"两个进程干同一件初始化的事"是设计问题，不是运气问题。**
  上一次绿不代表这次会绿：能随机红的东西，早晚会红在你要给人演示的那一次。

### 真实 runner 上的最终结果（run #14，commit `76b2a11`）

| job | 耗时 | 结果 |
|---|---|---|
| `e2e`（19 step） | **8 分 45 秒**（04:49:43 → 04:58:28 UTC） | **success**，含新增的「造量级数据 → 性能探针 → 清量级数据」三步 |
| `two-instance`（9 step） | **2 分 1 秒**（04:58:30 → 05:00:31 UTC） | **success**，跨实例在线 + 跨实例广播首次在真实 runner 上通过 |

[run #14](https://github.com/Oo7350/mimo/actions/runs/45942184401)。前一次（#12 主 job 绿、双子 job 红 BUG-130）
和 #13（主 job 红 BUG-131）都留了记录，不抹掉——**这轮 CI 的三次推送，恰好把"新写的闸门自己有没有毛病"
验了一遍**：第一次红在顺序，第二次红在就绪判定，第三次才两个 job 同时绿。
  另外：绿灯连续出现三次也不代表稳——它可能只是没撞上那个窗口。

**索引那一版（run #16，commit `c495897`）又多了一次红，红得很有价值**：

| job | 耗时 | 结果 |
|---|---|---|
| `e2e`（20 step） | 8 分 16 秒 | **success**（含建表自检 38 张表、造量级数据、性能探针、清数据） |
| `two-instance`（11 step） | 4 分 33 秒 | **failure**：`实例 8081 180 秒内未就绪`（BUG-135） |

查这一红的过程还牵出了 BUG-134：本地起空库时发现 CI 判的
`Started MimoApplication` **比迁移跑完早 7.6 秒**，也就是 BUG-131 那次"修复"治的是错的地方，
之前几轮的全绿是下一步自检的 60 秒轮询在兜底。于是这一轮把信号换成
`[MigrationRunner] 全部迁移完成`，并把两个实例改成串行启动。

### 收口时顺手清掉的两笔旧账
| 事项 | 处理 | 结果 |
|---|---|---|
| 计划书的路线图快照还停在 **v2.19.5** | 更新到当前版本，并把"还剩什么"改成实话 | 已改 |
| "示例项目被逻辑删除后行仍在表里" | 新增 `tools/cleanup-soft-deleted.sql`（按项目维度连子行一起物理删除），**先自测**：造一个逻辑删除项目 → 跑脚本 → 校验全清 | 清掉 **3 个逻辑删除项目 / 31 个工作项 / 1 条活动日志 / 1 个看板列** |
| 双实例（跨实例在线+广播）只在开发机跑过 | 写进 CI（新增 `two-instance` job，mysql + redis 两个 service，两个后端进程共用一个 Redis） | 见 CI 记录 |
| `e2e/two-instance-presence.mjs` 写死了 Windows 绝对路径 | 改成按脚本位置解析（`MIMO_FRONTEND_MODULES` 可覆盖） | Linux 上不再 `MODULE_NOT_FOUND` |

> 还剩一类残留没动，写清楚免得下次当成"没清干净"：**42 条工作项级逻辑删除**
> （`issues.deleted = 1`，属于仍然存在的项目）。它们来自 e2e 与「重置演示数据」，
> 项目还在所以按项目维度清理的脚本够不着；而且全库没有回收站/还原功能，
> 真要删得单独决定。`tools/cleanup-soft-deleted.sql` 末尾给了那一句 SQL，注释着，不默认执行。

### 改前 / 改后（同一台机器、同一份量级数据、同一轮 10 次）

| 接口 | 改前 p95 | 改后 p95 | 改前 SELECT | 改后 SELECT |
|---|---|---|---|---|
| `/workbench/team`（100 成员） | 540ms | **125ms**（再加索引：141ms @4 万行日志，见 BUG-132） | 606 | **28** |
| `/reports/stats/{id}`（100 人项目） | 108ms | **< 30ms** | 40 | **8** |
| `/board/{id}`（100 卡片、41KB） | 47ms | 47ms | 7 | 7 |
| 其余 15 个核心接口 | 全部 < 60ms | 同量级 | 每条 ≤ 3 | 同 |

> 一条方法论上的话：**"分页了"不等于"没白干"**。全员任务流早就分了页（每页 8 人），
> 但它仍然把所有成员、所有工作项都算了一遍——分页只省了带宽，没省查询。
> 这类问题只有量出来才会露头，读代码时它长得完全像"已经优化过"。

> 而**"批量查了"也不等于"查得快"**：BUG-126~129 把 606 条 SELECT 压到 28 条之后，
> 接口还剩 125ms，剩下的时间花在"少数几条但扫得很凶"的查询上——
> 这一层只有 `EXPLAIN ANALYZE` 看得见，`Com_select` 数条数发现不了（BUG-132）。

---

## v2.22.3 — 关闭 KNOWN-024：CI 在真实 runner 上第一次全绿 (2026-11-27)

> 这一轮没有新 Bug，是把上一轮"只差人点一下"的那步走完，以及**顺手纠正了一句不准确的记录**。

**真实 runner 上的结果**（`workflow_dispatch`，commit `5747663`）：

| 指标 | 实测值 |
|---|---|
| Run | [#8](https://github.com/Oo7350/mimo/actions/runs/45926155601)，`workflow_dispatch`，commit `5747663` |
| 结论 | **success**（job 35 个 step 全绿，唯一 skipped 的是「失败时留存日志」——它本来就只在 failure 时跑） |
| 总耗时 | **8 分 36 秒**（02:04:39 → 02:13:15 UTC） |
| 后端单测 | 31 秒（225 项，与本地一致） |
| 空库建表 | 成功，`建表结果自检` 通过（门槛 ≥30，本地实测 38） |
| 灌种子 / 预热项目 | 各 1 秒内（`docker exec` + `seed-fresh-db.mjs` 都通了） |
| 前端依赖+类型检查+构建 | 56 秒（`npm ci` + `vue-tsc` + `vite build`） |
| 安装 Playwright | 19 秒（`playwright@1.65.1` + `--with-deps chromium`） |
| **跑全部 e2e** | **5 分 41 秒，success**（15 套 148 条 + 3 探针） |

**不是一次偶然**：推送文档后由 `push` 触发的
[#9](https://github.com/Oo7350/mimo/actions/runs/45928085911)（commit `e9a1219`）**再次全绿**——
8 分 57 秒、e2e 步骤 5 分 43 秒，35 个 step 同样全 success。两条触发路径（手动 dispatch / push）
都验过了。

**同时纠正一句记录**：KNOWN-024 一直写着"**从没**在真实 runner 上跑过"。查 Actions 历史才发现
[v2.22.0 的 #4](https://github.com/Oo7350/mimo/actions/runs/45775384583) **就跑过一次，并且红了**——
红在「启动后端」：等了 180 秒也登不进去（那时空库没表、没账号，正是 BUG-123/125 的样子），
后续 4 个 step 全 skipped。也就是说：**证据一直在那里，只是没人去看它**，
而记录写成了"没跑过"，听起来像"没机会跑"，实际是"跑过、红了、没人管"。
后者更糟——它让人以为闸门没装，其实是闸门装了却没人读结果。

> 教训：**"未验证"和"验证失败"必须分开写。** 前者是没证据，后者是有反证据；
> 把后者写成前者，下一次就会带着同一个错再推一遍。

---

## v2.22.2 — 全新环境从零到绿：空库建不出表、启动顺序撞车、空库没有账号（BUG-123）(2026-11-26)

> 这一轮是冲着 KNOWN-024（"CI 从没在真实 runner 上跑过"）去的。想着"跑不起来大概率是
> YAML 写错了"，于是先在本机把 CI 的步骤**原样演一遍**：起一个独立的空 MySQL 实例、
> 空库、按 CI 的顺序走。结果第一步就红了——而且不是 YAML 的问题，是**仓库里根本建不出数据库**。
>
> 教训写在最后：**没演练过的 CI 配置，等于没写**。

### BUG-123: 全新数据库建不出来（基础建表脚本从没被执行过） [High]

- **现象**：对着空库启动后端，日志显示 `Started MimoApplication`（看着像成功），
  但库里只有 23 张表，`users` / `teams` / `projects` / `issues` 一个都没有，
  任何接口都返回建表失败。CI 第一次跑就倒在"登录接口探活"那一步。
- **根因**：基础建表脚本 `resources/db/init.sql`（18 张表）**从来没有被程序执行过**。
  `MigrationRunner` 只扫 `db/migration/V*.sql`；`init.sql` 唯一出现的地方是
  `AGENTS.md` 里一条手工命令，而那条命令的路径还是旧的（`backend/src/main/resources/...`，
  实际在 `backend/mimo-app/src/main/resources/...`）。所以：本机库是几个月前手工灌出来的，
  谁都没再验过"从零建库"这条链路。
- **修复**：
  - `init.sql` 搬成迁移链的第一环 `db/migration/V0__init.sql`（`V0` 排序最前），
    只留 DDL、`CREATE TABLE IF NOT EXISTS`，老库跑一遍是空操作。
  - 测试账号与演示团队**拆出去**成 `db/seed-demo.sql`，需要显式执行。
    建表是任何环境都该自动发生的；"默认口令的账号"不是——这两件事混在一个文件里，
    就只能是"要么全自动带上 admin/123456，要么全都不建表"。
  - `application.yml` 的 `url` 改成 `${MIMO_DB_URL:…}`：以前写死 `localhost:3306/mimo`，
    CI 里那行 `MIMO_DB_URL` 是句废话，连到别的库根本改不了。

### BUG-124: 两个启动期 Runner 顺序未定，空库上先查表后建表 [High]

- **现象**：修完建表脚本，空库启动直接崩：`Table 'mimo.users' doesn't exist`，
  `Application run failed`，进程退出。
- **根因**：`MigrationRunner`（建表）和空库引导（插管理员）都实现 `ApplicationRunner`，
  **谁先跑没写**。都不标 `@Order` 时顺序取决于扫描顺序，实际是引导先跑，
  于是它查的第一张表还没建。这事在"库早就建好"的开发机上永远看不见。
- **修复**：`@Order(HIGHEST_PRECEDENCE)` 给建表、`@Order(LOWEST_PRECEDENCE)` 给引导，
  并加一条单测（`AdminBootstrapTest.shouldRunAfterMigrationsAndMigrationsFirst`）
  直接断言两者的顺序值——不然下次有人加 Runner 又会重演。

### BUG-125: 空库一个账号都没有，而 CI 第一步就要登录 [High]

- **现象**：表建好了，`POST /api/auth/login {admin/123456}` 仍然 401——库里没有任何用户。
- **修复**：新增配置显式开启的空库引导 `mimo.bootstrap.admin-username/password`
  （`AdminBootstrap`）：配了就在"账号不存在时"建一个管理员，并同时写旧字段
  `users.role=ROLE_ADMIN` 和 RBAC 的 `user_roles(SUPER_ADMIN, GLOBAL)`
  ——只写一半会得到"能进后台、但某些按权限码判定的按钮一点就 403"的半截管理员。
  默认留空 = 不引导；只配用户名不配密码也拒绝（不猜默认口令）。
  顺便把重复插入打掉：V5 迁移会给 1/2/3 号用户预埋铭牌，空库引导出来的 admin 正好是 1 号，
  直接插会撞 `user_levels` 唯一键——这也是演练时真崩过一次的原因。

### 收口 KNOWN-024 到什么程度

| 之前 | 现在 |
|---|---|
| "CI 配置写了，但没在真实 runner 上跑过，也没人 garantee 它能跑起来" | 本机**按 CI 步骤从零演过一遍**：独立空 MySQL 实例 → 空库启动 → 自动建 38 张表 → 灌演示账号 → 建项目 → 全量 e2e **15 套 148 条 + 3 探针全绿** |
| YAML 里的命令没人核过 | 每个步骤都用"本机验证过的同一条命令"，并补了 `建表结果自检`（表数 < 30 就红）、`预热前端编译`（别把 Vite 冷启动算进用例超时） |
| `MIMO_DB_URL` 等环境变量形同虚设 | `application.yml` 真的读它了；CI 显式写 `MIMO_REALTIME_STORE=memory`（CI 没有 Redis，别指望 auto 探测可复现） |

**仍然没做到的**：真在 GitHub runner 上跑一次。这条还得人开 Actions 页面点 Run workflow，
或给一个带 `actions:read` 的 token。KNOWN-024 继续挂着，但现在它只剩"环境差异"这一层风险，
不再是"配置里藏着一堆必崩的坑"。

### 验证

- 后端 `mvn test`：**225 项全绿**（217 → 225，新增 `AdminBootstrapTest` 8 条：
  未配置不动手、只配用户名不猜口令、已存在不覆盖密码、正常建号+铭牌+授权、
  已有铭牌不重复插、已授权不重复插、角色缺失仍建号、Runner 顺序断言）。
- 空库从零演练（独立 MySQL 实例 `3307`，data 目录全新初始化）：
  `tables=38`、`users=admin`、`user_roles=1`、登录 200。
- `db/seed-demo.sql` 连跑两遍幂等（`INSERT IGNORE` + 两条纠正目标的 `UPDATE`）。
- 全量 e2e：**15/15 套、148/148 检查点、3/3 探针**（在这套零起点环境上跑出来的）。

### 顺带修掉的"用例假设了本机脏数据 / 假设了页面很快"

| 现象 | 真因 | 改法 |
|---|---|---|
| `admin-flow` 只能在开发机上绿：`全员`入口等 25 秒也不出现 | 用例要的 zhangsan 是「旧字段兜底的团队管理员」（`team_members.role=ROLE_ADMIN`），但开发库里他能用是因为**历史上被手工加过 DEVELOPER 全局角色**；种子脚本却把他灌成普通成员 | `seed-demo.sql` 里显式给 (team 1, user 2) → `ROLE_ADMIN`，并加 `UPDATE` 纠正已被灌错的库；用例一条没改（它测的是产品行为） |
| `admin-flow` 的「管理员代提交」那条红：`提交验收` 按钮不存在 | 两个假设叠在一起：① 它挑的是"进行中、非缺陷"的卡，**没排除已经验收通过的**（那种卡按设计就没有这个按钮）；② 也没排除**自己负责**的卡（自己负责时"能提交"理所当然，验不出代提交）。换台机器/换个库就跑偏 | 候选加两个条件：`owner ≠ admin` 且 `/issues/{id}/reviews` 为空；库里实在没有合适候选时**自己造一张**（TASK 指给 zhangsan、置为进行中）再走看板深链 |
| 同上，偶发：深链开了但弹窗没出现 | 看板 `?issue=` 的弹窗要等 `fetchBoard()` 回来才开，用例固定等 `2600ms`，项目大时会不够 | 改成等 `.el-dialog` 可见（20 秒），失败时把 `url` / 弹窗数 / 按钮列表打出来——固定毫秒等待是这个仓库里最反复的假红来源 |
| `presence` 在连跑时红：「关页后在线人数没回落」 | 判的是**总人数**。连跑十几套时，前面套件的浏览器会话正在陆续心跳超时下线，"我加一人、别人掉一人"总数不变。改成比基线也不行（同样在动） | 判"自己的 id 是否在 `onlineUserIds` 里"：开页必须出现、关页必须消失。另外把固定 `waitForTimeout(3000)` 换成"等自己的 id 出现（最多 25 秒）"，慢机器不再假红 |
| `core-flows` 连跑时偶发：「6.新建 Sprint」报 `POST /sprints=null`（一个请求都没发） | 进页面后固定等 `1800ms` 就点「新建 Sprint」、再固定等 `900ms` 就填表。整套连跑时 Vite 可能正在重编译，页面慢一拍 → 按钮点空、弹窗没挂上就填表，**请求根本没发出去**，报出来却像后端不认 | 改成等按钮可见（20 秒）→ 等弹窗可见（15 秒）→ 等真正的 `POST /api/sprints` 响应（20 秒）；失败时额外打印"弹窗还开着没 / 表单校验提示"，下次一眼能看出是卡在表单还是接口 |

### 跨平台体检（把"Linux 环境差异"这层也先查一遍）

KNOWN-024 剩下的唯一风险是"Windows 上绿、Linux runner 上红"。既然 CI 跑不了，就先把
**能静态查出来的差异**逐项过一遍（这一节记的是查的过程与结论，不是新 bug）：

| 查什么 | 结果 |
|---|---|
| e2e 里有没有写死 `C:\` / `process.platform` / `cmd.exe` / `path.win32` | **0 命中**（唯一一处是 `presence-source-label.mjs` 的默认模块路径 `C:/mimo-env/...`——已改成退回包名 `playwright`，否则 CI 上会报看不懂的 `MODULE_NOT_FOUND`） |
| 有没有 `require("./本地文件")` 这种大小写敏感引用 | **没有**：所有套件只 require playwright（或 `frontend/node_modules` 里的 esbuild/marked/dompurify，由 `npm ci` 落地），无仓库内相对引用 |
| 子进程怎么起 | `run-all.mjs` 用 `spawn(process.execPath, …)`，不是 `"node"` 也不是 `shell: true`；`xss-markdown.mjs` 依次试 `$GIT_EXE` → `git` → 一条本机 UCRT 路径，前两个在 Linux 上就是对的 |
| 仓库里存的行尾 | `git ls-files --eol`：索引里 **575 个文件全 LF、0 个 CRLF、0 个 mixed**，Linux 检出不会踩 `\r` |
| 那还防什么？ | 加 `.gitattributes` 把 `*.sql / *.mjs / *.ts / *.vue / *.yml / *.sh` 钉成 `eol=lf`，二进制声明 `binary`——`core.autocrlf` 是**每台机器各自的设置**，别人 clone 时不设就又会把 CRLF 带进来 |
| workflow 的 shell 坑 | ① `tables=$(… docker exec …)` 一旦多一行 warning，`[ "$tables" -lt 30 ]` 会报 `integer expression expected`，看不出真因 → 加 `tr -dc '0-9'` + `${tables:-0}`；② 后台起前后端改用 `nohup`，避免 step 结束时被 SIGHUP 带走（那会让上一步"成功"、下一步"连不上"） |

> 一句话：这批检查**不能代替真跑一次**，但能把"大概率会红"的低级差异先清掉，
> 让第一次真实运行时剩下的问题确实是环境级的（比如 runner 上 mysql 客户端版本、sudo 装浏览器依赖）。

### 教训

**没演练过的 CI 配置等于没写。** 这份 YAML 之前看着挺像样：服务容器、healthcheck、环境变量、
探活循环、产物留存都有。但把它当命令逐条执行时，第一个 30 秒就撞上"仓库建不出数据库"。
写配置的人和跑配置的人如果是同一个动作，坑早就暴露了；问题就在于**没人真的跑一遍**。

---

## v2.22.1 — 迭代规划的空态说清原因（BUG-122）+ 计划书里两条过时备注改成实话 (2026-11-26)

> 这一轮是从"计划书还剩什么没做"开始查的，查出来三件事：一条真欠（迭代规划空态），
> 两条**早就做完了却没勾**（Wiki 历史版本/差异、字号收敛）。文档里留着"未做"，
> 比少写一条更糟——下一个人会照着它重做一遍。

### BUG-122: 迭代规划的候选空态只有一句话，不区分原因也不给出路 [Low]

- **现象**：AI 规划面板里，Backlog 候选为空时只有一句写死的「Backlog 暂无可规划工作项」。
  但候选为空的**三种原因**下一步完全不同：项目里一项工作项都没有（该去建）、
  未完成的都排进迭代了（该去干活，不是去建）、全都做完了（该建下一批）。
  一句话打发三种情况，用户只能自己猜。
- **根因**：后端其实算出了原因（`summary` + `risks` 里就写着），但前端空态把这两样都丢掉了，
  换成了自己的固定文案——**后端说清了，界面又盖回去了**，和 v2.22.0 那个假的「终态」开关同源。
- **修复**：
  - 后端 `SprintPlanVO` 增 `emptyReason`：`NO_ISSUES` / `ALL_DONE` / `ALL_SCHEDULED` /
    `NOTHING_PLANNABLE`（兜底）；`summary` 与 `risks` 按原因分别写具体数字，
    例如「未完成的 2 项工作项都已经排进迭代了，Backlog 里没有剩下的。」
  - 前端空态改用后端给的 `summary` 作主文案，下面写「下一步：…」并给一个
    「去项目看板建工作项」按钮，直接把路指出来。
  - 有候选时 `emptyReason` 为 null 且不下发（Jackson `NON_NULL`），不干扰正常路径。
- **教训**：空状态是界面上最容易被写死的地方，而它恰恰是用户最需要指路的时候。
  空态文案必须由"为什么空"算出来，不能拍一句通用的话。

### 计划书里两条过时备注（不是 bug，是文档在误导）

| 计划书原话 | 实际情况 |
|---|---|
| 仍未做：Wiki 历史版本/差异 | **v2.13.7 就做了**（版本快照 + 列表 + 回看 + 回滚），v2.13.8 补了差异对比 |
| 仍未做：字号体系收敛（全仓还有 100+ 处 10–11px） | **0 处**：`BUG-116` 那轮把 106 处全抬到 12px，`font-size` 已无 <12px 声明 |

两条已就地改成"已做 + 当时的版本号"，并保留在计划书里作为记录（删掉会让人以为从没规划过）。

### 验证

- 后端 `mvn test`：**217 项全绿**（`SprintPlanningServiceTest` 9 → 12，新增：空项目 / 全排期 /
  全完成 三种原因各一条 + 有候选时不设 `emptyReason`）。
- 新 e2e `e2e/sprint-plan-empty.mjs` **6 项全绿**：空项目报 `NO_ISSUES` 且指路去建、
  有候选时不出空态原因（正常路径没被影响）、全排期报 `ALL_SCHEDULED`（出路是做完手上的）、
  全完成报 `ALL_DONE`（出路是建下一批）、界面上把原因与「下一步」都显示出来、
  空态里的出路按钮可点。
- 全量 `node e2e/run-all.mjs --probes` → **15 套 148 条 + 3 探针全绿**。

### 顺手修掉两个"用例太脆"（不是产品缺陷，记下来防复发）

这两个都是我这套 e2e 自己的问题，跑全量时才暴露，属于"环境相关"而非"功能坏了"：

| 现象 | 真因 | 改法 |
|---|---|---|
| `workflow-guard` 整套 **0 条检查点**就超时 | 登录页 `domcontentloaded` 偶发 >20 秒（连跑十几套 + 后端刚重启，Vite 重新编译首屏） | 登录页导航加重试（45 秒超时 × 2），并注明这是环境抖动；断言一条没改 |
| `presence` 报「关页后在线人数没归零」 | 断言写死 `onlineCount === 0`，而开发机上有人真开着页面；改成"比基线多 1"也不行——在线人数按**人**去重，默认 admin 登录时本机已有 admin 会话，再开一页人数不变 | 改用现场注册的独立账号 `e2e-presence{stamp}`：它开页前一定不在线，于是"开页 +1、关页回到原水位"无歧义；顺带把第 1 条改成"比基线多 1" |

教训：**"跑绿过"不等于"稳定"**。全量连跑一次就把两条脆用例照出来了，而这正是 `run-all.mjs` 存在的意义。

---

## v2.22.0 — 工作流：脱离看板列的自由节点 + 可视化连线 + 「验收标准勾完才能完成」(2026-11-26)

> 计划书里挂了几轮的待办：「脱离看板列的自由节点」。以前节点主键就是 `columnId`，
> 等于"流程里的一步必须先是一列"——想加个「上线」步骤，得先去建列，顺序是反的。
> 这一轮把身份还给节点：**节点是流程状态，看板列由它投影**。设计器也从"拖列的网格"
> 变成了真画布（拖节点、拖连线），并补上第一个"什么情况下才能做"的前提条件。
> 做的时候还翻出一个会撒谎的开关（BUG-121）。

### 节点身份与列投影

- **`WorkflowNode` 增三样**：`nodeKey`（稳定身份，画布连线用它）、`x` / `y`（画布坐标）。
  `columnId` 变成可空——可空才谈得上"先有流程，后有列"。旧配置（只有 `columnId`）
  读出时按 `col-{columnId}` 补身份、按顺序补坐标，历史数据不用迁移（config 是 JSON，不动表结构）。
- **保存链路三步**：`normalizeConfig`（补身份/坐标、把连线两端的 `nodeKey` 与 `columnId` 互相补齐）
  → `projectFreeNodes`（**缺列的节点建一列并回填**，记下新建的列名）→ `validateConfig`。
  顺序不能反：不先投影就校验，自由节点永远是"没有列"的非法状态。
- **不静默建列**：`WorkflowVO.createdColumns` 把新建的列名带回前端，界面提示
  「为「上线」自动创建了看板列（它们原本没有落脚的地方）」。偷偷多出一列比报错更让人困惑。
- **一个列只能是一个节点的落脚点**：两个节点共列，工作项就说不清在流程哪一步。
  保存时直接拒绝并说清是哪个节点重复绑定。
- **删节点不删列**：列里可能还有工作项，自动删列是破坏性操作。界面上说清、文档里写明，
  要删列去看板删。
- **为什么不许"节点没有列也能停"**：工作项总得停在某一列上，界面才画得出来。
  让节点凭空存在、看板却看不见它，等于让界面撒谎——这正是这个项目一直在修的病。

### 画布（`WorkflowDesigner.vue`）

- **节点卡片 + SVG 贝塞尔连线 + 箭头**。节点绝对定位，拖动后坐标跟着保存
  （下次打开还是你摆的样子，不会被排成一行）。
- **拖动把手放在卡片左上**：卡片里全是输入控件（改名 input、责任人 select），
  整卡 `mousedown` 会把这些点击吞掉——第一版就是这么错的，e2e 直接抓到"拖了但没动"。
- **连线两条路**：从节点右侧圆点拖到另一个节点；或者点一下圆点、再点目标节点
  （触屏、手抖、Playwright 都更吃这一套）。
- **点连线 → 设置面板**：「谁可以做（任一）」+「什么情况下才能做（全部）」两栏，
  外加"删除这条"。
- **踩到的坑（自己写的 bug，记下来防复发）**：最初 `mouseup` 就把"连线中"状态清掉，
  而浏览器在"按下在 A、松开在 B"时会把 `click` 派发到**共同祖先**（画布），
  于是画布先清状态、目标节点的 click 再执行已经晚了——表现是"拖了半天没连上"。
  改成：`mouseup` 只结束拖动，点空白才放弃连线。

### 前提条件：第一条是「验收标准全部勾完」

- **两类条件分开**：`conditions` = 谁可以做（**任一命中**即可，原有语义不变）；
  新增 `prerequisites` = 什么情况下才能做（**必须全部满足**）。
  混在一个字段里用 OR 语义，就会出现"管理员绕过验收标准"，等于没配。
- **`ACCEPTANCE_DONE`**：解析 `issue.acceptanceCriteria`，有没勾完的就拒绝，提示
  「还不能进入「已完成」：验收标准还有 2 项没勾完（共 2 项）」。拦的时候把数说清，
  而不是"条件不满足"。
- **一条验收标准都没写的工作项不受这条限制**：否则不写验收标准的老任务会永远推不动，
  那不是严格，是把流程变成死路。这一条在文档与界面上都写明。
- **`GET /api/workflow/transitions` 增可选 `issueId`**：给了才提前算得出"还差几项"，
  不给就只能对"谁可以做"下结论（老调用方照旧）。看板拖拽被拦时前端会带上它，
  于是拦下来的那句话是准确的数字，不是猜的。
- **模板不默认加这条前提**：默认套用会让所有项目立刻被卡住。要收紧，自己在连线上加。

### BUG-121: 设计器里的「终态」复选框勾了不生效 [Medium]

- **现象**：设计器每个节点有个「终态」勾选框，勾了之后看板/工作台的"终态"判定完全不变。
- **根因**：两套口径。看板与工作台判终态用的是 `BoardColumnRules.isTerminalName(columnName)`
  （列名含「完成」或 `done`），而工作流配置里的 `isTerminal` **只被用于"至少一个终态"的校验**。
  勾选框改的是后者，看起来能改行为，其实什么都没有。
- **修复**：勾选框删掉，改成只读徽标（按同一口径判定）；保存时按同一口径回填 `isTerminal`，
  校验照旧。说明文字写清「终态判断与看板一致（列名含「完成」或 done）」。
- **教训**：一个可以点、但不影响任何行为的控件，比没有这个控件更糟——
  用户会以为自己改了规则，然后在看板上被现实打脸。

### 验证

- **后端 `mvn test`：214 项全绿**（`WorkflowServiceTest` 25 → 32，新增 7 项：
  自由节点投影建列、`nodeKey` 归一化与连线两端对齐、连线指向不存在节点被拒、
  验收标准未勾完被拦/全部勾完放行/没写验收标准不拦、可用转换里带上前提原因）。
- **新 e2e `workflow-canvas.mjs`：10 项全绿**
  ① 设计器是画布（卡片 + 连线都画出来了）② 拖节点坐标真的变
  ③ 新节点可以先不绑列（卡片写明"保存后自动建列"）④ 从圆点拖到另一节点连出一条线
  ⑤ 点连线能加前提（面板两栏）⑥ 保存时自动建列并**明确提示建了哪一列**
  ⑦ 前提存下来了（`prerequisites` 含 `ACCEPTANCE_DONE`）⑧ 看板上真的多了这一列
  ⑨ 验收标准没勾完时拖不进「已完成」（提示含"还有 2 项没勾完"，卡片留在原列）
  ⑩ 勾完之后同样的拖拽顺利通过。收尾把工作流恢复原样、删掉自动建的列。
- **回归**：`workflow-guard.mjs` 8 项全过（选择器改到 `[data-workflow-canvas]`，
  断言语义没变：节点数 = 责任人下拉数）。
- **全量**：`node e2e/run-all.mjs --probes` → **14 套 142 条 + 3 探针全绿**（退出码 0）。
  实测确认收尾干净：看板仍是 待办/进行中/已完成 三列，STORY 工作流 4 条规则、0 条前提。

---

## v2.21.0 — Redis 真的用起来了：跨实例在线状态与实时广播（关闭 KNOWN-002）(2026-11-26)

> KNOWN-002 一直写着"Redis 已引入但没实际使用"。这一轮查下来，情况比记录里更彻底：
> `MimoApplication` 把 `RedisAutoConfiguration` 排除了，所以 `StringRedisTemplate` 这个 bean
> **从来不存在**；两处 `@Autowired(required = false)` 的注入永远是 null，
> `redisTemplate != null` 的守卫永远不成立——所谓"单设备登录 Token 校验"从来没跑过。
> 而 WebSocket 用内存简单 broker，多实例部署时在线人数与消息各算各的，且**没有任何提示**。

### 收口 KNOWN-002：可切换的在线状态与广播

- **抽出 `PresenceStore`**：`connect / disconnect / touch / onlineUserIds / isOnline / onlineUserCount`
  外加 `name()` / `shared()`。原来那堆 `ConcurrentHashMap` 原样搬进 `InMemoryPresenceStore`
  （行为不变），新增 `RedisPresenceStore`。
- **Redis 键与 TTL**：`mimo:presence:sess:{sessionId}` = userId（90s）、
  `mimo:presence:user:{userId}` = SET(sessionId)（90s）、`mimo:presence:online` = SET(userId)。
  本实例每 30 秒心跳续期一次；心跳发现 TTL 已过期会**重新登记**（`touch` 收的是「会话 → 用户」映射，
  就是为了自愈）。读在线列表时顺手摘掉索引里的死键，免得越积越多。
- **为什么用 TTL 而不是"断线就删"**：进程被杀、网络闪断时根本收不到 DISCONNECT，
  只靠删除会让在线人数只涨不跌。TTL + 心跳保证"最长 90 秒收敛"，
  代价是刚断线的一小段时间可能多算——比永久多算一个人强。
- **`RealtimeBridge`**：`WebSocketService` 不再直接调 `SimpMessagingTemplate`。
  `LocalRealtimeBridge` = 原来行为；`RedisRealtimeBridge` = 本地先投一次 + 发布
  `{origin, destination, payload}` 到 `mimo:bus:realtime`，各实例收到后投给自己的订阅者，
  **origin 等于自己的直接丢掉**（否则本机用户会收到两遍，表现为"聊天消息重复"）。
  payload 用 Spring 那个 `ObjectMapper` 序列化，日期格式与本地投递一致。
- **装配方式**：`RedisAutoConfiguration` **仍然排除**，连接工厂/模板由 `RealtimeRedisConfig`
  按 `mimo.realtime.*` 自己建；`mimo.realtime.store=auto`（默认）启动时探一次 Redis（300ms 超时），
  通就用 Redis、不通就用进程内。两个配置类靠互斥条件（`RedisReachableCondition` +
  `RedisUnreachableCondition`）装配，不用 `@ConditionalOnMissingBean`——后者取决于配置类解析顺序，
  顺序一变就是"两套 bean 都在"或"一套都没有"。
- **降级要说出来**：Redis 一旦抛异常（连不上/超时/命令超时 500ms），`RedisPresenceStore`
  标记 degraded、之后读写走进程内表、`shared()` 变 false；接口多返回
  `presenceStore` / `presenceShared` / `presenceNote` 三个字段，团队页写「N 人在线（仅本实例）」
  + 一个说明图标。**看不见"准不准"的数字，比看到一个不准的数字更糟**——这是 KNOWN-013/025 同一类病。
- **单设备登录显式化**：接上 Redis 后 `StringRedisTemplate` 真的存在了，原来静默跳过的校验会突然生效，
  同账号在两个浏览器登录会互相踢掉（开发调试与 e2e 立刻全崩）。加 `mimo.security.single-device-login`
  开关，默认关，注释里写明原因。
- **顺手修掉的两个构建/配置坑**（见下方 BUG-119 / BUG-120）。

### BUG-119: `mvn clean package` 产出的是瘦 jar，启动报 "no main manifest attribute" [Medium]

- **现象**：`mimo-app/pom.xml` 声明了 `spring-boot-maven-plugin`，但 `mvn clean package -DskipTests`
  只产出 0.9MB 的普通 jar；仓库里那个 69MB 可运行 jar 是当初手工跑 `spring-boot:repackage` 得来的，
  所以"照文档打包"其实拿不到能 `java -jar` 的东西。
- **根因**：`spring-boot-starter-parent` **不会**自动给你加 `repackage` 执行（那是 Spring Initializr
  生成项目时才写的），只声明插件不绑执行就等于没接上 package 阶段。
- **修复**：在 `mimo-app/pom.xml` 显式加 `<executions><execution><id>repackage</id><goals><goal>repackage</goal></goals>
  </execution></executions>`。现在 `mvn clean package -DskipTests` 直接得到 69MB 可运行 jar，
  且只有 app 模块被 repackage（common/system 保持普通 jar）。
- **教训**：打包配置也要按"跑一遍文档里的命令"验证，不能只看"以前能跑起来"。

### BUG-120: 误用 `spring-boot:repackage` 直接把依赖模块打成空壳 jar [High]

- **现象**：为了补上 BUG-119，我先手工跑了 `mvn ... package spring-boot:repackage`（没加 `-pl`），
  结果 goal 作为 `default-cli` 在所有模块上执行：`mimo-common` 没有 main class，
  repackage 失败并把 `mimo-common-1.0.0-SNAPSHOT.jar` 替换成 **22 字节的空壳**（原内容进了 `.jar.original`），
  随后 `mimo-app` 打出来的胖 jar 里缺 `BOOT-INF/lib/mimo-common`，启动时报
  `NoClassDefFoundError: com/mimo/common/AuditLog`。
- **根因**：`default-cli` 的 goal 会对 reactor 里每个模块都执行一遍，而 `repackage` 只对"有 main class 的
  可执行模块"成立。
- **修复**：`mvn clean package` 重跑整个 reactor（不只是 app），并确认 `mimo-common` 10KB、
  `mimo-system` 36KB、`mimo-app` 69MB；以后只在 `-pl mimo-app` 上跑 repackage。
- **教训**：`mvn <goal>` 在聚合工程里的作用范围是"每个模块"，不要拿它当单模块命令用。

### 验证（真机双实例，不是 mock）

- 起了两个后端（:8080 / :8081）共用同一个 MySQL 与 Redis，用 STOMP 客户端连 **B** 实例，然后：
  - 问 **A** 实例的 `/api/teams/1/presence` → `onlineUserIds` 里出现 B 上的那个用户（跨实例共享）；
  - 通过 **A** 实例发团队聊天 → 连在 **B** 上的客户端收到同一条消息（跨实例广播）；
  - 关闭客户端 → A 看到的在线数回落（DISCONNECT 立即清理）。
  - 脚本：`e2e/two-instance-presence.mjs`（5 项）。
- **反向对照**：把探针用的实例改成 `MIMO_REALTIME_STORE=memory` 再跑一遍，
  "跨实例共享"与"跨实例广播"两项**按预期不成立**（`--local` 模式）——
  证明上面测到的是真共享，而不是巧合或单机路径。
- 界面口径：新增 `e2e/presence-source-label.mjs`（6 项），用拦截接口的方式覆盖两种口径：
  `presenceShared=false` 时文案必须出现「（仅本实例）」+ 说明图标，`true` 时不许出现。
  实测文案：`2 人在线（仅本实例） · 4 位成员` / `2 人在线 · 4 位成员`。
- **TTL 与心跳实测**（临时脚本挂一个真实页面，不进仓库）：页面保持打开 110 秒，
  `/teams/1/presence` 每 10 秒采样一次，`onlineCount` **全程为 1**（超过 90 秒 TTL 仍在线，
  说明 30 秒心跳确实把 session 键续上了）；页面被强杀后 `mimo:presence:*` 在约 10 秒内清空
  （走 WebSocket 关闭 → `SessionDisconnectEvent`），不会留下长期在线的人。
  过程中差点误判一次：先看 `mimo:presence:online` 里还留着 userId、而 `redis-cli --scan` 又返回空，
  差点登记成"索引泄漏"——实际是这个 Redis 5.0 的 `--scan` 在 PowerShell 下没匹配到，
  换 `keys` 一看键都在，且那条残留属于用户自己开着的浏览器会话。教训：排查用 `KEYS` 复核，别只信一次 `SCAN`。
- 后端 `mvn test`：**207 项全绿**（新增 15 项：`PresenceServiceTest` +3 委托与心跳、
  `RedisPresenceStoreTest` 7 项键与 TTL 契约及降级、`RedisRealtimeBridgeTest` 5 项含"自己的消息必须丢掉"）。
- 全量 e2e：`node e2e/run-all.mjs --probes` → **13 套 132 条 + 3 探针全绿**（退出码 0，Redis 模式下跑）。

---

## v2.20.1 — 全员任务流：分页、服务端筛选、7 天趋势（关闭 KNOWN-025）(2026-11-25)

> 上一版把"全员任务流"做出来了，但它是**一次把所有成员的所有任务铺开**：
> 团队一大，首屏就是一堵墙，而且看不出"上周谁在忙、这周是不是更快了"。
> 这一轮补的就是这两件事，但做着做着发现分页会把原来的筛选**变成假的**——
> 那是这轮真正的收获。

### 收口 KNOWN-025：分页 / 筛选 / 趋势

- **分页在服务端**：`GET /api/workbench/team` 新增 `memberPage` / `pageSize`（默认 8，上限 30）
  与 `taskLimit`（每人最多带回几条任务，默认 6，上限 20）。前端切页不算分页——
  请求照样把全量数据拉下来，只是"看起来分页了"。切页顺序是**先筛选 → 再排序 → 最后切页**，
  所以第一页永远是"最该管的几个人"。
- **筛选与排序也搬到服务端**（`keyword` / `only` / `sort`）。这是做分页时连带发现的真问题：
  以前筛选按钮上的"有逾期 3"是拿**当前页 8 个人**算的，点进去可能只看到 1 个人。
  现在筛选按钮不显示条数，改由分页文案写"筛出 N / 全队 M"，分母不再骗人。
- **概览数字永远按全队算**，不受筛选影响：否则筛一下"空闲"，顶上的"未完成/逾期"
  就变成空闲成员的合计，看着像全队突然轻松了。
- **任务截断与计数分离**：`tasks` 受 `taskLimit` 限制，但 `taskTotal` / `openCount` /
  `overdueTaskCount` 一律按全部任务算——拿被截断的列表去算负载，标签就会说谎。
  界面在被截断的成员卡下写"还有 N 项没显示（共 M 项）· 点击展开"，展开到上限后差额会继续如实显示。
- **7 天趋势**（`trend` / `trendNote`）：数据取自 `activity_logs` 的**真实事件**——
  新建 = `CREATE`，完成 = `MOVE` 且目标列是终态列。**故意不用 `issues.updated_at` 近似**：
  项目没有单独的完成时间字段，拿更新时间冒充会在"改个标题"时虚增完成数。
  口径（含"列改名后更早的事件可能统计不到"）随接口下发，界面写在图下面。
- **判定只写一处**：新增 `util/BoardColumnRules`，看板拖拽的"终态列"判断与趋势统计共用它。
  以前这个判断是 `BoardService` 的私有方法，别处要用只能再抄一遍（与 BUG-115/117 同一类毛病）。
- 修掉两个自己写出来的问题：
  1. `vo.setMembers(streams)` 残留在分页代码之后，会把分页结果覆盖成全量——
     页码在变、数据没切，典型的"看着分页了其实没分"。
  2. 新按钮把 `--color-primary-solid` 当**文字色**用，暗色下压卡片只有 2.82:1；
     主题审计当场抓到（与 BUG-111 同一类）。改用随主题提亮的 `--color-primary`。

### 验证

- 后端 `mvn test`：**192 项全绿**（新增 5 项：分页切页与越界夹取、任务截断而计数不截断、
  服务端筛选与排序、趋势只数真实 MOVE 事件、事件描述解析）。
- e2e `admin-flow`：**26 条全绿**（新增 6 条：服务端分页真的切了数据、翻页换人、
  截断但计数不变、筛选在服务端生效、7 根趋势柱与口径脚注、分页文案"第 x/y 页 · 共 N 人"）。
- 全量回归：`node e2e/run-all.mjs --probes` → **12 套 126 条 + 3 探针全绿**（退出码 0）。
- 实测数据（开发库）：趋势 11-20 新建 13 / 完成 5、11-24 新建 35、今天新建 32 / 完成 1；
  柱高按当周最大值归一（35 → 42px，17 → 20.6px，0 → 2px 基线）。
- 暗色对比度实测：趋势脚注 4.34:1、日期 4.34:1、标题 16.3:1、分页文案 4.76:1；
  `theme-audit` 亮暗两套各 14 页：浅色块 0 / 低对比 0 / 小于 12px 0。

---

## v2.19.8 — 管理员视角：身份看得见、全员任务流、任务详情抽屉 (2026-11-24)

> 用户要求：「管理员账号要和普通账号突出区别，他的任务列表和看板可以看到所有成员的任务流，
> 然后点击的话可以看细致的具体任务，我希望把这个功能做好一点，具体动画和流程精美一点。」
>
> 这一轮之前，"是不是管理员"这件事在界面上**零痕迹**：顶栏只看得到用户名，
> 管理员和普通成员长得一模一样；而"我的任务"只显示分给自己的，
> 管理员想知道"每个人手上有什么、卡在哪"，只能一个个问或者点进每个看板翻。

### 管理员身份：从"没有痕迹"到"一眼认得出"

- 新增 `components/common/AdminBadge.vue`：盾牌图标 + 琥珀渐变 + 入场时一道微光（`prefers-reduced-motion` 下不闪），
  `quiet` 变体用于列表里大量出现的地方。
- 顶栏用户名旁边显示徽标（`系统管理员` / `团队管理员`），头像加一圈金色描边；
  工作台 Hero 也显示一次——**这样"我能看全员任务流"才有人知道**。
- 判定口径与后端口径一致（旧 `role` 字段 / RBAC `role.manage` / `team.manage`），
  但前端只用来决定显示，真正的权限在后端（前端藏起来不等于安全）。
- 团队页成员列表、全员任务流的成员列也复用同一个徽标，语言统一。

### 全员任务流（`GET /api/workbench/team`）

- 范围三条口径写在 `WorkbenchService#getTeamWorkbench` 的 Javadoc 里：
  **系统管理员看全部团队 / 团队管理员只看自己管理的团队 / 成员包含"手上没活的人"**。
  第三条是刻意的：空闲本身是信息，团队 3 人里 2 人没活，管理员得知道。
- 返回：`summary`（成员数、在线数、未完成、进行中、逾期、已完成、**未指派**）
  + 每人一条 `MemberStreamVO`（负载等级 + 一句话负载依据 + 排序好的任务）。
- **负载标签给依据**：`OVERLOADED / BUSY / NORMAL / IDLE` 不只是上色，还带
  `loadReason`（"已有 2 项逾期，未完成 8 项"）；逾期权重最高——一件事逾期了，数量多少都无所谓。
- 性能：工作项、项目、迭代、前置依赖、在线状态各查一次，**不是每人一次**
  （10 人团队按人查就是 40 次往返；管理员每刷一次页面都要付这个代价）。
- 未指派任务单独一栏并排在成员列之前：那是"没人认领但必须有人做"的一类。

### 前端：三个人一起看的东西，动画要"有信息量"

- `components/workbench/TeamFlowBoard.vue`：成员列网格，**交错入场**（每列 70ms、每张任务卡 40ms），
  负载条宽度 = 相对最忙那个人的比例（"谁堆活"一眼可比），逾期任务红底 + 左侧色条按负载分级。
- 工具条：成员/任务搜索、`全部 / 有逾期 / 进行中 / 空闲` 筛选（带人数）、`按负载 / 按姓名 / 按逾期` 排序。
- 工作台面板在「全员」视角下**占满整行**（`grid-column: 1 / -1`）：按人分列放不进半栏。
- `components/issue/TaskDetailDrawer.vue`：右侧抽屉展示单个工作项——类型/编号/标题/状态/优先级/
  逾期天数/故事点/标签、6 个字段格、Markdown 描述、验收标准（完成度）、缺陷信息、子任务、
  底部「在项目里打开」出口。加载时是骨架 → 内容分段淡入，**不跳页**（判断完直接看下一个人）。
- 看板：管理员多一个「全员泳道」开关（按指派人分泳道本来就是"看全员任务流"的办法，但没人知道），
  用金色描边与普通筛选项区分。

### BUG-110: 详情抽屉按 Esc 关不掉 [Low]

- **文件**: `frontend/src/components/issue/TaskDetailDrawer.vue`
- **现象**: e2e 里鼠标点开抽屉后按 Esc 没反应（点关闭按钮正常）。
- **根因**: Element 抽屉自带的 `close-on-press-escape` 是挂在**抽屉容器**上的 keydown；
  用鼠标点完卡片后焦点不在容器内，按键收不到。
- **修复**: 打开时在 `window` 上挂 `keydown` 捕获监听、关闭/卸载时摘掉，Esc 一定生效。
- **教训**: 依赖组件库的快捷键时，先确认真实焦点在哪；UI 用例要点"用户实际的点击顺序"。

### BUG-111: 统计卡标签压在淡色底上只有 2.95:1 [Low]

- **文件**: `frontend/src/components/workbench/TeamFlowBoard.vue`
- **现象**: 亮色下"进行中"统计卡的标签文字与淡紫底对比度 2.95:1，低于 3:1 门槛。
- **根因**: 标签用了 `--text-muted`（亮色 `#7c8798`），而卡片底是 `--color-primary-lighter` 淡紫。
- **修复**: 标签改用 `--text-secondary`；暗色不变（暗色下 muted 本来就够）。
- **教训**: "淡色底 + 弱化文字"是最容易掉到 3:1 以下的组合，新卡片一律用 secondary。

### BUG-112: 在线人数统计用了两套口径，单测里恒为 0 [Low]

- **文件**: `backend/mimo-app/src/main/java/com/mimo/service/WorkbenchService.java`
- **现象**: 新写的单测断言 `summary.onlineMemberCount = 1` 失败，实际 0。
- **根因**: 成员循环里用 `presenceService.filterOnline(...)` 算在线集合，而 summary 又调用了一次
  `countOnlineAmong(...)`；后者的 mock 没打桩，于是返回 0 —— 同一次请求里问了两遍在线状态。
- **修复**: 只调一次 `filterOnline`，summary 用 `onlineUserIds.size()`。
- **教训**: 同一个事实在一个方法里问两次，就会出现"两处数字不一样"的隐患；单测先抓到了。

### 用例自身写错的三处（补记，不算 BUG）

第一版 `e2e/admin-flow.mjs` 有 3 条 FAIL，全是**用例写错**，不是产品 bug：

| 断言 | 错在哪 | 改法 |
|---|---|---|
| 管理员顶栏有徽标 | 登录后立刻数 `.admin-badge`，而徽标要等 userInfo/权限码回来 | `waitFor({ state: 'visible' })` 最多等 10 秒 |
| Esc 能关掉抽屉 | 关闭后 `el-drawer` 内容仍留在 DOM（只是隐藏），`count() === 0` 永远不成立 | 判 `isVisible()` |
| 普通成员被拒（403） | 断言 HTTP 状态码；本项目业务错误用**响应体 `code`** 表达（HTTP 仍是 200） | 断言 `body.code === 403` |

第三条尤其容易误判：`GlobalExceptionHandler` 对 `BusinessException` 返回 HTTP 200 + `code`，
所以"越权被挡住"在浏览器 Network 面板里看不出红色。这一点也写进了 `AGENTS.md` Decision Log。

### 新增验收用例：`e2e/admin-flow.mjs`（15 条）

身份徽标（管理员有/普通成员没有）、`我的 / 全员` 切换、成员列数 = 接口人数、
概览数字与接口一致、点卡片开抽屉且标题一致、抽屉字段非空、Esc 关闭、
普通成员调接口被拒（`code=403` + 说明）、
以及**新界面自己的可读性**：全员视图与抽屉在亮色/暗色下均无低对比文本、无横向溢出（两种主题各量一次）。

那条对比度检查当场抓到了 BUG-111；这条用例今后就是这批 UI 的回归网。

### BUG-113: 团队管理员（旧字段）看不到管理员入口——功能"做了但等于没做" [High]

- **文件**: `frontend/src/views/Dashboard.vue`、`components/layout/MainLayout.vue`、`views/ProjectBoard.vue`
- **现象**: 用户看到成品后直接说"**管理员账号的刚刚给你说的功能你没有给我实现啊**"。
  实测三个开发账号：`admin` 有徽标有切换、`lisi` 有、**`zhangsan` 什么都没有**，
  但 `GET /api/workbench/team` 对 zhangsan 返回 `scope=TEAM`（后端放行）。
- **根因**: 前端自己拼判定：`userStore.isAdmin || has('role.manage') || has('team.manage')`。
  zhangsan 是 `users.role = ROLE_MEMBER`、RBAC 里没有角色，但
  `team_members.role = ROLE_ADMIN`（v2.7 之前的旧字段）—— `AccessControlService` 认这条旧路径，
  前端看不到它，于是把入口整个藏了。**判定在两处各写一遍，必然对不上。**
- **修复**: 判定收敛到后端一处 —— `AccessControlService#resolveAdminScope(userId)` 返回
  `SYSTEM / TEAM / NONE`，写进 `/api/user/profile` 与登录响应的 `adminScope` 字段；
  前端 `store/user.ts` 只读这个结论（`isManager` / `managerLabel`），
  工作台、顶栏、看板三处入口全部改读它，前端不再自己拼权限码。
- **教训**: ① 这类"前端判断能力、后端判断权限"的地方，前端**必须只读后端结论**，
  否则后端每加一条兜底路径（旧字段、白名单、迁移开关），前端就会多一处说谎；
  ② 自测账号要覆盖"权限来源不同的账号"：admin（旧字段）、lisi（RBAC）、zhangsan（旧字段团队级），
  只测 admin 会漏掉 zhangsan 这种。
- **回归**: `e2e/admin-flow.mjs` 新增 2 条（旧字段团队管理员有徽标与入口、切全员能看到成员列且
  `scope=TEAM`），共 17 条；三个账号的实测结论写进用例注释。
- **顺带修的可发现性**：第一版入口只放在「我的任务」面板标题里，用户实测"看不到这个功能"。
  Hero 区补一个金色「全员任务流」按钮（点了滚到面板并切视角），并把用例改成**从 Hero 按钮进入**
  （而不是点面板里的开关）—— 如果哪天这个按钮没了，用例会红。合计 18 条。
### BUG-114: 抬字号"浮出水面"的低对比 tag 没同步修 + 新组件缺键盘焦点环 [Medium]

- **文件**: `styles/global.css`、`styles/variables.css`、`components/workbench/TeamFlowBoard.vue`、
  `components/issue/TaskDetailDrawer.vue`、`views/Dashboard.vue`、`views/ProjectBoard.vue`
- **现象**: 把字号从 11/11.5px 抬到 12px 后，`theme-audit` 报出亮色 `el-tag` 的 info 浅 tag
  （`待开始`/`草稿`，`#909399` on `#f4f4f5`）2.8:1、danger 实心 tag（`高风险`，白字压 `#f87171`）2.77:1；
  同时发现新组件的按钮类控件（`flow-task` / `team-flow__chip` / `admin-scope__option` /
  `dash-hero__admin-entry` / `board__admin-flow`）`outline: none`，键盘 Tab 走过去看不见。
- **根因**: ① 这些 tag 以前 11.5px 小于体检阈值所以没被报 —— 抬字号让它们"浮出水面"，
  但我只改了字号、没回头查对比度；② 新组件只写了 `:hover`，漏了 `:focus-visible`。
- **修复**: ① `--el-color-danger`/`--el-color-error` 亮暗统一压到 `#dc2626`（白字 4.6:1），
  亮色 `--el-color-warning` 从 `#fbbf24`（亮黄压亮底 1.56:1）压到 `#b45309`（深棕黄 4.6:1），
  暗色块保留亮黄（深底要亮字）；info 浅 tag 文字从 `#909399` 抬到 `#64748b`（4.3:1）。
  ② 迭代页"进行中"徽标 `#10b981` on 浅绿底 2.31:1 → 压深到 `#059669`（4.5:1）。
  ③ 新组件（TeamFlowBoard / TaskDetailDrawer / Dashboard / ProjectBoard）所有可点控件
  补 `:focus-visible` 键盘焦点环。
- **教训**: "一处改、多处消"的操作（全局抬字号）会改变被体检命中的元素集合 —— 改完必须立刻
  重跑体检，不能只跑自己改的那几个组件。这次 theme-audit 全站重扫揪出了 4 条隐藏低对比
  （info tag / danger 实心 tag / warning 文字 tag / sprint 进行中徽标）。
- **回归**: `theme-audit` 亮/暗两套主题 0 低对比 / 0 浅色块；小字从 54 处降到 32 处。
### BUG-115: 管理员在成员的任务上"操作按成员逻辑"——写门槛比读门槛严 [High]

- **文件**: `backend/.../service/IssueReviewService.java`、`IssueChangeRequestService.java`、
  `frontend/src/components/issue/governance/IssueReviewPanel.vue`
- **现象**: 用户原话："**管理员账号点击全员工作流进入成员的项目面板后，进行操作怎么是成员的逻辑？**"
  实测（admin 打开 lisi 负责的任务）：详情面板里明写着
  **「只有任务负责人（lisi）可以提交验收」**，管理员被当成普通成员挡住；
  接口层同样返回 `code=403 只有任务负责人可以提交验收`。
- **根因（两条口径打架）**:
  1. **读门槛**在 `ProjectAccessService`：系统管理员 / 团队成员 / 团队管理员三者任一即可；
     **写门槛**却各自实现一遍，`IssueReviewService` 与 `IssueChangeRequestService` 里都只认
     `team_members` 有一行 —— 于是管理员"能看进去，一动手就被拦"，且系统管理员看别的团队时
     连写都进不去。
  2. **同一条治理链上两套规则**：同一个 `IssueReviewService` 里，「验收」分支给了管理员逃生口
     （`isAdmin`），「提交验收」分支没给。成员休假时管理员连代提交都做不到。
- **修复**:
  1. 两个 Service 的 `assertProjectMember` 改为委托 `ProjectAccessService.requireProject(...)`，
     写门槛与读门槛合成一个口径（单测用 `verify` 锁住"必须走统一入口"）。
  2. `submit()` 抽出 `assertCanSubmit(issue, submitterId)`：负责人 **或** 管理员（系统管理员 /
     项目负责人 / 团队管理员）都能提交，报错文案改成"只有任务负责人或管理员可以提交验收"。
  3. 前端 `IssueReviewPanel` 的 `isAdmin` 补上 `userStore.isManager`（后端算好的 `adminScope`）——
     旧字段兜底的团队管理员在 RBAC 里没有 `team.manage`，只看权限码会被判成普通成员（同 BUG-113）。
- **刻意保留**: 管理员代提交后**不能自己验收**（`submitterId == userId` 直接拒），职责分离不变；
  也就是说管理员代提交后要由报告人/另一位管理员验收。
- **回归**: 新增单测 `IssueReviewServiceSubmitGateTest` 7 条（负责人/普通成员/系统管理员/
  项目负责人/团队管理员/未指派两例/门槛委托）；`e2e/admin-flow.mjs` 新增 1 条
  （从全员流点成员任务 → 在项目里打开 → 「提交验收」可点且无拦截说明），共 19 条。
- **教训**: "能看见但动不了"是最难自证的一类缺陷 —— 读接口通、界面正常渲染，只有真去点一下才暴露。
  **凡是 `isXxxMember()` 这类判定，只能有一个入口**；再次验证了"同一件事两处各写一遍必然对不上"。
### BUG-116: 全站还剩 106 处 <12px 小字（历史遗留），抬字号又暴露两处布局问题 [Low]

- **文件**: 26 个文件、106 处 `font-size` 声明（`CalendarView` 19 / `TeamDetail` 10 / `SprintView` 10 /
  `AiChatPanel` 8 / `Dashboard` 8 / `ProjectBoard` 7 …）
- **现象**: 上一轮（BUG-114）只收了新组件与常见组件，实测各页仍有 9–11.5px 的文字：
  看板概览标签 11px、项目概览动态时间 11px、日历格子 11px、等级/角色管理 11.5px、
  日历时刻侧栏甚至 9px。
- **修复**: 一次性把 `9 / 9.5 / 10 / 10.5 / 11 / 11.5px` 全部抬到 12px（106 处），
  然后用"裁切探针"（`overflow: hidden` 且 `scrollHeight/scrollWidth` 超出）逐页验证是否撑坏：
  13 个真实路由 **小字 0 / 页面横向溢出 0**，只剩两处被裁切。
- **抬字号暴露的布局问题（顺手修）**: 团队详情「关联项目」卡片里的删除按钮原本是
  `opacity: 0` —— 看不见但**仍占 ~60px 宽度**，把项目名挤到 46px；字号一变大，省略号更明显。
  改成悬停时绝对定位浮在右上角（`pointer-events` 跟着回收），项目名可用宽度 46px → 83px。
  余下 1 处是超长项目名（"示例项目：移动端改版"）在 185px 卡片里的正常省略号，不再处理。
- **教训**: ① 全局改字号这类"一处改、多处"的操作，必须配一个**布局探针**（是否裁切、是否横向溢出），
  只看小字数量归零不算通过；② `opacity: 0` 的按钮仍是布局成员 —— 这是"看不见的占位"，
  比 12px 字号更值得修；③ 探测脚本要覆盖**真实路由**（`/projects/1/sprints` 而不是 `/sprints`），
  否则路由兜底重定向会让你误判"这个页面没问题"（我第一次就踩了，四个页面全部量成同一个页面）。
- **回归**: 13 页小字 0；`theme-audit` 亮暗 0 低对比 / 0 浅色块；e2e 7 套 66 条全绿。
### BUG-117: 十几处"自己实现的写门槛"口径不一致——管理员进别人的团队能看不能动 [High]

- **文件**: `ProjectAccessService`（新增 3 个判定方法）+ 9 个 Controller/Service：
  `BoardController`、`IssueController`、`IssueGovernanceController`、`RagController`、
  `DeliveryAnalyticsController`、`WorkflowController`、`SprintController`、`TeamController`、
  `SprintPlanningService`、`WikiService`、`WorkLogService`（4 处）、`IssueReviewService`
- **现象**（BUG-115 的同族问题，这次是系统性排查出来的）:
  1. **系统管理员进别人团队 = 403**：`SprintController`、`TeamController` 的判断是
     "团队成员 **或** 团队管理员"，漏了系统管理员 —— 而项目/看板接口是放行的，于是
     "能进项目，打不开迭代、看不到团队页"。
  2. **注释和代码直接矛盾**：`WikiService.requireMember` 注释写"含系统/团队管理员"，
     代码只放行了系统管理员，团队管理员进不去知识库。
  3. **团队管理员看不到待验收**：`IssueReviewService.pendingReviews` 先用
     `isTeamMember` 过滤，不在成员表里的团队管理员直接被 `continue` 掉。
  4. **删别人的工时**只有"创建人或团队管理员"，系统管理员反而删不掉。
- **根因**: 同一个判断题（"这人能不能动这个项目"）被 11 个地方各写一遍，
  每次漏一个角色。BUG-115 修了两个，这次把剩下的全部收敛。
- **修复**: `ProjectAccessService` 增加三个**判定用**（不抛异常）方法，成为唯一口径：
  | 方法 | 口径 | 用在哪 |
  |------|------|--------|
  | `canAccessProject(projectId, userId)` | 系统管理员 / 团队成员 / 团队管理员 | 看板、任务、工作项治理、知识库、交付分析、工作流、迭代规划、工时 |
  | `canAccessTeam(teamId, userId)` | 同上，按团队 | 团队详情 |
  | `canManageProject(projectId, userId)` | 系统管理员 / 项目负责人 / 团队管理员 | 删除别人的工时等"管理层"动作 |
  各调用点保留自己的错误文案（"无权操作此看板"…），只把判定换掉。
- **刻意不动**: 团队聊天（`ChatController`，成员私聊不该被管理员随便翻）、
  建/改/删项目与团队加入审批（`ProjectController`/`ApprovalController`，本来就该是团队管理员）。
- **回归**: `ProjectAccessServiceTest` 由 3 条扩到 **9 条**（系统管理员不在团队里、团队管理员不在成员表里、
  外人、项目不存在、团队读、项目管理层）；`WikiServiceTest`/`SprintPlanningServiceTest`/`AccessControlTest`
  改成注入**真实**的 `ProjectAccessService`，避免"测的是 mock 不是规则"。后端 **187 项**全绿。
- **现场双向验证**（真库）：临时建团队 39 + 项目 43，把 admin 从 `team_members` 删掉后
  以系统管理员身份请求 5 个接口全部 `200`；同一批接口换外人账号全部 `403`。
  验证数据与迁移文件 `e2e/cleanup-bug117.sql` 已清理（projects/teams/columns/workflows/wiki 全 0）。
- **教训**: ①"能看不能动"和"能动看不到"是一对，只修一边必翻车 —— 收敛到**唯一入口**才根治；
  ②注释不是契约，"注释说含团队管理员"这种地方最该当成缺陷线索；
  ③单测里如果注入的是 mock，测的是调用形状，不是规则本身；关键判定要用真实实现。
### BUG-118: e2e 套件之间共享同一个库，`admin-flow` 假设"最后一位成员有任务"——单独跑绿、连跑必红 [Low]

- **文件**: `e2e/admin-flow.mjs`、新增 `e2e/run-all.mjs`、`.github/workflows/e2e.yml`
- **现象**: 把 12 套 e2e 串起来跑（为了接 CI），`admin-flow` 第 19 条
  `Timeout waiting for .member-stream >> .flow-task` 超时；单独跑立刻通过。
  同一轮 `workflow-guard` 也红了一次，之后再跑两次都绿。
- **根因**: 用例取 `.member-stream` 的**最后一个**当"有人有任务的成员列"。
  而全员任务流按成员列表顺序渲染 —— `admin-flow` 自己会注册 `e2e-flowmember*`、
  `core-flows`/`authz` 也会注册临时账号，这些**空闲成员排在最后**，
  于是挑中的那一列是空的，等一个永远不出现的卡片。
  换句话说：**套件不是彼此独立的**，它们共享一个库；谁先跑决定了后者看到的成员顺序。
- **修复**:
  1. 判定改为"挑真正带任务卡片的成员列"（`filter({ has: .flow-task })`），并新增一条检查
     `全员流里至少有一个成员带着任务卡片`——不静默跳过，没有就判失败并打印列数。
  2. 新增 `e2e/run-all.mjs`：一条命令跑全部套件 + 3 个探针，汇总检查点，失败打印输出尾部，
     **有失败退出码 1**（可直接当 CI 判据）；`--only` / `--probes` / `--base` / `--api` 可调。
  3. 新增 `.github/workflows/e2e.yml`（MySQL service + 起前后端 + 跑 `run-all.mjs`）。
- **诚实声明**: CI 配置**没有在真实 GitHub runner 上跑过**（本机无此能力），
  本地用同一条命令验证过两轮全绿；`run-all.mjs` 本身的正/负向行为都验过
  （坏地址 → 退出码 1 且打印 `ERR_CONNECTION_REFUSED`）。
- **数字更正**: 之前文档一直写"e2e 7 套 66 条"，实际仓库里有 **12 套**——
  `core-flows / tour / ai-degrade / xss-markdown / authz` 这 5 套一直在跑、也一直是绿的，
  只是没被纳入我手敲的那 7 条命令里。现在是 **12 套 120 条 + 3 探针**。
- **教训**: ① "我手工跑了 N 套"不等于"仓库里有 N 套"，收口前先 `ls e2e`；
  ② 共享真实数据库的用例天然有顺序依赖，选择器别假设"最后一个是什么"，
  要么挑可验证的目标，要么每个套件自造数据；
  ③ 一条命令 + 退出码，是把"记得跑"变成"不可能忘"的唯一办法。
### 工具补丁：`theme-audit.mjs` 对"两层叠放"控件的测量盲区（非 BUG）

新加的「我的 / 全员」滑动开关，选中项的白字压在**兄弟节点**（滑动指示块）的靛蓝底色上。
审计脚本往上找祖先底色，只能找到容器灰底，于是报"白字压灰底 1.18:1"——
**这是量具的问题，不是界面的问题**（真实叠放是白字压 `#4f46e5`，约 6.3:1）。

处理方式不是加 `skip` 糊过去，而是按真实叠放关系取指示块底色再算，
并在脚本里写清"为什么这里要特例"。改完 `dark 0/0 / light 0/0` 的基线仍然成立。
同类盲区还有渐变背景（已有跳过规则）——量具的边界要写在量具里，别写在人的记忆里。
### KNOWN-025: 全员任务流没有分页，也不含历史趋势 [Low] — 2026-11-25 已解决

- 现状（原记录）：一次请求返回所选团队**所有成员的所有未完成任务**（只有"已完成"的计数，不返回明细）。
- 影响：团队 30 人、未完成 500+ 时首屏会明显变慢；也看不到"上周谁在忙"这类趋势。
- 为什么当时不做：开发库只有 3 人 20 个工作项，做分页/趋势等于在想象的数据上设计。
- **已解决（2026-11-25 / v2.20.1）**：
  - **分页**：`GET /api/workbench/team` 新增 `memberPage` / `pageSize`（默认 8，上限 30）、
    `taskLimit`（每人最多带几条任务，默认 6，上限 20）。**切页在后端做**，
    否则"分页"只是视觉效果、请求照样拉全量。被 `taskLimit` 截掉的任务只给计数
    （`taskTotal`），前端写"还有 N 项没显示·点击展开"（展开到上限 20，按钮文案始终是真实差额）。
  - **筛选/排序也搬到服务端**（`keyword` / `only` / `sort`）：这个是做分页时才发现的连带问题——
    以前筛选在前端算，分母是"当前页 8 个人"，于是"有逾期 3"点进去可能只看到 1 个人。
    现在筛选按钮不再显示条数，改由分页文案写"筛出 N / 全队 M"。
  - **趋势**：返回近 7 天的 `trend`（每天 `created` / `done`），数据取自 `activity_logs`
    的真实事件：新建 = `CREATE`，完成 = `MOVE` 且**目标列**是终态列。
    故意不拿 `issues.updated_at` 近似——项目没有完成时间字段，用更新时间会在"改个标题"时虚增完成数。
    口径随接口下发（`trendNote`），界面上直接写在图下面，不藏在图表背后。
  - 顺带把"终态列"的判定抽成 `BoardColumnRules`（看板拖拽与趋势统计共用一处），
    以前写在 `BoardService` 私有方法里，别处要用只能再抄一遍。
  - 概览数字（成员/未完成/逾期/空闲）**永远按全队算**，不受筛选影响：
    筛一下"空闲"就让"未完成"变成 0 的话，看着像全队突然轻松了。
- 已知边界（写进 `trendNote` 与代码注释）：列被改名后，更早的历史事件里旧列名匹配不上会被跳过
  （少算不多算）；趋势只统计当前还能看到的项目。
- 位置：`WorkbenchService#getTeamWorkbench`、`BoardColumnRules`、`TeamFlowBoard.vue`。

### 验证

- 后端 `mvn test`：**171 项全绿**（新增 4 项：按成员分组与逾期标记、空闲成员保留、
  非管理员被拒、团队管理员只看自己团队）。
- e2e：**7 套 62 条全绿**（review-matrix 13 / admin-flow 15 / main-chain 11 / workflow-guard 8 /
  next-step 6 / chain-links 5 / presence 4）。
- `vue-tsc` 通过；`npm run build` 通过；`probe-layout` 15 页面 0 溢出 0 裁切。
- 数据库：跑完整套 e2e 后 `leftover_e2e=0`、`integrity-check.sql` 16 项全 0。

---

## v2.19.7 — 暗色模式的颜色逻辑 + 界面在编的数字 + 5 条 Low 收口 (2026-11-24)

> 这一轮不加功能，只收拾"已经做出来但做得不诚实、不好看"的地方：
> 暗色模式下浅色块与隐形标题、团队页拿"成员总数"当"在线人数"、部署脚本一敲就上生产。
> 同时补了两把尺子，让"配色对不对""数据有没有孤儿"不再靠肉眼：
> `e2e/theme-audit.mjs`（开浏览器量实际底色与对比度）、`tools/integrity-check.sql`（16 类引用关系）。

### BUG-105: 暗色主题下 Element 品牌色被重置成默认蓝 [High]

- **文件**: `frontend/src/styles/variables.css`
- **现象**: 切到深色后，主按钮/单选框/日历选中态从品牌靛蓝变成另一种蓝（`#409EFF`），
  与亮色主题不是一套色，看起来像"两个产品"。
- **根因**: `element-plus/theme-chalk/dark/css-vars.css` 在 `html.dark` 里把
  `--el-color-primary` 重新赋成 Element 默认蓝；我们的品牌色只写在 `:root`，
  同级选择器下被它盖掉。`--el-color-primary-light-7/8/9`（Element 当"浅色底"用的）
  也没跟着换，暗色下会冒出浅紫色小块（`el-tag--primary`、plain 按钮）。
- **修复**: `[data-theme="dark"]` 与 `html.dark` 双写 primary 全家桶；`light-7/8/9`
  改成低透明度 rgba；`el-button--*:not(.is-plain)` 单独压深底色（白字要压得住），
  `is-plain` 则用亮一档的字色。
- **教训**: 覆盖设计系统时，**派生变量要跟着一起覆盖**；只改主色，浅色派生会露馅。

### BUG-106: 把文字色当底色用，暗色下按钮与选中态直接消失 [High]

- **文件**: `views/Login.vue`、`views/Register.vue`、`views/ProjectBoard.vue`、`styles/global.css`
- **现象**: 暗色下登录按钮看不见；看板「全部/故事/任务/缺陷」选中项、分段控件选中项
  变成"白字压白块"，对比度实测 1.05–1.18:1。
- **根因**: 用 `--text-primary` 当实心块底色（亮色下它是深墨色，看着像黑按钮；
  暗色下它是近白色，于是白字压白底）。分段控件的选中态更直接——写死了 `background:#fff`。
- **修复**: 新增语义变量 `--color-primary-solid`（两种主题都是深靛蓝，白字 4.6:1）给实心色块；
  分段控件选中态改用 `var(--bg-card)`。约定：`--text-*` 只当文字色，永不当底色。
- **教训**: 变量名带语义（"文字色"）就别拿来当另一件事用；跨主题的坑几乎都出在这。

### BUG-107: 页面标题写死深色，暗色下整行看不见 [High]

- **文件**: `views/ProjectList.vue`、`views/CalendarView.vue`
- **现象**: 暗色模式下"项目列表""日历"两个页面标题几乎不可见（对比度 1.09–1.13:1）。
- **根因**: 标题用 `ShinyText`（渐变 + `background-clip:text`），调用处传 `color="#0f172a"`
  写死深色。工作台早就传的是 `var(--text-primary)`，属于没统一。
- **修复**: 两处改成 `color="var(--text-primary)"`。
- **教训**: 渐变文字读不到"当前主题"，颜色必须由调用方给变量。

### BUG-108: 头像白字压在浅色底上看不清 [Medium]

- **文件**: `frontend/src/utils/color.ts`
- **现象**: 人员头像（琥珀 `#f7b955`）上的首字母白字对比度 1.75:1，团队绿 `#00b96b` 2.58:1。
- **根因**: 三色系取的是"好看"的亮色，但头像上压的是白字，没算对比度。
- **修复**: 压深到 `#2563eb / #047857 / #b45309`（白字 5.0–5.5:1）；另加 `readableInk()`，
  给"颜色来自数据"的地方（日历事件条）按底色自动选白字或深字。
- **教训**: 颜色是**数据带出来**的地方，写死 `color:#fff` 就一定有一批数据翻车。

### BUG-109: 引用了不存在的 CSS 变量，面板没有底色 [Medium]

- **文件**: `components/issue/governance/IssueVersionPanel.vue`、`views/DeliveryAnalytics.vue`、
  `components/common/{VelocityChart,BurndownChart,CfdChart,AiChatPanel,EmptyGuide}.vue` 等
- **现象**: 版本面板与交付分析里有几块区域没有背景色（透出页面底），暗色下尤其糊；
  图表空状态的文字颜色也没跟上主题。
- **根因**: 写了 `var(--surface-secondary)`（无 fallback）、`var(--text-tertiary)`、
  `var(--mimo-text-primary)` 等**从未定义**的变量——早期设计系统的残留名。
- **修复**: 全部换成真实变量（`--bg-subtle` / `--text-muted` / `--text-secondary` 等）。
- **教训**: 无 fallback 的 `var()` 失效时是"静默降级"，屏幕上看不出是 bug，只会觉得"丑"。

### BUG-104: 逻辑删除的项目留下了 project_members 孤儿 [Medium]

- **文件**: `tools/integrity-check.sql`（新）、`e2e/cleanup-residue.sql`
- **现象**: 新写的体检脚本一扫就报 `项目成员指向不存在的项目 = 6`，
  6 行都挂在 `deleted=1` 的项目 10 / 23 上。
- **根因**: 项目是**逻辑删除**、`project_members` 是**物理删除**，两者不会自动同步；
  现行代码（`ProjectService.deleteProject` / `DemoDataService.clear`）都是"先删成员再删项目"，
  但这 6 行来自更早的删除路径（手工 SQL / 旧脚本），于是留下孤儿。
- **修复**: 清掉现存 6 行；`cleanup-residue.sql` 加 §8 通用孤儿清理（跑完 e2e 兜一遍）；
  新增 `tools/integrity-check.sql` 覆盖 16 类引用关系，一分钟内可查完。
- **教训**: 没有外键就别指望"数据一定对"，得有个随时能跑完的体检表。

**KNOWN-013 收口：团队页的"在线人数"不再是成员总数**

- 以前 `onlineCount = computed(() => members.value.length)`，文案改「N 位成员」只是消了误导，
  页面上仍然没有真实在线数据。
- 现在：`PresenceService` 维护 `sessionId → userId`；`WebSocketAuthInterceptor` 在 CONNECT 登记、
  DISCONNECT 注销，`WebSocketPresenceListener` 用 `SessionDisconnectEvent` 兜住
  "网络闪断 / 进程被杀"（没有 DISCONNECT 帧）。接口 `GET /api/teams/{id}/presence`
  返回 `onlineCount / totalMembers / onlineUserIds`；团队页显示「N 人在线 · M 位成员」，
  在线成员头像挂绿点，首屏补两次短延时刷新 + 30 秒轮询。
- **顺手发现的真问题**：简单 broker 默认不发心跳，关掉页面后服务端要等 Tomcat 会话超时
  才认为人走了——在线人数会长时间虚高。给 `enableSimpleBroker` 配
  `setHeartbeatValue(10s)` + 调度器后，e2e 的"关页面 → 20 秒内回落到 0"才成立。
- 已知边界（写在 `PresenceService` 类注释里）：只算本实例（与 KNOWN-002 同源）、
  多标签页按 userId 去重、断线最迟一个心跳周期才掉。

**KNOWN-016 收口：deploy.ps1 加确认步骤**

- 原脚本"执行即发布"：直接 `Stop-Process` 杀本地 java、`mvn clean package`、scp 覆盖生产、
  `systemctl restart`，五步连跑无停顿。
- 现在先打印要动的目标与 5 个动作，再 `Read-Host` 要求敲 `y`；`-Force` 才跳过（CI 用）。
  `scp` / `ssh` 失败也补了显式判断，不会失败后继续打印 "Done"。
- 自测：`"n" | powershell -File deploy.ps1` → 「已取消，什么都没改。」退出码 1。

**KNOWN-021 收口：硬编码颜色有了尺子**

- 新增 `tools/theme_literals.py`：把样式块里的**中性色字面值**（深字 `#0f172a`、白底 `#fff`、
  浅底 `#f1f5f9`、边框 `#e2e8f0` 等）映射到语义变量；`--check` 只统计、`--write` 才落盘。
  本轮 `--write` 一次替换 **82 处 / 10 个文件**（SprintView 独占 58 处），之后 `--check` 归零。
  它故意不动品牌色/状态色/渐变/图表配色——那些是刻意的。
- 判据从"肉眼看着还行"变成两个可复现的数字：`theme_literals.py --check ＝ 0`、
  `e2e/theme-audit.mjs` 暗色 `浅色块 0 / 低对比 0`。
- 残留的 `rgba(255,255,255,…)` 都在渐变/深色 hero 上，属刻意设计，不在检查范围。

**KNOWN-007 处理口径：不修外键，改给体检工具**

- 结论：**保持无外键**。逻辑删除与外键约束冲突太大（删项目会级联失败），
  加外键的代价高于收益。
- 补偿：`tools/integrity-check.sql` 定期体检 + 文件头写清"为什么没有外键"。
  本轮的 BUG-104 就是这个工具扫出来的。

**KNOWN-023 收口：跑完 e2e 不留垃圾**

- `cleanup-residue.sql` 已能按前缀清账号/团队/项目，本轮补 §8 通用孤儿清理。
- 跑完整套 e2e 后核对：`leftover_e2e=0`、`orphan_notifications=0`、`orphan_burndown=0`，
  且 `integrity-check.sql` 16 项全 0。

### 验证

- 后端 `mvn test`：**167 项全绿**（新增 `PresenceServiceTest` 6 项、`TeamServiceTest` 在线人数 1 项）。
- e2e：`presence.mjs` **4/4**；5 套旧用例回归全绿，合计 **47 条断言**。
- `e2e/theme-audit.mjs`：暗色 `浅色块 0 / 低对比 0`，亮色 `0 / 0`（14 个页面 × 2 主题）。
- `tools/integrity-check.sql`：16 类引用关系全 0。
- `vue-tsc` 通过；`npm run build` 通过。

---

## v2.19.6 — 工作流节点能指定责任人了：一步一交接，卡在谁那一目了然 (2026-11-24)

> 上一轮之后还剩一个空洞：工作流能规定"从哪到哪"，却规定不了"这一步归谁"。
> 于是"开发 → 测试 → 验收 → 上线"这种多节点移交，配不出真实的责任链。

### 节点责任人（工作流配置的节点上多了 `ownerUserId`）

- 设计器里每个节点多一个「责任人」下拉（选项来自团队成员，可清空）。**留空 = 不限制**。
- 语义是"**谁负责把工作项从这一步交给下一步**"：指定之后，只有这个人能推动该节点的流转，
  其他人拖动会被告知「这一步由「张三」负责，只有他或项目管理员能推进」。
- 项目管理员（系统管理员 / 项目负责人 / 团队管理员）永远能推进——这是**故意留的逃生口**：
  责任人休假、离职、调岗时流程不能卡死。这条写进了接口文档和设计器提示里。
- 只读表格多一列「这一步谁负责」，节点卡片上挂责任人标签：不点编辑就看得出谁管哪一段。

### 顺手把"能不能走"搬到前端（`GET /workflow/transitions`）

以前这个接口只回"有哪些目标列"，前端只能先让用户拖、被拦住再解释。现在每条转换带：

| 字段 | 含义 |
|------|------|
| `fromOwnerId` / `fromOwnerName` | 出发节点的责任人（这一步该谁管） |
| `allowed` | 当前登录用户能不能走 |
| `reason` | 不能走时的一句话原因（「这一步由「张三」负责」/「只有项目管理员能执行这一步」） |

看板 `whereCanIGo()` 据此区分两种情形：能走 → "当前可以从这里去：进行中"；
全被责任人挡住 → 直接说"这一步由「张三」负责，你推不动这一步"。

**只下确定的结论**：`reason` 只对"责任人不是当前用户"和"规则写着仅管理员"两种情况给判断；
`ASSIGNEE` / `REPORTER` 这类条件需要具体工作项才判得准，接口不猜，真正的拦截仍在
`validateTransition` 里做——避免出现"接口说不允许、实际又能过"的假提示。

### 配置校验

`validateConfig` 现在校验责任人必须是真实用户：配错 id 直接
`400 节点「测试」的责任人不存在（userId=…）`，而不是存进去之后"所有人都推不动，
还查不出为什么"。

### 自测踩到的坑（值得记一笔）

写 e2e 时先拿 `zhangsan` 当"普通成员"验证拦截，结果卡片顺利过去了。查库才发现
**开发库里 admin / zhangsan / lisi 在 `team_members` 里的 role 全是 `ROLE_ADMIN`**——
三个人都是团队管理员，而"管理员能推进"是设计内的放行口。于是用例改成现场注册一个
`e2e-wfowner*` 账号、以 `ROLE_MEMBER` 拉进团队再验证（`cleanup-residue.sql` 按 `e2e-%`
前缀收掉它）。教训：**用"看起来像普通成员"的既有账号测权限，可能测的是管理员路径**。

顺带修掉一处提示重复：被责任人挡住时后端已经回了完整一句，前端又拼了一句
"…，你推不动这一步"，同一层意思出现两遍；现在只在能补充方向时才合并。

### 验证

- 后端 `mvn test`：**160 项**全绿（WorkflowServiceTest 19 → 25，新增 6 条：
  非责任人被拒 / 责任人放行 / 管理员兜底放行 / 接口说出卡在谁那里 / 责任人不存在拒绝保存 /
  读配置回填责任人名字）。
- e2e `workflow-guard`：**8/8**（新增 4 条：纯成员推不动、提示点名、管理员能推、
  设计器每节点都有责任人下拉）。真拖拽 + 真登录，不是 mock。
- 前端 `vue-tsc` 通过、`npm run build` 通过。

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

### KNOWN-002: Redis 未实际使用 [Low] — 2026-11-26 已解决（v2.21.0）

- **文件**: `pom.xml`, `application.yml`
- **现象**: `spring-boot-starter-data-redis` 已引入，Redis 配置已写入，但仅用于单设备登录 Token 校验，WebSocket 仍使用内存代理
- **影响**: 多实例部署时 WebSocket 消息无法跨实例同步
- **建议**: 切换到 Redis-backed WebSocket message broker
- **复核（2026-11-26）**: 实际情况比记录的还严重——`MimoApplication` 排除了 `RedisAutoConfiguration`，
  `StringRedisTemplate` 从未存在过，两处 `@Autowired(required=false)` 永远是 null，
  连"单设备登录校验"都从未运行；`spring.redis.*` 是一段死配置。
- **收口（v2.21.0）**:
  - 在线状态抽成 `PresenceStore`：默认进程内（行为不变），探测到 Redis 时用 `RedisPresenceStore`
    （`mimo:presence:sess|user|online` + 90 秒 TTL + 30 秒心跳续期）；
  - 下行消息抽成 `RealtimeBridge`：`RedisRealtimeBridge` 本地投一次 + 发布到 `mimo:bus:realtime`，
    各实例按 `origin` 丢自己的那份，保证每个浏览器只收一次；
  - 连不上 Redis 不影响启动（`mimo.realtime.store=auto` 启动探测），降级后接口下发
    `presenceShared=false` / `presenceNote`，界面写「N 人在线（仅本实例）」；
  - 单设备登录改用 `mimo.security.single-device-login`（默认关）——接上 Redis 后它本来会突然生效。
- **验证**: 双实例真机（:8080 A / :8081 B 共用一个 Redis）——B 上的连接出现在 A 的在线列表、
  A 发的团队聊天被 B 上的客户端收到、断开后立即回落；改成 `memory` 模式再跑一遍，
  这两条**按预期不成立**（`e2e/two-instance-presence.mjs`）。后端 207 项测试、e2e 13 套 132 条 + 3 探针全绿。
- **边界**: 断线到"算离线"最长差一个 TTL（90 秒），因为进程被杀时收不到 DISCONNECT；
  在线状态仍只表示"连着 WebSocket"，不等于活跃度。

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

### KNOWN-007: 数据库无外键约束 [Low] — 已解决（判定为设计选择 + 补体检工具，v2.19.7）

- **文件**: `init.sql`
- **现象**: 所有表之间通过应用层代码维护关系，无 DB 级外键
- **影响**: 可能出现孤立的 `comments`、`attachments` 等记录
- **当前状态**: 设计选择，非必须修复

- **收口（v2.19.7）**: 明确**不建外键**（逻辑删除与外键冲突），补偿手段是
  `tools/integrity-check.sql`（16 类引用关系，一分钟跑完）。本轮 BUG-104 就是它扫出来的。

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

### KNOWN-013: 在线人数始终显示总成员数 [Low] — 已解决（v2.19.7）

- **文件**: `frontend/src/views/TeamDetail.vue`
- **根因**: `onlineCount = computed(() => members.value.length)` 取的是成员总数
- **建议修复**: 通过 WebSocket 统计真实在线连接数
- **复核（2026-11-23）**: 仍在（`TeamDetail.vue:591`），但**文案已改成"N 位成员"**（第 166 行），
  不再对外宣称是"在线人数"，误导消除；真实在线统计仍未实现，故保持未关闭。

- **收口（v2.19.7）**: 实现 `PresenceService` + `GET /api/teams/{id}/presence`，
  团队页显示真实「N 人在线 · M 位成员」，并为简单 broker 配了 10 秒心跳；
  `e2e/presence.mjs` 4/4 覆盖（含"关页面后在线数回落到 0"）。详见 v2.19.7 段。

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

### KNOWN-016: 部署脚本 deploy.ps1 无确认步骤 [Low] — 已解决（v2.19.7）

- **文件**: `deploy.ps1`
- **现象**: 执行即覆盖服务器生产环境，无确认提示
- **建议修复**: 加 `Read-Host "确认部署? (y/n)"` 确认步骤
- **复核（2026-11-23）**: 仍在 —— `deploy.ps1` 全文件无 `Read-Host` / 无二次确认，
  0/4～4/4 五步直接连跑（其中第 0 步会 `Stop-Process` 杀掉占用 jar 的 java 进程）。


- **收口（v2.19.7）**: 加目标说明 + `Read-Host` 确认（`-Force` 跳过，给 CI 用），
  `scp`/`ssh` 失败显式抛错。自测 `"n" | powershell -File deploy.ps1` → 退出码 1、什么都没改。

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

> **v2.19.8 新登记**：KNOWN-025（全员任务流没有分页、也不含历史趋势，Low）。
> 条目正文写在文件顶部 v2.19.8 段里（同一条不写两个标题，`tools/buglog_stats.py` 按标题计数）。

### KNOWN-024: e2e 用例没有接进 CI，只能本地手工跑 [Low] — 2026-11-27 已解决（真实 runner 全绿）

- **文件**: `e2e/*.mjs`、`e2e/run-all.mjs`、`.github/workflows/e2e.yml`
- **现象**（原记录）: 5 套 38 条用例全靠人工 `node e2e/xxx.mjs` 触发；改完代码没人跑就等于没测，
  合代码时也没有"红了不许合"的闸门
- **风险**: 回归靠自觉。这一轮我自己就出现过"合并后忘了重跑"，靠补跑才补回证据
- **已落地（2026-11-25 / v2.20.0）**:
  - `e2e/run-all.mjs`：一条命令跑全部 **12 套 120 条 + 3 个探针**，汇总检查点，
    失败打印输出尾部，**有失败退出码 1**；支持 `--only` / `--probes` / `--base` / `--api`。
    正/负向都验过：坏地址 → 退出码 1 且打印 `ERR_CONNECTION_REFUSED`。
  - `.github/workflows/e2e.yml`：MySQL service + 起后端 + `vite preview` + 跑 `run-all.mjs`，
    失败留存日志工件。
  - 顺带修掉 BUG-118（连跑时 `admin-flow` 因"挑最后一列成员"而超时）。
- **仍未闭合的部分（如实记）**: 这份 workflow **没在真实 GitHub runner 上跑过**
  （本机没有 CI 能力），首次接入需要留一次调参时间。等它真红/真绿过一次再关掉这条。
- **2026-11-26 / v2.22.2 进展（BUG-123~125 就是演练照出来的）**:
  - 本机按 workflow 的步骤**从零演了一遍**（独立空 MySQL 实例、空库、同一条命令），
    第一个 30 秒就撞上"仓库建不出数据库"——原来 `init.sql` 从没被执行过、
    启动期 Runner 顺序未定、空库没有任何账号。三条都修了，详见 BUG-123~125。
  - 演练环境（零起点）上全量 e2e **15 套 148 条 + 3 探针全绿**，
    说明"从空库到全绿"这条链路本身没有别的暗坑了。
  - workflow 也改成与本机验证过的命令一致：用 `vite` dev server（`preview` 不配代理会全 404）、
    `MIMO_BOOTSTRAP_ADMIN_*` 引导管理员、`docker exec` 灌演示账号、建表结果自检、
    预热前端编译、`playwright@1.65.1` 装在仓库外。
  - **只剩一层风险：Linux/runner 与 Windows 的环境差异**。仍需人开一次 Actions 页面点
    Run workflow（或给带 `actions:read` 的 token）。
- **已解决（2026-11-27 / v2.22.3）**:
  - 真实 runner 上跑通：[run #8](https://github.com/Oo7350/mimo/actions/runs/45926155601)，
    `workflow_dispatch`，**8 分 36 秒全绿**，其中「跑全部 e2e」**5 分 41 秒**；
    空库自动建表（自检 ≥30 通过）、`docker exec` 灌种子、`seed-fresh-db.mjs` 预热、
    `playwright@1.65.1` + `--with-deps chromium` 全部在 Linux 上一次过。
  - 上 CI 前的 5 处"环境差异"也都提前消掉了：`vite preview` 换 dev server（preview 不配代理）、
    `PLAYWRIGHT_MODULE` 的外置安装、`presence-source-label` 写死的 `C:/…` 默认路径、
    `.gitattributes` 钉 LF、`docker exec` 输出清洗 + `nohup` 起服务。
  - 顺带纠正记录本身：v2.22.0 那次（[#4](https://github.com/Oo7350/mimo/actions/runs/45775384583)）
    **其实跑过并红在「启动后端」**，此前被记成"从没跑过"。两条的区别很大：
    "没跑过"是没证据，"跑过红了"是有反证据没人看。
- **原始建议**: MySQL service + 起后端 + 起前端 + 依次跑用例，任一非 0 退出即失败。

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

### KNOWN-021: 前端仍有硬编码颜色残留 [Low] — 已解决（v2.19.7）
- **文件**: `frontend/src/**/*.vue`、`styles/global.css`
- **现象**: 全仓仍有约 42 处 `rgba(255,255,255,…)`、10 处 `background: #fff`、若干 `#f8fafc/#f5f7fa/#f1f5f9`；本次只修了已经被截图暴露出来的部分
- **风险**: 换主题或新增页面时继续出现"某一块是白的"
- **建议**: 增加 stylelint 规则禁止颜色字面量（或至少警告），逐步替换为 CSS 变量

- **收口（v2.19.7）**: 新增 `tools/theme_literals.py`（`--check` 归零才合格，
  `--write` 本轮替换 82 处/10 文件）+ `e2e/theme-audit.mjs`（暗色浅色块 0、低对比 0）。
  建议里的"stylelint 规则"用这个脚本等效实现：它只禁中性色字面值，不动品牌色与渐变。

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

### KNOWN-023: e2e 用例不清自己造的账号 / 团队 / 项目，开发库会累积残留 [Low] — 已解决（v2.19.7）

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
| High | 52 |
| Medium | 51 |
| Low | 26 |
| **已修复（BUG 条目）** | **135** |
| **已知未修复（KNOWN 条目）** | **0** |
| **总计** | **135** |

| 版本段 | BUG 条数 |
|--------|---------|
| v2.22.4 — 量级验证 + 缺失索引 + 就绪信号 + 双实例串行（BUG-126~135）(2026-11-27) | 10 |
| v2.22.3 — 关闭 KNOWN-024：CI 在真实 runner 上第一次全绿（无新 BUG）(2026-11-27) | 0 |
| v2.22.2 — 全新环境从零到绿：空库建不出表 / 启动顺序撞车 / 空库没账号（BUG-123~125）(2026-11-26) | 3 |
| v2.22.1 — 迭代规划空态说清原因（BUG-122）+ 计划书两条过时备注改成实话 (2026-11-26) | 1 |
| v2.22.0 — 工作流自由节点 + 画布连线 + 验收标准前提（BUG-121 终态开关不生效）(2026-11-26) | 1 |
| v2.21.0 — Redis 真的用起来了：跨实例在线状态与广播（关闭 KNOWN-002，BUG-119/120）(2026-11-26) | 2 |
| v2.20.0 — e2e 一键入口 + CI 配置 + 连跑必红的用例（BUG-118）(2026-11-25) | 1 |
| v2.20.1 — 全员任务流分页 + 服务端筛选 + 7 天趋势（关闭 KNOWN-025，修复测试自身缺陷）(2026-11-25) | 0 |
| v2.19.8 — 管理员视角：身份徽标 + 全员任务流 + 任务详情抽屉（BUG-110~113）(2026-11-24) | 4 |
| v2.19.7 — 暗色配色系统性修正 + 在线人数落实 + Low 收口（BUG-104~109）(2026-11-24) | 6 |
| v2.19.6 — 工作流节点责任人 + 交接说明（无 BUG，纯功能）(2026-11-24) | 0 |
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
- **收口（v2.19.7）**: `cleanup-residue.sql` 补 §8 通用孤儿清理；跑完整套 e2e 后
  `leftover_e2e=0 / orphan_notifications=0 / orphan_burndown=0`，`integrity-check.sql` 16 项全 0。
