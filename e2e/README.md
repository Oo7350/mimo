# Mimo 端到端用例（核心链路）

对应 `bug-log.md` **KNOWN-017**：「核心业务链路缺少端到端验证」。
这里用真实浏览器把主链路点一遍，并对接口结果做断言——不是"页面能打开"，而是"操作真的生效"。

## 一条命令跑全部（`run-all.mjs`）

```bash
node e2e/run-all.mjs                    # 15 套套件，约 10 分钟
node e2e/run-all.mjs --probes           # 连 3 个探针（版面 / 布局 / 配色）一起
node e2e/run-all.mjs --only admin-flow,review-matrix   # 只跑挑中的（改哪儿跑哪儿）
node e2e/run-all.mjs --base http://localhost:5173 --api http://localhost:8080/api
```

- 全部通过 → 退出码 **0**；有任何一套失败 → 退出码 **1**，并打印该套输出的尾部（不用再去翻日志）。
- 前提：后端 `:8080`、前端 `:5173` 已启动；`PLAYWRIGHT_MODULE` / `PLAYWRIGHT_BROWSERS_PATH` 指向装好的 playwright。
- **Redis 不是前提**：后端不带 Redis 也能跑（在线状态退回进程内实现），
  `presence-source-label.mjs` 用拦截接口的方式覆盖两种口径，不依赖真实 Redis。
- 跑完执行 `e2e/cleanup-residue.sql` 清临时账号（`run-all` 结束会提醒）。

当前清单（**15 套 148 条 + 3 探针**）：

| 套件 | 条数 | 覆盖 |
|---|---|---|
| `main-chain.mjs` | 11 | 主链路闭环 |
| `core-flows.mjs` | 6 | 建任务/拖拽/评论/附件/验收/建 Sprint |
| `review-matrix.mjs` | 13 | 验收权限矩阵（谁能通过/打回/提交） |
| `next-step.mjs` | 6 | 详情页「下一步」建议 |
| `chain-links.mjs` | 5 | 链路口径一致性 |
| `workflow-guard.mjs` | 8 | 工作流转换守卫（含节点责任人） |
| `workflow-canvas.mjs` | 10 | 工作流画布：自由节点投影成列 + 拖连线 + 验收标准前提 |
| `sprint-plan-empty.mjs` | 6 | 迭代规划的空态说清三种原因并给出路（v2.22.1，BUG-122） |
| `admin-flow.mjs` | 26 | 管理员视角（徽标/全员流/分页/筛选/趋势/抽屉/主题/权限/代提交） |
| `presence.mjs` | 4 | 在线人数按会话算 |
| `presence-source-label.mjs` | 6 | 在线人数的口径写在界面上（「仅本实例」/ 共享两种口径，v2.21.0） |
| `tour.mjs` | 9 | 新手引导（跳过/重播/Esc） |
| `ai-degrade.mjs` | 3 | 未配 Key 的降级表现 |
| `xss-markdown.mjs` | 10 | Markdown 渲染 XSS（跑仓库真实源码） |
| `authz.mjs` | 25 | 接口级越权（外人打各读接口必须 4xx） |

**不进 `run-all` 的验证（需要两台后端）**

```bash
node e2e/two-instance-presence.mjs            # A:8080 / B:8081 共用一个 Redis
node e2e/two-instance-presence.mjs --local    # 反向对照：探针实例用 MIMO_REALTIME_STORE=memory
```

`two-instance-presence.mjs` 用 STOMP 客户端连 B 实例，然后问 A 实例的
`/teams/{id}/presence`、通过 A 发团队聊天，验证"跨实例在线"与"跨实例广播"；
`--local` 模式把 A 换成进程内实现，同样的两项**应当不成立**——用来证明测的是真共享。

> **套件不是彼此独立的**：它们共用同一个开发库。曾经出现过"单独跑绿、连起来红"——
> `admin-flow` 挑了"最后一个成员列"，而别的套件注册的空闲账号正好排在最后（BUG-118）。
> 所以：① 选择器别假设"最后一个是什么"；② 用例崩了之后再跑，先清残留；
> ③ 新套件尽量自造数据、自己收尾。

