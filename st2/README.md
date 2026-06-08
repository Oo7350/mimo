# st2 —— Playwright PO 模式自动化测试

基于 **Playwright + Pytest + PO 模式 + Allure** 的 Mimo 项目自动化测试框架。覆盖 Web UI 自动化 + 后端 API 集成测试。

## 与 st (Selenium) 的区别

| 维度 | st (Selenium) | st2 (Playwright) |
|------|---------------|-------------------|
| 驱动管理 | 手动 chromedriver / msedgedriver | `playwright install` 自动 |
| 元素定位 | `find_element(By.XPATH, ...)` | `page.locator("css")` 统一 |
| 等待机制 | 显式/隐式/强制三种等待 | 自动等待，无需手写 |
| 浏览器隔离 | 需手动管理窗口句柄 | Browser Context 天然隔离 |
| 并发 | Selenium Grid 或 pytest-xdist | Context 原生支持并行 |
| 调试 | 截图 | Trace Viewer 时间轴回放 |

## 安装

```bash
pip install -r requirements.txt
playwright install chromium
```

## 运行

```bash
# 运行所有测试
pytest

# 运行指定文件
pytest scripts/test_01_login.py -v

# 运行指定用例
pytest -k "test_login_success" -v

# 并发执行（4线程）
pytest -n 4

# ====== API 测试 ======
# 运行所有 API 测试
pytest scripts/test_api_*.py -v

# 运行单个 API 模块测试
pytest scripts/test_api_01_auth.py -v

# 运行指定 API 用例
pytest -k "test_login_parametrized" -v

# 运行类型差异化相关测试
pytest scripts/test_api_07_issue.py -k "story or bug" -v
```

## API 测试

基于 `requests` + `pytest` + Allure 的后端 API 集成测试，覆盖 11 模块、90+ 用例（含 STORY/TASK/BUG 三种类型全覆盖）。

| 文件 | 模块 | 覆盖内容 |
|------|------|---------|
| `test_api_01_auth.py` | 认证 | 登录参数化(5)、注册参数化(6)、token 验证 |
| `test_api_02_user.py` | 用户 | 个人信息、修改密码参数化(4) |
| `test_api_03_dashboard.py` | 仪表盘 | 仪表盘数据 |
| `test_api_04_team.py` | 团队 | 创建(2)、列表、详情、成员管理、权限验证 |
| `test_api_05_project.py` | 项目 | 创建(2)、列表、详情、团队项目、添加成员 |
| `test_api_06_board.py` | 看板 | 获取看板、列 CRUD(2)、排序、拖动任务 |
| `test_api_07_issue.py` | **工作项(核心)** | TASK创建(3)、STORY创建(2)、BUG创建(2)、BUG状态流转(12)、验收标准CRUD(2+生命周期)、查询增强(epic/bugStatus筛选)、详情验证(STORY/BUG专属字段)、更新(STORY/BUG字段)、父子关系、删除 |
| `test_api_08_sprint.py` | Sprint | 创建(2)、列表、详情、生命周期(规划→活跃→完成) |
| `test_api_09_burndown.py` | 燃尽图 | 获取数据、创建快照 |
| `test_api_10_report.py` | 报告 | 生成(2)、列表、详情、更新、提交 |
| `test_api_11_attachment.py` | 附件 | 上传、列表、下载、删除生命周期 |

### 工作项测试覆盖详情 (test_api_07_issue.py)

| 测试用例 | 数量 | 说明 |
|---------|------|------|
| `test_create_task_parametrized` | 3 | TASK 创建（正常/空标题/BUG基础字段） |
| `test_create_story_parametrized` | 2 | STORY 创建（完整三要素+史诗 / 最简） |
| `test_create_bug_parametrized` | 2 | BUG 创建（完整缺陷报告+环境 / 轻微） |
| `test_bug_status_transition` | 12 | BUG 状态流转（9合法 + 3非法） |
| `test_add_acceptance_criteria_parametrized` | 2 | 验收标准添加（有效/空文本） |
| `test_acceptance_criteria_full_lifecycle` | 1 | AC 完整生命周期（增→勾选→改→删） |
| `test_acceptance_criteria_bug_rejected` | 1 | BUG类型不能添加AC |
| `test_bug_status_change_on_task_rejected` | 1 | TASK类型不能变更bug_status |
| `test_query_by_bug_status` | 1 | 按 bugStatus 筛选 |
| `test_query_by_epic` | 1 | 按 epic 筛选 |
| `test_get_story_detail_with_ac` | 1 | STORY 详情含 AC/subTasks |
| `test_get_bug_detail_with_report` | 1 | BUG 详情含环境/期望/实际结果 |
| `test_update_bug_fields` | 1 | 更新 BUG 专属字段 |
| `test_update_story_fields` | 1 | 更新 STORY 专属字段 |
| `test_task_parent_story` | 1 | TASK 挂载到 STORY 父子关系 |

