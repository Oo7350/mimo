# Mimo 项目自动化测试教程

> 适用环境：Windows 11 | 测试框架：Playwright + Pytest + Allure + Requests

---

## 目录

1. [测试环境概览](#1-测试环境概览)
2. [Playwright E2E 测试](#2-playwright-e2e-测试)
3. [API 接口自动化测试](#3-api-接口自动化测试)
4. [测试数据管理](#4-测试数据管理)
5. [Jenkins 持续集成](#5-jenkins-持续集成)
6. [常见问题与排错](#6-常见问题与排错)
7. [面试常见问题速查](#7-面试常见问题速查)

---

## 1. 测试环境概览

### 项目已有的测试框架 `st2/`

```
st2/
├── base/                   # 页面基类（PO模式）
│   └── base_page.py        # BasePage：封装 Playwright 通用操作
├── pages/                  # 页面对象层
│   ├── page_login.py       # 登录页
│   ├── page_register.py    # 注册页
│   ├── page_board.py       # 看板页
│   └── page_dashboard.py   # 仪表盘页
├── api/                    # API 客户端层
│   ├── mimo_api_client.py  # 核心客户端（Session + JWT）
│   ├── auth_api.py, issue_api.py, board_api.py ...  # 各模块 API
├── scripts/                # 测试用例
│   ├── test_01_login.py    # 登录 E2E 测试
│   ├── test_02_register.py # 注册 E2E 测试
│   ├── test_03_board.py    # 看板 E2E 测试
│   ├── test_api_01_auth.py ~ test_api_11_attachment.py  # API 测试
├── data/                   # JSON 参数化测试数据
├── config.py               # 全局配置
├── conftest.py             # Fixture 定义（浏览器 + API 客户端）
├── pytest.ini              # Pytest 配置
└── requirements.txt        # Python 依赖
```

### 安装依赖

```bash
cd st2

# 1. 创建虚拟环境（Python 3.10+）
python -m venv .venv

# 2. 激活虚拟环境
.venv\Scripts\activate       # Windows CMD
# 或 .venv/Scripts/activate  # Windows PowerShell

# 3. 安装 Python 依赖
pip install -r requirements.txt

# 4. 安装 Playwright 浏览器（Chromium）
playwright install chromium
```

### 启动被测服务

测试前需确保前后端都在运行：

```bash
# 终端1：启动后端（确保 MySQL + Redis 已运行）
cd backend
set JAVA_HOME=D:\Java\jdk-21
mvn spring-boot:run

# 终端2：启动前端
cd frontend
npm run dev
```

---

## 2. Playwright E2E 测试

### 2.1 为什么用 Playwright 而不是 Selenium？

| 维度 | Selenium | Playwright |
|------|----------|------------|
| 浏览器驱动 | 手动下载 chromedriver，版本匹配麻烦 | `playwright install` 一键安装 |
| 元素等待 | 需要显式 `WebDriverWait` | **自动等待**元素可操作 |
| 定位方式 | 多种 By 策略，不统一 | 统一 `page.locator("selector")` |
| 浏览器隔离 | 需手动管理窗口 | Browser Context 天然隔离 |
| 网络拦截 | 需要额外配置 | 内置 `page.route()` API |
| 调试 | 截图 | Trace Viewer 时间轴回放 |
| WebSocket | 不支持 | 内置 `page.wait_for_websocket()` |

### 2.2 PO（Page Object）模式理解

**核心思想**：每个页面封装成类，操作细节对测试用例隐藏。

```
测试用例 (scripts/) → 页面对象 (pages/) → 基础操作 (base/)
     ↓                      ↓                    ↓
  写业务逻辑          封装元素定位         封装 click/fill
  读起来像步骤          不暴露选择器         等通用方法
```

**示例：登录页面对象** (`st2/pages/page_login.py`)

```python
class LoginPage(BasePage):
    def __init__(self, page: Page):
        super().__init__(page)
        self.url = "http://localhost:5173/login"

    def input_username(self, username: str):
        self.fill("input[placeholder='用户名']", username)

    def input_password(self, password: str):
        self.fill("input[placeholder='密码']", password)

    def click_login_btn(self):
        self.click("button:has-text('登录')")

    # 业务方法：一个方法完成整个登录流程
    def login(self, username="admin", password="123456"):
        self.open_url(self.url)
        self.input_username(username)
        self.input_password(password)
        self.click_login_btn()
```

**理解要点**：
- `self.fill("selector", text)` — Playwright 会自动等待元素可见再填充
- `self.click("selector")` — 自动等待元素可点击
- 不需要 `WebDriverWait`、不需要 `time.sleep()`（大部分情况）

### 2.3 Fixture 机制（conftest.py）

```python
# conftest.py — 这些 fixture 会被 pytest 自动注入

@pytest.fixture(scope="function")   # 每个测试函数独立
def browser():
    """启动 Chromium 浏览器"""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        yield browser
        browser.close()            # 测试结束后自动关闭

@pytest.fixture(scope="function")
def page(browser):
    """每个测试独立页面（Context 隔离 Cookie/Storage）"""
    context = browser.new_context(
        viewport={"width": 1920, "height": 1080}
    )
    page = context.new_page()
    yield page
    context.close()                # 自动清理，不会影响下个测试
```

**理解要点**：
- `scope="function"` — 每个测试函数拿到全新的浏览器页面
- `yield` 之前的代码是**前置操作**，之后的代码是**后置清理**
- Context 隔离意味着：测试A的登录状态不会影响测试B

### 2.4 编写一个完整的 E2E 测试用例

```python
"""scripts/test_login.py — 登录功能测试"""
import pytest
import allure
from pages.page_login import LoginPage
from config import BASE_URL


@allure.feature("登录模块")
class TestLogin:

    @allure.story("登录成功")
    @allure.title("使用正确的用户名和密码登录")
    def test_login_success(self, page):
        """
        测试步骤：
        1. 打开登录页
        2. 输入用户名 admin
        3. 输入密码 123456
        4. 点击登录
        5. 验证跳转到工作台页面
        """
        login_page = LoginPage(page)

        # 执行登录
        login_page.login("admin", "123456")

        # 断言：URL 不再包含 /login
        assert "/login" not in page.url, f"登录后应跳转，实际URL: {page.url}"

        # 断言：页面显示"工作台"
        assert "Mimo" in page.title()

    @allure.story("登录失败")
    @allure.title("用户名或密码错误时显示错误提示")
    def test_login_fail_wrong_password(self, page):
        """密码错误应有错误提示"""
        login_page = LoginPage(page)

        login_page.login("admin", "wrong_password")

        # 断言：页面仍停留在登录页
        assert "/login" in page.url
```

### 2.5 Playwright 常用 API 速查

```python
# === 元素定位 ===
page.locator("button")                        # CSS 选择器
page.locator("text=登录")                     # 文本匹配
page.locator("button:has-text('提交')")       # 包含文本
page.get_by_placeholder("用户名")              # placeholder 属性
page.get_by_role("button", name="确认")        # ARIA 角色

# === 操作 ===
page.locator("input").fill("文本")            # 输入
page.locator("button").click()               # 点击
page.locator("select").select_option("值")    # 下拉选择
page.locator("div").hover()                  # 悬停

# === 拖拽（看板核心功能）===
page.locator(".issue-card").drag_to(
    page.locator(".column:nth-child(3)")
)

# === 断言 ===
expect(page.locator(".title")).to_have_text("预期文本")
expect(page.locator(".card")).to_be_visible()
expect(page.locator(".card")).to_have_count(3)

# === 等待 ===
page.wait_for_selector(".loading", state="hidden")  # 等待 loading 消失
page.wait_for_url("**/dashboard")                   # 等待 URL 变化

# === 截图（失败时使用）===
page.screenshot(path="screenshots/error.png", full_page=True)

# === API 拦截（Mock 后端响应）===
page.route("**/api/**", lambda route: route.fulfill(
    status=200, body='{"code":200,"data":[]}'
))
```

### 2.6 运行 E2E 测试

```bash
cd st2

# 运行所有 E2E 测试
pytest scripts/test_01_login.py scripts/test_02_register.py scripts/test_03_board.py -v

# 运行单个测试方法
pytest scripts/test_01_login.py::TestLogin::test_login_success -v

# 按关键字匹配运行
pytest -k "login" -v

# 有界面模式（看浏览器怎么操作的）
# 在 config.py 中设 HEADLESS = False

# 生成 Allure 报告
pytest scripts/test_*.py --alluredir=report
allure serve report        # 打开可视化报告
```

### 2.7 E2E 测试覆盖的 Mimo 关键场景

| 场景 | 文件 | 说明 |
|------|------|------|
| 登录成功/失败 | `test_01_login.py` | 参数化覆盖多种情况 |
| 注册成功/重复注册 | `test_02_register.py` | 校验用户名/邮箱唯一性 |
| 看板拖拽 | `test_03_board.py` | Issue 在 TODO/IN_PROGRESS/DONE 间拖动 |
| Issue 创建/编辑/删除 | `test_03_board.py` | CRUD 全生命周期 |

---

## 3. API 接口自动化测试

### 3.1 架构设计

```
测试用例 (scripts/test_api_*.py)
    ↓ 调用
API 封装层 (api/*.py)  —  每个模块一个类
    ↓ 依赖
核心客户端 (api/mimo_api_client.py)  —  Session + JWT Token
    ↓ 发送
HTTP 请求  →  Mimo 后端 (:8080)
```

### 3.2 核心客户端解析

```python
# api/mimo_api_client.py
class MimoAPIClient:
    def __init__(self, base_url="http://localhost:8080"):
        self.session = requests.Session()  # 复用 TCP 连接
        self.token = None

    def login(self, username, password):
        """登录后自动保存 token"""
        resp = self.session.post(
            f"{self.base_url}/api/auth/login",
            json={"username": username, "password": password}
        )
        data = resp.json()
        if data["code"] == 200:
            self.token = data["data"]["token"]  # ← 关键：存 token
        return resp.json()

    def _get_headers(self):
        """自动给每个请求加上 Authorization 头"""
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def _get(self, path):
        return self.session.get(
            f"{self.base_url}{path}",
            headers=self._get_headers()
        )

    def _post(self, path, data):
        return self.session.post(
            f"{self.base_url}{path}",
            json=data,
            headers=self._get_headers()
        )
    # ... _put、_delete 同理
```

**理解要点**：
- `requests.Session()` — 复用连接，性能更好，自动管理 Cookie
- `self.token` — 登录成功后存起来，后续请求自动带上
- Mimo 的响应统一格式：`{"code": 200, "message": "success", "data": {...}}`

### 3.3 API Fixture 详解

```python
# conftest.py — API 测试的 fixture

@pytest.fixture(scope="function")
def api_client():
    """未登录客户端 — 测试公开接口或鉴权失败场景"""
    client = MimoAPIClient()
    yield client
    client.session.close()

@pytest.fixture(scope="function")
def api_client_admin():
    """管理员客户端 — 已登录 admin/123456"""
    client = MimoAPIClient()
    client.login("admin", "123456")
    yield client
    client.session.close()
```

**使用方式**：测试函数参数名匹配 fixture 名称即可自动注入。

```python
def test_something(api_client_admin):   # ← 自动拿到已登录的管理员客户端
    resp = api_client_admin._get("/api/user/profile")
    assert resp.json()["code"] == 200
```

### 3.4 编写一个 API 测试用例

```python
"""测试创建 Issue"""
import pytest
import allure


@allure.feature("Issue 模块")
class TestIssue:

    @allure.story("创建 Issue")
    def test_create_issue_success(self, api_client_admin):
        """正常创建 Issue"""
        resp = api_client_admin._post("/api/issue/create", {
            "projectId": 1,
            "title": "测试任务_" + str(int(time.time())),
            "description": "这是 API 自动化测试创建的任务",
            "priority": "P1",
            "type": "TASK"
        })

        assert resp.status_code == 200
        result = resp.json()
        assert result["code"] == 200
        assert result["data"]["title"] is not None

    @allure.story("创建 Issue — 无权限")
    def test_create_issue_unauthorized(self, api_client):
        """未登录不能创建 Issue（鉴权测试）"""
        resp = api_client._post("/api/issue/create", {
            "projectId": 1,
            "title": "未登录创建",
            "priority": "P1"
        })

        assert resp.status_code == 401  # Spring Security 拦截
```

### 3.5 参数化测试（Data-Driven）

使用 JSON 数据文件驱动测试，一套代码覆盖多种场景：

```python
# data/login.json
[
  {"username": "admin",    "password": "123456",  "status": 200, "errno": 200, "errmsg": ""},
  {"username": "admin",    "password": "wrong",   "status": 400, "errno": 1004, "errmsg": "密码错误"},
  {"username": "nouser",   "password": "123456", "status": 400, "errno": 1003, "errmsg": "不存在"},
  {"username": "",         "password": "123456", "status": 400, "errno": 400,  "errmsg": ""},
  {"username": "admin",    "password": "",        "status": 400, "errno": 400,  "errmsg": ""}
]
```

```python
# scripts/test_api_01_auth.py
@pytest.mark.parametrize(
    "username,password,status,errno,errmsg",
    [(d["username"], d["password"], d["status"], d["errno"], d["errmsg"])
     for d in read_json("login.json")]
)
def test_login_parametrized(self, api_client, username, password, status, errno, errmsg):
    """一条测试方法 = 5 个测试用例"""
    resp = api.auth.login({"username": username, "password": password})
    assert resp.status_code == status
    assert resp.json()["code"] == errno
```

### 3.6 响应格式与断言模式

Mimo 所有接口返回格式：

```json
{
  "code": 200,         // 业务状态码，200 表示成功
  "message": "success", // 提示信息
  "data": { ... }      // 实际数据（可能为 null）
}
```

**标准断言模板**：

```python
# 1. 检查 HTTP 状态码
assert resp.status_code == 200

# 2. 检查业务状态码
result = resp.json()
assert result["code"] == 200

# 3. 检查 data 内容
assert result["data"] is not None
assert result["data"]["title"] == expected_title
assert "token" in result["data"]

# 4. 检查错误消息（失败场景）
assert errmsg in result.get("message", "")
```

### 3.7 API 测试覆盖清单（共 11 个模块）

| 模块 | 文件 | 关键测试点 |
|------|------|-----------|
| 认证 | `test_api_01_auth.py` | 登录参数化(5种)、注册参数化(6种)、Token 验证 |
| 用户 | `test_api_02_user.py` | 个人信息、修改密码（参数化） |
| 仪表盘 | `test_api_03_dashboard.py` | 仪表盘数据正确性 |
| 团队 | `test_api_04_team.py` | 创建团队、成员管理、权限校验 |
| 项目 | `test_api_05_project.py` | 创建、列表、添加成员 |
| 看板 | `test_api_06_board.py` | 获取看板、列 CRUD、排序、拖拽 |
| Issue | `test_api_07_issue.py` | 创建(参数化)、查询(参数化)、CRUD、Bug 类型 |
| Sprint | `test_api_08_sprint.py` | 创建、生命周期（规划→活跃→完成） |
| 燃尽图 | `test_api_09_burndown.py` | 数据获取、快照创建 |
| 报告 | `test_api_10_report.py` | 生成(参数化)、CRUD、提交 |
| 附件 | `test_api_11_attachment.py` | 上传、列表、下载、删除生命周期 |

### 3.8 运行 API 测试

```bash
cd st2

# 运行所有 API 测试
pytest scripts/test_api_*.py -v

# 运行单个模块
pytest scripts/test_api_01_auth.py -v

# 运行指定用例
pytest -k "test_login_parametrized" -v

# 显示 print 输出（调试用）
pytest scripts/test_api_07_issue.py -v -s

# 生成 Allure 报告
pytest scripts/test_api_*.py --alluredir=report
allure serve report
```

---

## 4. 测试数据管理

### 4.1 数据文件位置

`st2/data/` 下的 JSON 文件：

| 文件 | 用途 |
|------|------|
| `login.json` | 登录参数化（5种场景） |
| `register.json` | 注册参数化（6种场景） |
| `create_team.json` | 创建团队参数化 |
| `create_project.json` | 创建项目参数化 |
| `create_issue.json` | 创建 Issue 参数化 |
| `create_sprint.json` | 创建 Sprint 参数化 |
| `update_issue.json` | 更新 Issue 参数化 |
| `generate_report.json` | 生成报告参数化 |

### 4.2 读取测试数据

```python
# tools/read_data.py
import json
import os

def read_json(filename):
    filepath = os.path.join(os.path.dirname(__file__), "..", "data", filename)
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)

# 使用
test_data = read_json("login.json")
# 返回 [{username, password, status, errno, errmsg}, ...]
```

### 4.3 新增测试数据的步骤

1. 在 `st2/data/` 下创建 JSON 文件
2. 在测试用例中使用 `read_json("your_file.json")`
3. 用 `@pytest.mark.parametrize` 注入参数

---

## 5. Jenkins 持续集成

### 5.1 Jenkins Pipeline 概念

```
代码提交 (git push)
    ↓ 触发
Jenkins Pipeline
    ├── Stage 1: 拉代码 + 编译
    ├── Stage 2: 启动服务
    ├── Stage 3: 运行测试
    └── Stage 4: 生成 Allure 报告
```

### 5.2 基础 Jenkinsfile

```groovy
pipeline {
    agent any

    environment {
        JAVA_HOME = 'D:/Java/jdk-21'
    }

    stages {
        stage('Build Backend') {
            steps {
                dir('backend') {
                    bat 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    bat 'npm install && npm run build'
                }
            }
        }

        stage('Test') {
            steps {
                dir('st2') {
                    bat 'pytest scripts/test_api_*.py --alluredir=allure-results'
                }
            }
            post {
                always {
                    allure includeProperties: false,
                           results: [[path: 'st2/allure-results']]
                }
            }
        }
    }
}
```

---

## 6. 常见问题与排错

### Q1：`playwright install chromium` 下载慢/失败？

```bash
# 设置环境变量使用国内镜像
set PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright/
playwright install chromium
```

### Q2：后端启动报 "Communications link failure"？

```bash
# 检查 MySQL 是否运行
sc query MySQL80
# 如果未运行，启动 MySQL 服务
net start MySQL80
```

### Q3：API 测试返回 401？

```bash
# 检查数据库 seed 数据是否存在
# 确保 backend/src/main/resources/db/init.sql 已执行
mysql -u root -p"336699" mimo < backend/src/main/resources/db/init.sql
```

### Q4：测试用例间数据相互影响？

API 测试 fixture 是 `scope="function"` 的，每个测试用例独立创建 Session。但共享的数据库状态（如创建了同名项目）可能会冲突。解决方法：
- 使用时间戳生成唯一数据名
- 测试后清理创建的测试数据

### Q5：Playwright 元素定位不到？

策略优先级：
1. **用浏览器 DevTools 检查元素**，优先用 `placeholder`、`role`、`text` 定位
2. 检查元素是否在 iframe 中
3. 检查是否有 loading 遮罩层
4. 增大 `config.py` 中的 `TIMEOUT`

```python
# 调试技巧：截图看页面状态
page.screenshot(path="debug.png")

# Trace 回放（最强调试工具）
# 在 conftest.py 中开启 trace
context = browser.new_context()
context.tracing.start(screenshots=True, snapshots=True)
# ... 执行操作 ...
context.tracing.stop(path="trace.zip")
# 用 npx playwright show-trace trace.zip 查看
```

---

## 7. 面试常见问题速查

### Playwright 相关

**Q: Playwright 比 Selenium 好在哪？**
A: 自动等待（无需 WebDriverWait）、原生 Context 隔离、内置网络拦截 API、支持 WebSocket、Trace Viewer 调试、一键安装浏览器驱动。

**Q: PO（Page Object）模式是什么？为什么用？**
A: 将页面元素定位和操作封装成类，测试用例只调用业务方法。好处：选择器变化只需改一处、测试用例可读性高、减少重复代码。

**Q: Playwright 的 fixture 是怎么工作的？**
A: `conftest.py` 中定义 fixture（用 `@pytest.fixture`），通过 `yield` 实现前置和后置。测试函数参数名匹配 fixture 名称即可自动注入。

### API 测试相关

**Q: 你的 API 测试框架怎么设计的？**
A: 三层架构：核心客户端（Session + JWT Token 管理）→ API 封装层（按模块拆分方法）→ 测试用例层（断言业务逻辑）。

**Q: 参数化测试是什么？**
A: `@pytest.mark.parametrize` 装饰器 + JSON 数据文件，一套测试代码覆盖正常/异常/边界多种场景。

**Q: 如何处理需要认证的接口？**
A: 请求头加 `Authorization: Bearer <token>`。Token 通过 fixture 中 `client.login()` 获取并自动附加到后续请求。

### CI/CD 相关

**Q: Jenkins Pipeline 怎么配置的？**
A: 通过 Jenkinsfile 定义：拉代码 → 编译（Maven/npm）→ 运行测试（pytest）→ 生成 Allure 报告。可配置 Git webhook 触发出触发构建。

**Q: Allure 报告能展示什么？**
A: 测试通过率、失败用例的详细步骤和截图、按模块/功能的统计、历史趋势图。

---

## 附录：快速命令速查

```bash
# === E2E 测试 ===
pytest scripts/test_01_login.py -v                    # 运行登录测试
pytest -k "login" -v                                  # 按关键字运行
pytest scripts/test_*.py -v -n 4                     # 4 线程并发
pytest --alluredir=report && allure serve report      # 生成报告

# === API 测试 ===
pytest scripts/test_api_*.py -v                       # 所有 API 测试
pytest scripts/test_api_07_issue.py -v                # Issue 模块
pytest -k "test_create" -v                            # 按名称筛选

# === Playwright 工具 ===
playwright install chromium                            # 安装浏览器
playwright codegen http://localhost:5173              # 录制脚本
npx playwright show-trace trace.zip                  # 查看 Trace
```
