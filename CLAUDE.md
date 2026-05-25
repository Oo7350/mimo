# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mimo is a lightweight Trello-like project management platform for small teams. It supports team collaboration, kanban task tracking, and progress visualization.

- **GitHub**: https://github.com/Oo7350/mimo
- **API docs**: `接口文档.md` (50+ endpoints, Chinese)
- **User manual**: `操作手册.md` (Chinese)

## Tech Stack

- **Frontend**: Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts + vuedraggable + driver.js + marked
- **Backend**: Spring Boot 2.7.18 (Java 17+) + MyBatis-Plus 3.5.3.1 + MySQL 8.0 + Redis + Spring Security + JWT (jjwt 0.11.5)
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
| `config/` | `SecurityConfig`, `JwtAuthenticationFilter`, `CorsConfig`, `MyBatisPlusConfig` |
| `controller/` | 12 REST controllers under `/api/*` |
| `service/` | 11 service classes with business logic |
| `mapper/` | MyBatis-Plus `BaseMapper` interfaces (no XML, all queries via `LambdaQueryWrapper`) |
| `entity/` | 15 entity classes mapped to database tables |
| `dto/` | Request/response DTOs per domain |

### Frontend Layout

| Directory | Purpose |
|-----------|---------|
| `api/` | `request.ts` (Axios instance + interceptors) + per-domain API modules |
| `store/` | Pinia stores — `user.ts` (auth/role), `app.ts` (sidebar, current project) |
| `router/` | Vue Router with navigation guard for auth + redirect |
| `views/` | 10 page components (Dashboard, ProjectOverview, ProjectBoard, ReportList, etc.) |
| `components/layout/` | `MainLayout.vue` (header, sidebar, content slot), `BreadcrumbNav.vue` |
| `components/issue/` | `IssueDetailDialog.vue` (3-tab modal: basic/detail+attachments/comments), `IssueCard.vue` |
| `components/common/` | `StatCard.vue`, `SkeletonLoader.vue`, `AssigneeAvatar.vue` |
| `composables/` | `useTour.ts` (driver.js onboarding), `useNotifications.ts` (30s polling) |
| `types/` | TypeScript interfaces for all domain objects |
| `utils/` | `constants.ts` (label/color maps), `markdown.ts` (marked renderer) |

### API Pattern

- All responses wrapped in `Result<T>`: `{ code: 200, message: "success", data: ... }`
- Public endpoints: `/api/auth/login`, `/api/auth/register`, Swagger UI paths
- All other endpoints require `Authorization: Bearer <token>` header
- Axios request interceptor auto-attaches token from localStorage; response interceptor handles 401 (redirect to login), 403 (toast), 500 (toast)
- Knife4j/Swagger UI at `/swagger-ui.html` and `/doc.html`

### Database

15 MySQL tables (InnoDB, utf8mb4): `users`, `teams`, `team_members`, `projects`, `project_members`, `board_columns`, `issues`, `issue_labels`, `attachments`, `sprints`, `burndown_snapshots`, `reports`, `activity_logs`, `comments`, `notifications`.

- Schema at `backend/src/main/resources/db/init.sql`
- Seed data: 3 users (admin/zhangsan/lisi, password `123456`), 1 team, 3 team members
- Soft delete via MyBatis-Plus `@TableLogic` on `deleted` column (users, teams, projects, issues)

## Key Features

- **Kanban Board**: 3 fixed columns (TODO/IN_PROGRESS/DONE) with drag-and-drop, priority color bars, search and filter (assignee/priority/type)
- **Project Overview**: Status pie chart, member workload bar chart, upcoming due tasks, recent activity
- **Task Comments**: Markdown-enabled discussion thread per issue, Ctrl+Enter to send
- **Notifications**: Bell badge with 30s polling, ASSIGNED and STATUS_CHANGED types
- **Reports**: Daily/weekly report generation with auto-draft content; data dashboard with 3 ECharts charts (weekly trend, member distribution, sprint velocity)
- **Sprint** (backend only): Quick-start 2-week iteration with auto task assignment; complete-with-migration for undone tasks
- **Onboarding Tour**: driver.js guided walkthrough triggered on first board visit

## Known Issues

### `@PreAuthorize` role prefix bug

`TeamController` uses `@PreAuthorize("hasRole('ROLE_ADMIN')")`. Spring Security's `hasRole()` automatically prepends `ROLE_`, so this checks for `ROLE_ROLE_ADMIN` — which will never match. **Fix**: use `hasAuthority('ROLE_ADMIN')` instead.

### Redis unused

`spring-boot-starter-data-redis` is declared and Redis is in Docker Compose, but no service code uses Redis.

### Upload directory

`AttachmentService` reads upload path from `mimo.upload.dir` property (default `./uploads`), but this property is **not** set in `application.yml`.

### `.claude/settings.local.json`

This file defines allowed Bash commands for Claude Code in this repo — if you add new toolchains or scripts, update it so Claude Code can execute them.