## UI 测试

基于 Playwright + Page Object 模式的前端 UI 自动化测试。

| 文件 | 模块 | 覆盖内容 |
|------|------|---------|
| `test_01_login.py` | 登录 | 参数化登录(5)、成功、失败 |
| `test_02_register.py` | 注册 | 注册成功、重复用户名 |
| `test_03_board.py` | **看板(核心)** | 看板访问、三视图切换(3)、创建STORY/TASK/BUG(3)、类型筛选(2)、缺陷列表交互、故事地图交互 |

### 看板测试覆盖详情 (test_03_board.py)

| 测试用例 | 说明 |
|---------|------|
| `test_access_board_after_login` | 登录后看板视图可见 |
| `test_switch_to_buglist_view` | 切换到缺陷列表视图 |
| `test_switch_to_storymap_view` | 切换到故事地图视图 |
| `test_view_switching_roundtrip` | 三视图来回切换不报错 |
| `test_create_story` | 创建 STORY（含三要素+史诗） |
| `test_create_task` | 创建 TASK |
| `test_create_bug` | 创建 BUG（含严重程度） |
| `test_type_filter_story` | 类型筛选——仅故事 |
| `test_type_filter_bug` | 类型筛选——仅缺陷 |
| `test_buglist_view_with_bugs` | 缺陷列表查看+点击行打开详情 |
| `test_storymap_view_with_stories` | 故事地图查看+点击展开子任务 |

### API 测试架构

```
api/                          # API 客户端层
├── mimo_api_client.py        # 核心客户端（requests.Session + JWT）
├── __init__.py               # MimoAPI 门面（统一入口）
├── auth_api.py               # 认证模块
├── user_api.py               # 用户模块
├── issue_api.py              # 工作项模块（含验收标准 + BUG状态端点）
└── ...                       # 其他模块

data/                         # 测试数据（参数化 JSON）
├── login.json                # 登录场景(5)
├── register.json             # 注册场景(6)
├── create_story.json         # 创建故事(2)
├── create_bug.json           # 创建缺陷(2)
├── bug_status.json           # BUG状态流转(12——9合法+3非法)
├── acceptance_criteria.json  # 验收标准场景(2)
└── ...                       # 更多 JSON 数据文件

pages/                        # Page Object 页面对象
├── page_login.py             # 登录页
├── page_register.py          # 注册页
├── page_dashboard.py         # 工作台
└── page_board.py             # 看板页（含视图切换/类型筛选/卡片定位）

scripts/                      # 测试脚本
├── test_01_login.py          # 登录UI测试
├── test_02_register.py       # 注册UI测试
├── test_03_board.py          # 看板UI测试（11用例）
├── test_api_01_auth.py       # 认证API测试
└── ...                       # 更多API测试
```

### Fixtures

| Fixture | 说明 |
|---------|------|
| `browser` | Chromium 浏览器实例（function scope） |
| `page` | 独立 BrowserContext + Page（隔离 Cookie） |
| `logged_in_page` | 支持 storage_state 的登录态复用 |
| `api_client` | 未登录 API 客户端（公开接口/401测试） |
| `api_client_admin` | 已登录管理员（admin/123456） |
| `api_client_member` | 已登录成员（zhangsan/123456） |
| `api_client_member2` | 已登录成员（lisi/123456） |

## 前提条件

运行测试前需确保：
1. MySQL 数据库运行中（`localhost:3306`, root/336699, db: mimo）
2. Redis 运行中（`localhost:6379`）
3. 后端 Spring Boot 运行中（`localhost:8080`, `mvn spring-boot:run`）
4. 前端 Vite 运行中（`localhost:5173`, `npm run dev`，仅 UI 测试需要）
