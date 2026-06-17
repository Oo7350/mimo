# Mimo Bug Log

记录 Mimo 项目自开发以来遇到并修复的所有 Bug，按版本倒序排列。

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

### KNOWN-001: @PreAuthorize 角色前缀 Bug [Low]

- **文件**: `backend/.../TeamController.java`
- **现象**: `@PreAuthorize("hasRole('ROLE_ADMIN')")` 实际检查的是 `ROLE_ROLE_ADMIN`，永远不会匹配
- **原因**: Spring Security 的 `hasRole()` 自动添加 `ROLE_` 前缀
- **建议修复**: 改用 `hasAuthority('ROLE_ADMIN')`
- **当前状态**: 未修复（该注解目前未被实际依赖，权限校验在 Service 层手动实现）

### KNOWN-002: Redis 未实际使用 [Low]

- **文件**: `pom.xml`, `application.yml`
- **现象**: `spring-boot-starter-data-redis` 已引入，Redis 配置已写入，但仅用于单设备登录 Token 校验，WebSocket 仍使用内存代理
- **影响**: 多实例部署时 WebSocket 消息无法跨实例同步
- **建议**: 切换到 Redis-backed WebSocket message broker

### KNOWN-003: 上传目录配置缺失 [Low]

- **文件**: `backend/.../AttachmentService.java`
- **现象**: `mimo.upload.dir` 属性未在 `application.yml` 中设置，使用默认值 `./uploads`
- **影响**: 部署时附件保存路径不可控

### KNOWN-004: Issue Key 生成存在并发冲突风险 [Medium]

- **文件**: `backend/.../IssueService.java`
- **现象**: 多个用户同时创建任务时，`issueKey` 可能重复
- **根因**: `count + 1` 方式非原子操作，高并发下可能生成相同 Key
- **建议修复**: 使用数据库序列或 `SELECT ... FOR UPDATE` 悲观锁
- **当前状态**: 未修复（依赖 `uk_issue_key` 唯一约束兜底，但冲突时用户收到 500）

### KNOWN-005: 删除团队后项目数据成为孤儿 [Medium]

- **文件**: `backend/.../TeamService.java`
- **现象**: 删除团队后，该团队下的项目及其 issues/board/sprints 不受影响但无归属团队
- **根因**: `deleteTeam()` 仅删除 `team_members` 和 `teams`，项目表标注"独立存在"
- **建议修复**: 要么级联删除项目，要么提供"无团队项目"的管理视图

### KNOWN-006: 审批通过后执行失败无补偿机制 [Medium]

- **文件**: `backend/.../ApprovalService.java`
- **现象**: 审批通过后 `executeApprovedAction()` 若执行失败（如项目创建失败），审批状态已变更为 APPROVED，但操作未实际生效
- **根因**: 审批和执行在同一个事务中，但异常被 catch 后没有回滚
- **代码注释明确标注**："实际生产环境应该有补偿机制或重试队列"
- **当前状态**: 未修复

### KNOWN-007: 数据库无外键约束 [Low]

- **文件**: `init.sql`
- **现象**: 所有表之间通过应用层代码维护关系，无 DB 级外键
- **影响**: 可能出现孤立的 `comments`、`attachments` 等记录
- **当前状态**: 设计选择，非必须修复

### KNOWN-008: Sprint 快速创建限制前 5 个任务硬编码 [Low]

- **文件**: `backend/.../SprintService.java`
- **现象**: `createQuickSprint()` 中 `if (added >= 5) break` 硬编码了 5 个任务的限制
- **建议修复**: 改为可配置参数

### KNOWN-009: 团队/项目/Sprint/评论均无更新端点 [Medium]

- **文件**: `TeamController`, `ProjectController`, `SprintController`, `CommentController`
- **现象**: 创建后可删除但无法编辑，缺少 PUT 更新端点
- **影响**: 用户无法修改团队名/描述、项目名/Key、Sprint 名称/日期、评论内容

### KNOWN-010: 默认看板列名硬编码中文 [Low]

- **文件**: `backend/.../ProjectService.java`
- **现象**: 默认列名"待办/进行中/已完成"写死在代码中
- **影响**: 非中文部署时列名不便

### KNOWN-011: 聊天/评论使用 v-html 无 XSS 防护 [Medium]

- **文件**: `TeamDetail.vue`（@提及渲染）、`IssueDetailDialog.vue`（Markdown 渲染）
- **现象**: 用户输入的聊天内容和评论经 `renderMarkdown()`/`renderMention()` 后直接通过 `v-html` 渲染
- **风险**: 若服务端未做 sanitize，恶意 `<script>` 标签可被执行
- **建议修复**: 后端存储前 strip 危险 HTML 标签，或前端使用 DOMPurify

### KNOWN-012: 离开团队使用 window.location.href 触发全页刷新 [Low]

- **文件**: `frontend/src/views/TeamDetail.vue`
- **现象**: 移除成员自己离开团队后，使用 `window.location.href = '/teams'` 而非 Vue Router
- **影响**: 全页刷新丢失 Vue 状态

### KNOWN-013: 在线人数始终显示总成员数 [Low]

- **文件**: `frontend/src/views/TeamDetail.vue`
- **根因**: `onlineCount = computed(() => members.value.length)` 取的是成员总数
- **建议修复**: 通过 WebSocket 统计真实在线连接数

### KNOWN-014: 工作台快捷操作 5 个按钮 4 个指向同一路由 [Low]

- **文件**: `frontend/src/views/Dashboard.vue`
- **现象**: "新建任务"/"看板视图"/"Sprint"/"报告" 都跳到 `/projects`
- **建议修复**: 分别路由到看板创建弹窗、Sprint 管理、报告页

### KNOWN-015: 审批"拒绝原因"输入框取消时行为不一致 [Low]

- **文件**: `frontend/src/views/ApprovalList.vue`
- **现象**: `ElMessageBox.prompt` 的 cancel 回调中，Element Plus 返回 `'cancel'` 或 `'close'`，但只检查了 `'cancel'`
- **影响**: 某些情况下用户点 X 关闭后仍会执行 reject 请求

### KNOWN-016: 部署脚本 deploy.ps1 无确认步骤 [Low]

- **文件**: `deploy.ps1`
- **现象**: 执行即覆盖服务器生产环境，无确认提示
- **建议修复**: 加 `Read-Host "确认部署? (y/n)"` 确认步骤

---

## Bug 统计

| 严重程度 | 数量 |
|---------|------|
| Critical | 5 |
| High | 14 |
| Medium | 10 |
| Low | 7 |
| **已知未修复** | **13** |
| **总计** | **49** |

| 模块 | Bug 数量 |
|------|---------|
| 审批系统 | 7 |
| 权限控制 | 6 |
| 看板/拖拽 | 5 |
| 项目管理 | 4 |
| 通知系统 | 3 |
| 用户/认证 | 3 |
| 聊天系统 | 2 |
| 报表/Sprint | 3 |
| UI/样式 | 3 |
| 已知未修复 | 13 |
| **总计** | **49** |