### 全新环境预热（`seed-fresh-db.mjs`，v2.22.2）

e2e 全家都假定「团队 1 + 项目 1 + 三列看板（待办/进行中/已完成）」。本机库是几个月长出来的，
空库上直接跑会从第一步就红——所以 CI 与任何新环境先跑这个：

```bash
# 前提：后端已启动（空库会自动建表），且已灌过演示账号
mysql -u mimo -p mimo < backend/mimo-app/src/main/resources/db/seed-demo.sql
node e2e/seed-fresh-db.mjs          # MIMO_API=http://127.0.0.1:8080/api 可改地址
```

它全程走真实 HTTP 接口、不碰 SQL，幂等：管理员能登录（由后端 `MIMO_BOOTSTRAP_ADMIN_*`
在启动时创建）→ 团队取第一个或建一个 → 项目取第一个或建一个 → 踩出看板列与 STORY 工作流，
最后打印 `teamId` / `projectId` 供和用例里的硬编码对账。

> 团队 id 不是 1 时它会**明确警告**：那种库不是"干净库"，用例里的 `teamId=1` 会对不上，
> 要用干净库或者改用例——别默默改数字。

### 把 CI 的步骤在本机演一遍（v2.22.2 的做法，建议照抄）

`.github/workflows/e2e.yml` 长期"写了但没跑过"。演练方式不是读 YAML，而是**按它的命令在本机执行**：

1. 起一个独立的空 MySQL 实例（别用开发库）：`mysqld --initialize-insecure --datadir=<新目录>`，
   起了之后按 docker-entrypoint 的方式建库建号（`mimo` / `mimo123`）；
2. 用 `MIMO_DB_URL` 指过去、`MIMO_REALTIME_STORE=memory`、`MIMO_BOOTSTRAP_ADMIN_*` 打开空库引导，起后端；
3. 确认它**自己**建出 38 张表（少一半就是迁移链断了）、`admin/123456` 能登录；
4. 灌 `seed-demo.sql` → `node e2e/seed-fresh-db.mjs` → `node e2e/run-all.mjs --probes`。

这一轮演练第一天就撞出三个必崩的坑（建表脚本从没执行 / 启动期 Runner 顺序未定 / 空库没账号，
BUG-123~125）。所以：**改 CI 配置时，先把里面的命令在本机跑一遍**，
"看起来对的 YAML"和"能跑的 YAML"是两件事。

### 跨平台：Windows 开发机 ↔ Ubuntu runner（v2.22.2 体检，v2.22.3 真机确认）

跑不了 CI 的时候，至少把**静态能查的差异**查干净。这套用例现在的规矩：

| 规矩 | 为什么 |
|---|---|
| 不写死盘符/绝对路径 | 模块位置一律 `PLAYWRIGHT_MODULE`，没有就退回包名 `playwright`；写死 `C:/…` 的结果是 CI 上报 `MODULE_NOT_FOUND`，看不出真因 |
| 不 require 仓库内的相对文件 | 大小写敏感的文件系统上，`../Views/x.vue` 在本机能过、Linux 上 import 失败 |
| 子进程用 `process.execPath` | 不依赖 `node` 在 PATH 里、不依赖 `shell: true` 的引号规则 |
| 行尾 LF 由 `.gitattributes` 钉住 | `core.autocrlf` 是每台机器的设置，靠自觉就会有人提交 CRLF；`\r` 混进 `textContent` 断言里就是永远不等的假红 |
| 少用固定毫秒等待 | 等待条件（元素可见 / id 出现）比 `waitForTimeout` 稳，慢机器不会假红 |

`.github/workflows/e2e.yml` 那边同步加了两处：`docker exec` 取表数后 `tr -dc '0-9'`
（多一行 warning 会让 `[ "" -lt 30 ]` 报 `integer expression expected`），
后台进程用 `nohup`（否则 step 结束时被 SIGHUP 带走，表现成"上一步成功、下一步连不上"）。

### CI 实跑记录（v2.22.3，这一格不再是空的）

