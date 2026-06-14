# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mimo is a lightweight Trello-like project management platform for small teams. It supports team collaboration, kanban task tracking, and progress visualization.

- **Version**: v2.7.2 — Approval Notification Feedback + Auto-Degrade Fallback + Full User Level Display (2026-06-14)
- **Previous**: v2.7.1 — Approval Permission Enforcement + Global Approval List (2026-06-13)
- **GitHub**: https://github.com/Oo7350/mimo
- **API docs**: `接口文档.md` (70+ endpoints, Chinese)
- **User manual**: `操作手册.md` (Chinese)

## Tech Stack

- **Frontend**: Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts + vuedraggable + driver.js + marked + SockJS + STOMP.js + html2pdf.js + DeepSeek API + Inter font
- **Backend**: Spring Boot 2.7.18 (Java 17+) + MyBatis-Plus 3.5.3.1 + MySQL 8.0 + Redis + Spring Security + JWT (jjwt 0.11.5) + Spring WebSocket + STOMP
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
| `config/` | `SecurityConfig`, `JwtAuthenticationFilter`, `CorsConfig`, `MyBatisPlusConfig`, `WebSocketConfig`, `WebSocketAuthInterceptor` |
| `controller/` | 15 REST controllers under `/api/*` (including ApprovalController, UserLevelController) |
| `service/` | 15 service classes including `WebSocketService`, `ChatService`, **`ApprovalService`**, **`UserLevelService`** |
| `mapper/` | MyBatis-Plus `BaseMapper` interfaces (no XML, all queries via `LambdaQueryWrapper`) |
| `entity/` | 18 entity classes mapped to database tables (including ApprovalRequest, UserLevel) |
| `dto/` | Request/response DTOs per domain |

### Frontend Layout

| Directory | Purpose |
|-----------|---------|
| `api/` | `request.ts` (Axios instance + interceptors) + per-domain API modules + `ai.ts` (DeepSeek API) |
| `store/` | Pinia stores — `user.ts` (auth/role), `app.ts` (sidebar, current project, **dark mode**) |
| `router/` | Vue Router with navigation guard for auth + redirect |
| `views/` | 11 page components (Dashboard, ProjectOverview, ProjectBoard, ReportList, **LevelManage**, etc.) |
| `components/layout/` | `MainLayout.vue`, `AuthLayout.vue` (split login/register), `BreadcrumbNav.vue` |
| `components/issue/` | `IssueDetailDialog.vue`, `IssueCard.vue` (hover quick actions), type-specific cards/dialogs |
| `components/common/` | `StatCard.vue`, `SkeletonLoader.vue`, `PageHeader.vue`, **`CommandPalette.vue`**, `AssigneeAvatar.vue`, **`UserBadge.vue`** |
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
- **AI Assistant**: DeepSeek-powered polish description (professionalize draft text) and priority recommendation (analyze urgency/importance), frontend-only integration
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
