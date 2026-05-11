# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mimo is a simplified Jira-like agile project management platform for small teams (Scrum). It supports project collaboration, task tracking, and progress visualization.

## Tech Stack

- **Frontend**: Vue 3 + Vite + Element Plus + Pinia + Vue Router
- **Backend**: Spring Boot 2.7.18 (Java 17) + MyBatis-Plus 3.5.3.1 + MySQL 8.0 + Redis + Spring Security + JWT (jjwt 0.11.5)
- **Deployment**: Docker Compose (Nginx + Spring Boot + MySQL + Redis)
- **Backend tests**: JUnit 5 + Mockito (dependencies declared, **no tests written yet**)
- **E2E tests**: Python 3.10+ + Pytest + Selenium + webdriver-manager (in `tests/e2e/`)
- **Build**: Maven (backend), npm (frontend)

## Commands

### Backend (from `backend/`)

```bash
# Build
mvn clean compile
mvn clean package -DskipTests

# Run (dev)
mvn spring-boot:run

# Run (jar)
java -jar target/mimo-backend-1.0.0-SNAPSHOT.jar

# Tests (none exist yet; when added:)
mvn test
mvn test -Dtest=ClassName
```

### Frontend (from `frontend/`)

```bash
npm install
npm run dev          # Vite dev server on :5173, proxies /api → :8080
npm run build        # Type-check (vue-tsc) + production build to dist/
npm run preview      # Preview production build locally
```

### Docker Compose (from root)

```bash
docker compose up -d                  # Start all services
docker compose up -d mysql redis     # Start infrastructure only (for local dev)
docker compose logs -f backend       # Follow backend logs
docker compose down                  # Stop all
```

### E2E Tests (from `tests/e2e/`)

```bash
pip install -r requirements.txt
pytest -v                          # Run all
pytest test_login.py -v            # Run single file
pytest -k "test_login_success"     # Run by name
```

E2E tests target `http://localhost:5173` (Vite dev server). Requires the backend and frontend dev server to be running, and MySQL seeded with init data.

## Architecture

Single backend + RBAC with two roles: `ROLE_ADMIN` (team/project admin) and `ROLE_MEMBER`.

### Backend Package Layout (`com.mimo`)

| Package | Purpose |
|---------|---------|
| `common/` | `Result<T>` response wrapper, `ResultCode` enum, `BusinessException`, `GlobalExceptionHandler` |
| `config/` | `SecurityConfig`, `JwtAuthenticationFilter`, `CorsConfig`, `MyBatisPlusConfig` |
| `controller/` | 10 REST controllers under `/api/*` |
| `service/` | 9 service classes with business logic |
| `mapper/` | MyBatis-Plus `BaseMapper` interfaces (no XML, all queries via `LambdaQueryWrapper`) |
| `entity/` | 13 entity classes mapped to database tables |
| `dto/` | Request/response DTOs per domain |
| `util/` | `JwtUtil` (token generation/validation with HS256) |

### Frontend Layout

| Directory | Purpose |
|-----------|---------|
| `api/` | `request.ts` (Axios instance + interceptors) + per-domain API modules |
| `store/` | Pinia stores — `user.ts` (auth/role), `app.ts` (sidebar, current project) |
| `router/` | Vue Router with navigation guard for auth + redirect |
| `views/` | 11 page components |
| `components/layout/` | `MainLayout.vue` (header, sidebar, content slot) |
| `components/issue/` | `IssueDetailDialog.vue` (create/edit issue modal) |

### API Pattern

- All responses wrapped in `Result<T>`: `{ code: 200, message: "success", data: ... }`
- Public endpoints: `/api/auth/login`, `/api/auth/register`, Swagger UI paths
- All other endpoints require `Authorization: Bearer <token>` header
- Axios request interceptor auto-attaches token from localStorage; response interceptor handles 401 (redirect to login), 403 (toast), 500 (toast)
- Knife4j/Swagger UI at `/swagger-ui.html` and `/doc.html`

### Database

13 MySQL tables (InnoDB, utf8mb4): `users`, `teams`, `team_members`, `projects`, `project_members`, `board_columns`, `issues`, `issue_labels`, `attachments`, `sprints`, `burndown_snapshots`, `reports`, `activity_logs`.

- Schema at `backend/src/main/resources/db/init.sql` — auto-executed on first MySQL container start
- Seed data: 3 users (admin/zhangsan/lisi, password `123456`), 1 team, 3 team members
- Soft delete via MyBatis-Plus `@TableLogic` on `deleted` column (users, teams, projects, issues)

### Authentication Flow

1. `POST /api/auth/login` → BCrypt password check → returns JWT with claims `userId`, `username`, `role`
2. `JwtAuthenticationFilter` extracts token from `Authorization` header, validates, sets `SecurityContextHolder`
3. Frontend stores token in `localStorage`, auto-attached by Axios interceptor
4. Role stored as `ROLE_ADMIN` or `ROLE_MEMBER` in JWT claim; `SecurityContext` grants authority as-is

## Permission Model

- Write operations (create team, manage members) require `ROLE_ADMIN`
- Board operations and task claiming are open to all project members
- Task modifications limited to the assignee or admin
- Same-project members can see all project tasks and boards
- Spring Security `@PreAuthorize` on `TeamController.inviteMember` and `TeamController.removeMember`

## Known Issues

### `@PreAuthorize` role prefix bug

`TeamController` uses `@PreAuthorize("hasRole('ROLE_ADMIN')")`. Spring Security's `hasRole()` automatically prepends `ROLE_`, so this checks for `ROLE_ROLE_ADMIN` — which will never match. The JWT role claim stores the full string `ROLE_ADMIN`, and `JwtAuthenticationFilter` passes it directly to `SimpleGrantedAuthority`. **Fix**: use `hasAuthority('ROLE_ADMIN')` instead, or strip the `ROLE_` prefix when building authorities.

### Redis unused

`spring-boot-starter-data-redis` is declared and Redis is in Docker Compose, but no service code uses Redis (no caching, no token blacklist).

### Upload directory

`AttachmentService` reads upload path from `mimo.upload.dir` property (default `./uploads`), but this property is **not** set in `application.yml`.

### Frontend RBAC incomplete

`UserStore` exposes `isAdmin` computed property but no views or components currently use it to hide admin-only UI elements. Role enforcement is backend-only (HTTP 403 responses).

### No backend unit tests

The `src/test` directory does not exist. JUnit 5 and Mockito dependencies are declared in `pom.xml` but no tests have been written.

### `.claude/settings.local.json`

This file defines allowed Bash commands for Claude Code in this repo — if you add new toolchains or scripts, update it so Claude Code can execute them.
