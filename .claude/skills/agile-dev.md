# 敏捷项目管理与协作平台 (Mini Jira)

你是一个全栈开发专家，正在构建一个面向小型团队的敏捷项目管理与协作平台。系统定位为简化版 Jira，核心目标是支持 Scrum 团队的项目协作、任务跟踪和进度可视化。

## 技术栈
- 前端：Vue 3 + Vite + Element Plus + Pinia (状态管理) + Vue Router
- 后端：Spring Boot 2.7+ (Java 17) + MyBatis-Plus + MySQL 8.0 + Redis + Spring Security + JWT
- 部署：Docker Compose (前端 Nginx + 后端 + MySQL + Redis)
- 测试：
  - 后端单元测试：JUnit 5 + Mockito
  - 端到端 UI 测试：Python 3.10+ + Pytest + Selenium + webdriver-manager
- 工具：Maven 管理依赖，Swagger/Knife4j 生成接口文档

## 核心功能模块
1. 用户认证与授权：注册、登录、JWT 认证，角色分为团队管理员和普通成员
2. 团队管理：创建团队、邀请成员、设置角色、查看团队列表
3. 项目管理：创建项目、选择模板（Scrum/Kanban），支持多个项目并存
4. 看板 (Kanban)：自定义列（如待办、进行中、已完成），拖拽任务改变状态，任务卡片展示
5. 任务管理 (Issue)：创建任务，设置类型（故事/任务/缺陷）、优先级、指派人、截止日期、描述、附件
6. 燃尽图 (Burndown Chart)：关联 Sprint，自动统计剩余工作量生成图表
7. Sprint 管理：创建 Sprint，绑定任务，查看 Sprint 进度
8. 日报/周报：自动汇总成员提交的任务记录，支持手动编辑并提交
9. 缺陷跟踪：专属缺陷任务类型，突出显示，关联复现步骤和严重程度

## 权限控制说明
系统采用单一后端服务 + RBAC 权限模型。角色分为 `ROLE_ADMIN`（团队管理员/项目管理员）和 `ROLE_MEMBER`（普通成员）。
- 创建团队、管理成员等写操作仅管理员可执行；
- 看板操作、任务领取等所有成员均可操作，但对任务的修改限于本人名下或管理员；
- 数据可见性遵循项目内共享原则：同项目成员可看到项目所有任务和看板。

后端使用 Spring Security + 方法级鉴权实现，前端根据用户角色动态控制菜单和按钮显示。

## 数据库核心表设计（请直接生成完整 DDL）
- 用户表 (users)
- 团队表 (teams)
- 团队-用户关联表 (team_members)
- 项目表 (projects)
- 项目成员表 (project_members)
- 看板列表 (board_columns)
- 任务表 (issues)
- 任务标签表 (issue_labels)
- 附件表 (attachments)
- Sprint 表 (sprints)
- 日报/周报表 (reports)
- 操作日志表 (activity_logs)

请按以上描述生成 MySQL DDL，注意外键约束、索引和字段注释，所有表使用 InnoDB，字符集 utf8mb4。

## 开发流程与提示词模板

### 第一步：环境搭建
> 请提供一份 Docker Compose 文件，包含 MySQL 8.0、Redis 7 以及后端 Spring Boot 服务（使用 Dockerfile 构建）和前端 Nginx 部署。

### 第二步：后端基础结构
> 基于 Spring Boot 初始化项目结构，包含统一的响应类 Result、异常处理 GlobalExceptionHandler、JWT 工具类、Spring Security 配置。请给出核心代码和 Maven 依赖。

### 第三步：用户模块
> 实现用户注册和登录接口，密码 BCrypt 加密，登录成功返回 JWT。给出完整的 Controller、Service、Mapper 和 Entity 代码。

### 第四步：团队与项目管理
> 创建团队、添加成员、创建项目的 RESTful API。要求使用 MyBatis-Plus，提供对应的实体类和 DTO。

### 第五步：看板与任务
1. **看板列管理**：根据项目 ID 获取所有列，支持增删改排序列位置。
2. **任务 CRUD**：任务包含标题、描述、类型、优先级、指派人、截止日期、所属看板列、排序序号等字段。
3. **拖拽更新**：提供任务移动（改变列和排序）的接口，使用排序序号实现位置交换，前端拖拽结束后调用。
请给出完整的后端代码，包括 Controller、Service 及数据库更新的逻辑。

### 第六步：燃尽图数据接口
> 请设计一个接口，根据 Sprint ID 返回燃尽图数据：Sprint 开始日期、结束日期、每日理想剩余工作量、每日实际剩余工作量。数据从 issues 表的状态变更记录或每日快照中统计。如果缺少记录表，请先给出表结构和数据采集方法。

### 第七步：日报周报
> 自动汇总某用户在指定日期范围内的任务完成情况，生成日报草稿，支持用户编辑后保存。给出前后端交互接口和数据库设计。

### 第八步：前端核心页面生成
- **登录注册页**：使用 Element Plus 表单，调用后端接口。
- **项目看板页**：顶部项目切换，下方为可拖拽的列和卡片。使用 vuedraggable 实现拖拽，调用更新接口。
- **任务详情 Dialog**：编辑标题、描述、指派人、优先级等，支持评论列表。
- **燃尽图页**：选择 Sprint，使用 ECharts 渲染折线图。
请依次给出 Vue 3 组件的代码模板，使用 `<script setup>` 语法，并包含必要的 API 调用封装。

### 第九步：端到端 UI 自动化测试 (Python + Pytest + Selenium)

使用 Python 编写 UI 自动化测试，覆盖核心业务流程。测试环境与项目主代码分离，目录结构建议设为 `tests/e2e/`。

**环境要求**
- Python 3.10+
- 依赖：selenium, pytest, pytest-html, webdriver-manager (自动管理浏览器驱动)

**目录结构**