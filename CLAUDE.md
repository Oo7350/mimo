# CLAUDE.md

> **最新版本**：v2.13.6（2026-08-21）— 邮件通知收尾（发送日志 + 模板化） + 第三方 Webhook 集成 + 数据导入导出 + 操作审计日志。详见末尾"版本变更记录"。

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mimo is a lightweight Trello-like project management platform for small teams. It supports team collaboration, kanban task tracking, and progress visualization.

- **Version**: v2.10.0 — Configurable Workflow Engine + Security Hardening + ESLint/Prettier (2026-06-24)
- **Previous**: v2.9.3 — Gantt Chart + Dependency Visualization (2026-06-18)
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
| `entity/` | 18 entity classes mapped to database tables (including ApprovalRequest, UserLevel) |
| `dto/` | Request/response DTOs per domain |

### Frontend Layout

| Directory | Purpose |
|-----------|---------|
| `api/` | `request.ts` (Axios instance + interceptors) + per-domain API modules + **`ai.ts`** (DeepSeek API: chatStream/polish/priority/parse-issue/suggest-tasks/estimate-story-points) |
| `store/` | Pinia stores — `user.ts` (auth/role), `app.ts` (sidebar, current project, **dark mode**) |
| `router/` | Vue Router with navigation guard for auth + redirect |
| `views/` | 11 page components (Dashboard, ProjectOverview, ProjectBoard, ReportList, **LevelManage**, etc.) |
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

18 MySQL tables (InnoDB, utf8mb4): `users`, `teams`, `team_members`, `projects`, `project_members`, `board_columns`, `issues`, `issue_labels`, `attachments`, `sprints`, `burndown_snapshots`, `reports`, `activity_logs`, `comments`, `notifications`, `chat_messages`, **`approval_requests`**, **`user_levels`**.

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
- **AI Assistant (v2.8.0)**: **DeepSeek-powered AI Copilot** — SSE流式对话(`GET /api/ai/chat/stream`)、智能任务创建(自然语言→结构化任务)、Story拆分子任务、润色描述、优先级推荐、故事点估算；全局悬浮窗(AiChatPanel)集成于MainLayout；API Key由后端管理(DEEPSEEK_API_KEY环境变量)
- **Onboarding Tour**: driver.js guided walkthrough triggered on first board visit
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

### Frontend deep links

| URL query | Behavior |
|-----------|----------|
| `?issue={id}` | Open issue detail dialog on board (used by notifications, dashboard todos, command palette) |
| `?create=1` | Open create-issue dialog on board (used by command palette) |

## Known Issues

### `@PreAuthorize` role prefix bug

`TeamController` uses `@PreAuthorize("hasRole('ROLE_ADMIN')")`. Spring Security's `hasRole()` automatically prepends `ROLE_`, so this checks for `ROLE_ROLE_ADMIN` — which will never match. **Fix**: use `hasAuthority('ROLE_ADMIN')` instead.

### Redis unused

`spring-boot-starter-data-redis` is declared and Redis is in Docker Compose, but no service code uses Redis. WebSocket uses in-memory broker (not Redis-backed) — for multi-instance deployment, switch to Redis message broker.

### Upload directory

`AttachmentService` reads upload path from `mimo.upload.dir` property (default `./uploads`), but this property is **not** set in `application.yml`.

### `.claude/settings.local.json`

This file defines allowed Bash commands for Claude Code in this repo — if you add new toolchains or scripts, update it so Claude Code can execute them.

### Pending AI Features (Phase 0 remaining)

The following AI enhancements are designed but **not yet implemented**. See `项目功能完善计划.md` §2.2-2.3 for full specs.

**AI Sprint Planning Assistant** (§2.2):
- `POST /api/ai/suggest-sprint-plan` — analyze Backlog priorities + team capacity + dependencies → recommend task list with rationale & risk alerts
- `POST /api/ai/analyze-sprint-health` — current progress vs remaining time vs remaining workload → adjustment suggestions (reduce scope / extend / add people)
- `POST /api/ai/predict-velocity` — historical Sprint completion trend + prediction value
- Frontend: `SprintPlanningView.vue` (wizard) + `AiSprintAdvisor.vue` (suggestion panel)

**AI Report Generation Enhancement** (§2.3):
- `POST /api/ai/generate-daily-report` — auto-generate from today's activity (completed tasks, work logs, comments, blockers)
- `POST /api/ai/generate-weekly-report` — weekly summary + next week plan suggestions
- `POST /api/ai/generate-retrospective` — Sprint retrospective summary (AI distillation)
- Data sources to integrate: issues, time_logs, comments, Sprint goals
- Frontend: integrate into existing ReportView with "AI 生成" button

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
   - `GET /api/ai/chat/stream` (SSE) — 流式对话，支持systemPrompt自定义
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
- Docs: All three docs synchronized (接口文档.md, 操作手册.md, CLAUDE.md)

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
- 现状：完全无文档协作能力
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
