# st2 —— Playwright PO 模式自动化测试

基于 **Playwright + Pytest + PO 模式 + Allure** 的 Mimo 项目自动化测试框架。

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
```

## API 测试

基于 `requests` + `pytest` + Allure 的后端 API 集成测试，覆盖 10+ 模块、70+ 用例。

| 文件 | 模块 | 覆盖内容 |
|------|------|---------|
| `test_api_01_auth.py` | 认证 | 登录参数化(5)、注册参数化(6)、token 验证 |
| `test_api_02_user.py` | 用户 | 个人信息、修改密码(4) |
| `test_api_03_dashboard.py` | 仪表盘 | 仪表盘数据 |
| `test_api_04_team.py` | 团队 | 创建(2)、列表、详情、成员管理、权限验证 |
| `test_api_05_project.py` | 项目 | 创建(2)、列表、详情、团队项目、添加成员 |
| `test_api_06_board.py` | 看板 | 获取看板、列 CRUD(2)、排序、拖动任务 |
| `test_api_07_issue.py` | 任务 | 创建(3)、查询(3)、详情、更新、删除、Bug |
| `test_api_08_sprint.py` | Sprint | 创建(2)、列表、详情、生命周期(规划→活跃→完成) |
| `test_api_09_burndown.py` | 燃尽图 | 获取数据、创建快照 |
| `test_api_10_report.py` | 报告 | 生成(2)、列表、详情、更新、提交 |
| `test_api_11_attachment.py` | 附件 | 上传、列表、下载、删除生命周期 |

### API 测试架构

```
api/                          # API 客户端层
├── mimo_api_client.py        # 核心客户端（requests.Session + JWT）
├── auth_api.py               # 认证模块方法
├── user_api.py               # 用户模块方法
├── ...                       # 其他模块

data/                         # 测试数据
├── login.json                # 登录参数化数据
├── create_team.json          # 创建团队参数化数据
├── ...                       # 更多 JSON 数据文件

scripts/                      # 测试脚本
├── test_api_01_auth.py       # 认证测试
├── test_api_02_user.py       # 用户测试
└── ...                       # 更多测试文件
```

### Fixtures

| Fixture | 说明 |
|---------|------|
| `api_client` | 未登录 API 客户端（公开接口/认证失败测试） |
| `api_client_admin` | 已登录管理员（admin/123456） |
| `api_client_member` | 已登录成员（zhangsan/123456） |
| `api_client_member2` | 已登录成员（lisi/123456） |