| 项 | 实测 |
|---|---|
| Run | [#8](https://github.com/Oo7350/mimo/actions/runs/45926155601)（`workflow_dispatch`，`5747663`）+ [#9](https://github.com/Oo7350/mimo/actions/runs/45928085911)（`push`，`e9a1219`） |
| 结论 | **两次都 success**，35 个 step 全绿（skipped 的只有「失败时留存日志」，它只在 failure 时跑） |
| 总耗时 | **8 分 36 秒** / **8 分 57 秒** |
| 空库建表 | 自检通过（门槛 ≥30，本地实测 38） |
| 后端单测 | 31 秒（225 项） |
| 前端依赖+类型检查+构建 | 56 秒 |
| Playwright 安装 | 19 秒（`1.65.1` + `--with-deps chromium`） |
| **全量 e2e** | **5 分 41 秒 / 5 分 43 秒通过**（15 套 148 条 + 3 探针） |

> 怎么自己查（不需要 token，仓库是公开的）：
> `https://api.github.com/repos/Oo7350/mimo/actions/runs` 看列表，
> `…/actions/runs/<id>/jobs` 看每个 step 的 `conclusion`。
> 本机用 curl 走代理会被挡（403），用 web_fetch 或浏览器直接打开就行。

CI：`.github/workflows/e2e.yml`，**已在真实 runner 上跑通**（v2.22.3 起三次全绿，
见上表与 bug-log）。v2.22.4 起是两个 job：

| job | 干什么 | 说明 |
|---|---|---|
| `e2e`（19 step） | 原链路 + 末尾「造量级数据 → 性能探针 → 清量级数据」 | 探针只出数字、**不设门槛**（机器规格会变，设成红线只会随机红） |
| `two-instance`（9 step，`needs: e2e`） | mysql + redis 两个 service，jar 从主 job 的 artifact 取回，起 8080/8081 两个后端共用一个 Redis，跑 `two-instance-presence.mjs` | 单独一个 job：主回归跑的是 `store=memory`，两种语义混在一个进程里会互相干扰 |

**v2.22.4 的第一次真实跑（run #12）**：主 job 8 分 35 秒全绿（含量级数据 + 探针三步），
`two-instance` **红在第 7 步「灌演示账号」**——我把顺序写成了"先灌种子再起后端"，
而空库的建表是后端启动时 `MigrationRunner` 干的活，于是 1 秒内
`Table 'mimo.users' doesn't exist`，后三步 skipped（BUG-130）。

**第二次（run #13）**：重排顺序后主 job 反而红了——「建表结果自检」只数到 **25 张表**（应 38），
而「启动后端」那步是绿的：Spring Boot **先开 HTTP 端口、再跑 `ApplicationRunner`**，
迁移跑到一半 `users` 表就存在了，`AdminBootstrap` 与登录都正常，
于是"能登录"被当成了"库建完"（前三次全绿只是迁移够快凑出来的时序运气，BUG-131）。
现在两处就绪判定都改成：`grep -q "Started MimoApplication"`（这句在 Runner 全部跑完之后才打）
**且** 登录接口能过；自检改成"最多等 60 秒、最后一次不达标才红"，失败时打印
`MigrationRunner` 日志与现有表清单（它不写迁移记录表，只打日志）。

> 两条结论：① **步骤顺序在 workflow 里就是依赖关系**（建表 → 种子 → 用例），不是排版，
> 照旁边已经绿的 job 抄顺序比自己再想一遍快得多；② **"服务能响应"不等于"服务准备好了"**，
> 就绪信号要选"依赖全部完成之后才出现"的那一个，而不是"最早能用的那一个"。

**第三次（run #14，commit `76b2a11`）两个 job 都绿**：

| job | 耗时 | 结果 |
|---|---|---|
| `e2e`（19 step） | 8 分 45 秒（04:49:43 → 04:58:28 UTC） | success，含「造量级数据 → 性能探针 → 清量级数据」三步 |
| `two-instance`（9 step） | 2 分 1 秒（04:58:30 → 05:00:31 UTC） | success，跨实例在线 + 跨实例广播在真实 runner 上首次通过 |

> 探针那一步现在**会红**：只要有接口没跑通就非 0（`--only` 没匹配也非 0），
> 所以它绿就等于"18 个接口都真跑到了"。慢不报错这条仍然保留。

### 量级验证：先造量，再量（v2.22.4）

开发库常年 3 个用户 / 20 个工作项，所以"性能没问题"这句话以前**没有任何数字支撑**。
现在这套是配套的两个文件加一个探针：

```bash
mysql -u mimo -p mimo < tools/seed-load.sql      # 100 用户 / 5 项目 / 500 工作项 / 5000 活动日志
node e2e/perf-probe.mjs --iters 10               # 18 个读接口的 p50 / p95 / max
mysql -u mimo -p mimo < tools/cleanup-load.sql   # 清干净（跑全量 e2e 前必须清）
```

- 压测数据全挂在独立「压测团队」下，**不掺演示数据**；`cleanup-load.sql` 按团队/项目整块删。
- 探针自己找数据：有「压测团队」就用它，没有就退回演示项目——这时会明确打印
  "本次只代表空库基线，别拿它下结论"。
- 退出码：**慢不报错**（`--budget` 才拦），但登录失败 / 有接口没跑通 / `--only` 没匹配一律非 0。
  第一版写成"永远退出 0"，登录失败也是一片绿——那是 KNOWN-024 老毛病的翻版，已改。
- 数 SQL 条数用 `SHOW GLOBAL STATUS LIKE 'Com_select'` 前后差值（`mimo` 账号没有 SUPER，
  `general_log` 开不了）；找热点用 `jcmd <pid> Thread.print` 在压测中采样线程栈。
- 实测结论（同一份数据、10 轮）：`/workbench/team` 100 成员 **606→28 条 SELECT、540→125ms**；
  `/reports/stats` **40→8 条**；其余 16 个接口均 < 60ms。

> 一条踩过的认知：**"分页了"不等于"没白干"**。全员任务流早分了页（每页 8 人），
> 但它仍然把所有成员 × 所有工作项算了一遍——分页只省带宽，不省查询。

### 索引这一层：条数降下来之后，要改看执行计划（v2.22.4）

`Com_select` 只会告诉你"几条"，不会告诉你"每条扫了多少行"。606 条压到 28 条之后接口还剩
125ms，剩下的时间全在少数几条"扫得很凶"的查询上，只有 `EXPLAIN ANALYZE` 看得见：

```sql
EXPLAIN ANALYZE
SELECT DATE(al.created_at) d, COUNT(*) c
FROM activity_logs al
WHERE al.action = 'CREATE' AND al.created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
  AND al.project_id IN (…)
GROUP BY DATE(al.created_at);
```

量级数据不够大时看不出差别，这一轮把活动日志临时放大到 4 万行（近 7 天窗口 3.6 万行）再量：

| 量什么 | 改前 | 改后（`V25__hot_query_indexes.sql`） |
|---|---|---|
| 新建趋势 | 31.4ms，`type=ALL` 扫 40104 行 | **16.4ms**，覆盖索引定位 |
| 移动趋势 | 63.7ms，每项目先捞 7774 行 | **47.2ms**，索引条件下推，每项目 4008 行 |
| `/workbench/team`（关掉/装回索引做 A/B，缓存已热、12 轮） | p95 **387ms** | p95 **141ms** |

- **A/B 必须两个方向都做**：第一次量出 759→177ms（4.3 倍）时缓冲池还是冷的，
  慢里混着"从磁盘读"；反向再量一遍（先撤索引、再装回）才拿到稳的 387→141。
- **同一轮量掉了两个"看着该建"的索引**：`issues (project_id, deleted)` 在 17% 选择性下
  扫表 6.45ms、走索引 7.65ms（更慢）→ 不建；`issues (sprint_id, status)` 在 0.9% 选择性下
  6.47→0.33ms → 建。理由与数字都写在 `V25` 的注释里，省得下次再来一轮。
- **压测数据要长得像真数据**：第一版活动日志的 `detail` 写成 `压测活动 LOAD1-1 #123`，
  于是「移动到终态列」那条趋势查询在压测数据上一行都统计不到，量到的"几毫秒"是**空转**。
  现在 seed 写真实文案（`将 X 移动到 已完成`），末尾还多打一列 `move_to_done` 自证路径有数据。

## 覆盖范围（`main-chain.mjs`）

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

## 工作流画布（`workflow-canvas.mjs`，v2.22.0）

这一版把"节点必须先是一列"拆掉了，顺带把设计器换成真画布，所以单独一套用例盯住三件事：
画布是不是真能画、自由节点会不会真的落成列（并且**说出来**）、连线上加的前提条件是不是真管用。

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 设计器是画布 | 编辑态出现 `[data-workflow-canvas]`，节点卡片 ≥3、连线 ≥1 |
| 2 | 节点能拖动 | 拖把手后卡片 `boundingBox.y` 变化 > 40px |
| 3 | 新节点可以先不绑列 | 点「新节点」→ 卡片 +1，卡片上出现「保存后自动建列」 |
| 4 | 拖圆点能连线 | 从「已完成」的 `[data-port]` 拖到新节点 → 连线数 +1 |
| 5 | 连线能加前提 | 点连线（`dispatchEvent('click')`，SVG 描边点不准）→ 面板含「什么情况下才能做」 |
| 6 | 保存时自动建列并提示 | 接口返回 `createdColumns` 含新节点名，toast 写「为「…」自动创建了看板列」 |
| 7 | 前提存下来了 | 该转换的 `prerequisites` 含 `ACCEPTANCE_DONE` |
| 8 | 看板真的多了一列 | `GET /board/{id}` 里出现该列名，且不是跑之前的列 |
| 9 | 没勾完验收标准拖不进「已完成」 | 提示含「验收标准还有 2 项没勾完」，`columnId` 未变 |
| 10 | 勾完之后同样拖拽能过 | 逐条 `PUT /issues/{id}/acceptance-criteria/{acId}` 勾完 → 拖进「已完成」成功 |

```bash
node e2e/workflow-canvas.mjs http://localhost:5173
```

前置：项目 id=1 有 STORY 工作流（scrum 模板，含「进行中 → 已完成」）。用例**结束时**
把工作流恢复成跑之前的样子、删掉自动建的列与本轮建的卡，所以可以连跑。
唯一会留下的痕迹是工作流 `config` 里节点的 `x/y` 坐标（拖动过）与新增的 `nodeKey`——
这两样在读出侧本来就会自动补齐，不影响行为。

> 踩坑记录：连线是 SVG `path`，Playwright 点元素中心可能落在描边之外，用 `dispatchEvent('click')` 更稳；
> 节点拖动必须抓**左上角的把手**（`[data-drag-handle]`），因为卡片里全是 input / select，
> 整卡 `mousedown` 会被它们的 `@mousedown.stop` 吃掉。

## 迭代规划的空态（`sprint-plan-empty.mjs`，v2.22.1）

对应 `bug-log.md` **BUG-122**：AI 规划面板里候选为空只有一句写死的「Backlog 暂无可规划工作项」，
但"空"有三种原因、下一步完全不同。这套用例证明接口分得清、界面也说得出：

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 空项目 | `emptyReason=NO_ISSUES`，`summary` 含「还没有任何工作项」，`risks` 指路去建 |
| 2 | 有候选时不误报 | 建 2 项后 `emptyReason` 缺省（Jackson `NON_NULL` 不下发），候选 2 项 |
| 3 | 都排进迭代了 | `apply-plan` 之后 `emptyReason=ALL_SCHEDULED`，出路是「先把手上的迭代做完」 |
| 4 | 全做完了 | 全部标记 `DONE` 后 `emptyReason=ALL_DONE`，出路是「先建下一批工作项」 |
| 5 | 界面说原因 + 下一步 | `[data-plan-empty]` 里同时出现后端算出的原因与「下一步：…」 |
| 6 | 空态有出路按钮 | 「去项目看板建工作项」可见可点 |

```bash
node e2e/sprint-plan-empty.mjs http://localhost:5173
```

用例自己建项目 + 迭代 + 卡（名字带 `e2e空态项目{stamp}` 前缀），跑完删项目。

> 断言里有个坑：`emptyReason` 为 null 时 Jackson 直接不下发（`NON_NULL`），
> 所以"没有原因"的判断要写成 `(emptyReason ?? null) === null`，不能写 `=== null`。

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
| 1 | 浏览器连着 WebSocket 时算在线 | 开页后 `onlineCount` **比基线多 1**（基线 = 开页前的人数） |
| 2 | 不会超过成员总数 | `onlineCount <= totalMembers` |
| 3 | 文案两个数都在 | 页面出现「N 人在线 · M 位成员」 |
| 4 | 关掉页面会掉线 | 关浏览器后最多等 40 秒，`onlineCount` **回到开页前的水位**（正常关闭走 DISCONNECT 帧，实测 0.1 秒；被杀进程只能等心跳，最迟一个 10 秒周期 + 调度延迟） |

第 4 条当初是**红的**：简单 broker 默认不发心跳，服务端要等 Tomcat 会话超时才认为人走了。
给 `enableSimpleBroker` 配了 10 秒心跳 + 调度器之后才通过——所以这条用例同时是心跳配置的回归保护。

> **v2.22.1 / v2.22.2 修掉了这套用例的三个脆点**（都不改产品行为）：
> ① 原先断言「关页后 `onlineCount === 0`」，但开发机上常有人真开着页面，永远等不到 0；
> ② 改成"比基线多 1"也不行——在线人数按**人**去重，默认用 admin 登录时，本机若已有
> 一个 admin 会话，再开一页人数根本不变（v2.22.1 就因此红过一次）；
> ③ v2.22.2 连跑时又红：判**总人数**在连跑场景下必然飘——前面十几套的浏览器会话正在陆续
> 心跳超时下线，"我加一人、别人掉一人"总数不变。
> 现在改用**现场注册的独立账号**（`e2e-presence{stamp}`，`ROLE_MEMBER` 入队 1），
> 并且判的是**自己的 id 在不在 `onlineUserIds` 里**：开页必须出现、关页必须消失。
> 固定 `waitForTimeout(3000)` 也换成"等自己的 id 出现（最多 25 秒）"，慢机器不再假红。
> 账号由 `cleanup-residue.sql` 回收。

## 在线口径文案（`presence-source-label.mjs`，v2.21.0）

只把在线数字做准还不够：多实例部署又没接 Redis 时，后端只知道本实例的连接，
数字**只对一台机器成立**。这条用例盯的是"界面有没有说出来"：

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 本实例口径写明来源 | 拦截 presence 接口返回 `presenceShared=false` → 文案含「（仅本实例）」 |
| 2 | 数字没被挤掉 | 文案同时含「N 人在线」与「M 位成员」 |
| 3 | 有说明入口 | 出现 `.td-chat-header__hint` 图标（悬停/点开给出原因） |
| 4 | 共享口径不加标注 | `presenceShared=true` → 文案**不含**「仅本实例」 |
| 5 | 共享口径数字照旧 | 仍含两个数字 |
| 6 | 共享口径无图标 | 说明图标数量为 0 |

```bash
node e2e/presence-source-label.mjs http://localhost:5173
```

用 `page.route()` 直接把 presence 接口拦掉，所以**不需要真起两台后端**，也不依赖本机有没有 Redis。
实测文案：`2 人在线（仅本实例） · 4 位成员` / `2 人在线 · 4 位成员`。

> 跨实例的真实链路验证（在线状态共享 + 消息跨实例送达）在 `two-instance-presence.mjs`，
> 那一条需要两台后端共用一个 Redis，所以不并入 `run-all`，见文首说明。

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

## 管理员视角用例（`admin-flow.mjs`，v2.19.8 起，现 26 条）

对应"管理员账号要和普通账号突出区别、能看到所有成员的任务流、点开能看细致的具体任务"。
它同时是**新 UI 自己的可读性回归网**（对比度那一条当场抓到了 BUG-111、后来又抓到空闲成员那行的 2.76:1，
以及 v2.20.1 里新按钮把 `--color-primary-solid` 当文字色的 2.82:1）。

| # | 检查点 | 断言 |
|---|--------|------|
| 1 | 管理员顶栏有身份徽标 | `.mimo-header__user .admin-badge` 在 10 秒内可见 |
| 2 | 工作台有「我的 / 全员」切换 | `.admin-scope` 存在 |
| 2.5 | Hero 上有「全员任务流」按钮 | `.dash-hero__admin-entry` 可见；后续步骤**就点它**进入全员视角（入口没了用例就红） |
| 3 | 成员列数量与接口一致 | 界面 `.member-stream` 数 == `GET /api/workbench/team` 的 `members.length` |
| 4 | 概览数字与接口一致 | 未完成 / 逾期 / 空闲成员三格分别对得上 `summary` |
| 4.1 | **服务端分页真的切了数据** | `?pageSize=1` 时 `members.length === 1`，但 `memberTotal` / `memberPageCount` 仍是全量口径 |
| 4.2 | **翻页拿到的是不同的人** | 第 2 页首个成员的 `userId` ≠ 第 1 页 |
| 4.3 | **截断但计数不变** | `?taskLimit=2` 时某成员 `tasks.length === 2` 而 `openCount === taskTotal`（拿截断列表算负载会让标签说谎） |
| 4.4 | **筛选在服务端生效** | `?only=idle` 回来的人 `openCount` 全为 0，且 `memberTotal ≤ memberTotalAll` |
| 4.5 | **趋势有 7 根柱子 + 口径** | `.team-flow__trend-col` 数 == 7，脚注含"活动日志" |
| 4.6 | **分页文案写清分母** | 底部文案含「共 N 人」，不是只显示"一页的人" |
| 5 | 点卡片能开抽屉，标题一致 | 抽屉标题 == 被点卡片的标题 |
| 6 | 抽屉不是空壳 | 至少 5 个字段格且有非空值（负责人 / 项目 / 看板列…） |
| 7 | Esc 能关掉抽屉 | 按 Esc 后抽屉不可见（BUG-110 的回归） |
| 8 | 普通成员没有徽标与切换 | 现场注册一个纯成员账号，两处都为 0 |
| 8.5 | **旧字段兜底的团队管理员也有入口** | zhangsan（`users.role=ROLE_MEMBER` + `team_members.role=ROLE_ADMIN`）徽标与切换都在，切全员后成员列 > 0、接口 `scope=TEAM` |
| 8.6 | **全员流里至少有一列带任务卡** | 有任务卡的成员列数 > 0（不静默跳过：没有就判失败并打印列数） |
| 8.8 | **管理员在别人负责的任务上不被按成员门槛拦**（BUG-115） | 按接口状态挑一张"进行中、非缺陷"的卡 → 抽屉「在项目里打开」→ 看板弹窗里「提交验收」可点，且页面上没有「只有任务负责人（X）可以提交验收」。只验不点：点了会写一条待验收记录 |
| 9 | 普通成员调接口被拒 | 响应体 `code === 403` 且说明里含"管理员" |
| 10–15 | 亮/暗两套主题下新界面 | 全员视图与抽屉**无低对比文本**（< 3:1）、页面**无横向溢出** |

三条经验写在用例注释里，别再踩：

- **判 `code` 不判 HTTP 状态**：本项目 `BusinessException` 走 HTTP 200 + 响应体 `code`，
  "越权被挡住"在 Network 面板里不是红色请求。第一版断言 `status === 403` 直接误判成失败。
- **`el-drawer` 关闭后 DOM 还在**（只是隐藏），所以判 `isVisible()`，`count() === 0` 永远不成立。
- **别假设"最后一个是什么"**：挑成员列要挑"真正带任务卡的列"，否则别的套件注册的空闲账号
  排在最后，用例会等一个永远不出现的卡片（BUG-118）。同理，代提交那条要按**工作项状态**挑卡——
  随便点第一张，可能那张本来就不该有「提交验收」按钮，会被误判成权限问题。
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
