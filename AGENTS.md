# AGENTS.md

> **最新版本**：v2.19.4（2026-11-24）— 验收按角色分工。补上多角色验收矩阵 e2e（`e2e/review-matrix.mjs`，12 条），写着写着抓到一个真问题：验收面板的「通过 / 打回」以前对**所有人**显示，提交人自己、完全无关的普通成员都看得到，点了才被后端 403 拒——已按后端同一口径改成"该看到的看到、不该看到的被告知在等谁"（BUG-102）。KNOWN-017（核心链路缺 e2e）随之关闭，另登记 KNOWN-024（e2e 未接 CI）。核心链路口径：5 套 38 条断言。
>
> **文档版本号以本文件 + `bug-log.md` 为准**：同一批工作流自动生成改动，另一台机器的提交写 v2.17.1、`CLAUDE.md` 写 v2.18.1，合并后实际是 v2.19.3（`CLAUDE.md` 头部已同步）。

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Mimo is a lightweight Trello-like project management platform for small teams. It supports team collaboration, kanban task tracking, and progress visualization.

- **Version**: v2.19.4 — 验收按角色分工：界面别撒谎 + 多角色矩阵 e2e (2026-11-24)
- **Previous**: v2.19.3 — 合并另一台机器的工作流自动生成 (2026-11-24)
- **GitHub**: https://github.com/Oo7350/mimo
- **API docs**: `接口文档.md` (70+ endpoints, Chinese)
- **User manual**: `操作手册.md` (Chinese)

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

### Frontend (from `frontend/`)

```bash
npm install
npm run dev          # Vite dev server on :5173, proxies /api → :8080
npm run build        # Type-check (vue-tsc) + production build to dist/
```

### 端到端用例（E2E）

仓库 `e2e/` 下是核心链路的真实浏览器用例（Playwright，装在仓库外，不污染 package.json）：

```bash
PLAYWRIGHT_BROWSERS_PATH=<浏览器目录> \
PLAYWRIGHT_MODULE=<playwright 包目录> \
node e2e/core-flows.mjs http://localhost:5173
```

四套用例：

| 脚本 | 覆盖 | 最近结果 |
|---|---|---|
| `e2e/core-flows.mjs` | 创建任务 / 拖拽进进行中（含自动计时）/ 评论 / 附件 / 验收 / 新建 Sprint | 6/6 PASS |
| `e2e/ai-degrade.mjs` | AI 未配置提示条 / 对话友好说明 / 润色失败不改写正文 | 3/3 PASS |
| `e2e/authz.mjs` | 外人打 23 个读接口全部 403 / 成员访问正常 / 缺参数 400 | 25/25 PASS |
| `e2e/tour.mjs` | 引导自动触发 / 跳过清理 / 入口常驻与重播 / 错页面提示 | 9/9 PASS |
| `e2e/xss-markdown.mjs` | Markdown 与邮件正文的 XSS 载荷 10 条（真实浏览器 + 哨兵变量；**不需要起服务**） | 10/10 PASS |

跑法与三个必踩的坑（driver.js 的 `driver-active` 会锁死全站点击、Element Plus 页签要 `dispatchEvent`、
vuedraggable 不吃 `locator.dragTo`）见 `e2e/README.md`。

### Database

New tables must be created manually since Docker is not available:

```bash
mysql -u root -p"336699" mimo < backend/src/main/resources/db/init.sql
```

Or create individual tables:
```sql
CREATE TABLE IF NOT EXISTS comments (...);
CREATE TABLE IF NOT EXISTS notifications (...);
```

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

- Schema at `backend/src/main/resources/db/init.sql`
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
- **Broker**: Simple in-memory broker (`/topic` for broadcast, `/queue` for user-specific)
- **Topics**:
  - `/topic/notifications/{userId}` — personal notification push
  - `/topic/board/{projectId}` — board sync events (ISSUE_MOVED/CREATED/DELETED)
  - `/topic/team-chat/{teamId}` — team group chat messages (CHAT_MESSAGE event)
- **Board Sync Flow**: Drag → `PUT /api/board/issue/move` → backend updates issue + logs + WebSocket broadcast → all board subscribers receive event → optimistic UI update

## Version History

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
