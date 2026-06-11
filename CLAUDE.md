# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mimo is a lightweight Trello-like project management platform for small teams. It supports team collaboration, kanban task tracking, and progress visualization.

- **Version**: v2.4.0 — Emoji, Enhanced Reports, Audit Log (2026-06-10)
- **Previous**: v2.1.0 — UI Enhancement & Command Palette (2026-06-10)
- **GitHub**: https://github.com/Oo7350/mimo
- **API docs**: `接口文档.md` (60+ endpoints, Chinese)
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
| `controller/` | 13 REST controllers under `/api/*` |
| `service/` | 13 service classes including `WebSocketService` for real-time push and `ChatService` for team chat |
| `mapper/` | MyBatis-Plus `BaseMapper` interfaces (no XML, all queries via `LambdaQueryWrapper`) |
| `entity/` | 16 entity classes mapped to database tables |
| `dto/` | Request/response DTOs per domain |

### Frontend Layout

| Directory | Purpose |
|-----------|---------|
| `api/` | `request.ts` (Axios instance + interceptors) + per-domain API modules + `ai.ts` (DeepSeek API) |
| `store/` | Pinia stores — `user.ts` (auth/role), `app.ts` (sidebar, current project, **dark mode**) |
| `router/` | Vue Router with navigation guard for auth + redirect |
| `views/` | 10 page components (Dashboard, ProjectOverview, ProjectBoard, ReportList, etc.) |
| `components/layout/` | `MainLayout.vue`, `AuthLayout.vue` (split login/register), `BreadcrumbNav.vue` |
| `components/issue/` | `IssueDetailDialog.vue`, `IssueCard.vue` (hover quick actions), type-specific cards/dialogs |
| `components/common/` | `StatCard.vue`, `SkeletonLoader.vue`, `PageHeader.vue`, **`CommandPalette.vue`**, `AssigneeAvatar.vue` |
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

16 MySQL tables (InnoDB, utf8mb4): `users`, `teams`, `team_members`, `projects`, `project_members`, `board_columns`, `issues`, `issue_labels`, `attachments`, `sprints`, `burndown_snapshots`, `reports`, `activity_logs`, `comments`, `notifications`, `chat_messages`.

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
