# Playwright 自动化测试 —— 从 Selenium 迁移实战

# Day00 课堂笔记

**背景**

你已经掌握了 `Python + Pytest + Selenium + PO + Jenkins + Allure` 技术栈。Playwright 是微软推出的下一代自动化测试框架，在**速度、稳定性、API 设计**上全面超越 Selenium。

**学习目标**

- <font color="fuchsia">理解 Playwright 相比 Selenium 的核心优势</font>
- <font color="fuchsia">掌握 Playwright 环境搭建与基本使用</font>
- <font color="fuchsia">掌握 Playwright 元素定位方式</font>
- <font color="fuchsia">能按 PO 模式组织 Playwright 项目</font>
- <font color="fuchsia">集成 pytest + allure + Jenkins 持续集成</font>

---

## 1. Playwright vs Selenium 核心对比

### 1.1 为什么选 Playwright

| 对比维度 | Selenium | Playwright |
| :--- | :--- | :--- |
| **浏览器启动** | 需手动下载匹配版本的驱动（chromedriver） | 内置浏览器，`playwright install` 一条命令搞定 |
| **运行速度** | 每个操作都通过 HTTP 协议与驱动通信，慢 | 通过 CDP（Chrome DevTools Protocol）直接通信，快很多 |
| **等待机制** | 需要 `time.sleep()` / 隐式等待 / 显式等待 | **自动等待**元素可操作后才执行，无需手写等待 |
| **浏览器支持** | Chrome、Firefox、Edge、Safari | Chromium、Firefox、WebKit（Safari 内核），**三件套全覆盖** |
| **iframe 处理** | 需要 `driver.switch_to.frame()` 手动切换 | API 设计无需切换，直接操作 |
| **多页面/多窗口** | 需获取句柄手动切换 | `context.new_page()` / `page.wait_for_event('popup')` 更优雅 |
| **网络拦截** | 需借助 BrowserMob 等代理工具 | 内置 `page.route()` 可直接 mock 接口 |
| **截图/录屏** | 截图可以，录屏需额外工具 | **原生支持录屏** `record_video_dir` |
| **并发/多浏览器** | 需借助 Selenium Grid / pytest-xdist | 原生 **Browser Context** 隔离，天生支持并行 |
| **代码生成** | 需第三方插件（Katalon 等） | 内置 **`codegen`**，录制操作自动生成代码 |
| **调试体验** | 较原始 | 内置 **Trace Viewer**（时间轴回放）和 **Inspector** |
| **安装依赖** | `pip install selenium` + 手动驱动管理 | `pip install playwright` + `playwright install` |

### 1.2 一张图理解架构差异

```yacas
# Selenium 架构（每一层都要走网络）
Python脚本 -> WebDriver HTTP Server -> 浏览器驱动 -> 浏览器内核

# Playwright 架构（WebSocket 长连接，延迟低）
Python脚本 <--> Playwright Server（CDP/WebSocket）<--> 浏览器内核
```

---

## 2. ==Playwright 环境搭建:black_flag:==

### 2.1 安装

```python
# 方式1：安装 playright 主库（推荐）
pip install playwright

# 方式2：国内镜像加速
pip install playwright -i https://pypi.tuna.tsinghua.edu.cn/simple/

# 安装内置浏览器（Chromium + Firefox + WebKit）
playwright install

# 查看安装版本
pip show playwright
playwright --version
```

> **注意**：`playwright install` 会下载三个浏览器内核到本地，大约 300-500MB，第一次下载需耐心等待。如果只想装一个浏览器：
>
> ```python
> playwright install chromium  # 只装 Chromium
> ```

### 2.2 确认安装结果

```python
# 在 Python 中验证
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.goto("https://www.baidu.com")
    print(page.title())  # 输出：百度一下，你就知道
    browser.close()
```

---

## 3. ==Playwright 基本使用:black_flag:==

### 3.1 最小化脚本（对比 Selenium 写法）

**Selenium 写法（你已掌握）：**

```python
import time
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By

# 手动指定驱动路径
path = r"C:\Program Files\Python311\chromedriver.exe"
ser = Service(executable_path=path)
driver = webdriver.Chrome(service=ser)

driver.get("https://hmshop-test.itheima.net")
driver.maximize_window()
time.sleep(2)  # 强制等待
driver.find_element(By.ID, "username").send_keys("13800000001")
driver.find_element(By.ID, "password").send_keys("123456")
driver.find_element(By.CLASS_NAME, "login-btn").click()
time.sleep(2)
driver.quit()
```

**Playwright 写法（新学）：**

```python
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    # 启动浏览器（无需单独下载驱动！）
    browser = p.chromium.launch(headless=False)  # headless=False 显示浏览器窗口
    page = browser.new_page()
    page.goto("https://hmshop-test.itheima.net")
    # 无需 maxmize_window，也无需 time.sleep！
    page.fill("#username", "13800000001")  # CSS选择器直接填充
    page.fill("#password", "123456")
    page.click(".login-btn")  # 自动等待元素可点击
    browser.close()
```

> **关键变化**：
> - 无需 `Service`、无需 `chromedriver.exe`
> - 无需 `maximize_window()`（默认就是最大化）
> - 无需 `time.sleep()`（自动等待元素就绪后再操作）

### 3.2 两种编写模式：同步 vs 异步

| 对比项 | 同步 API（sync_api） | 异步 API（async_api） |
| :--- | :--- | :--- |
| **适用对象** | 普通自动化测试，写法接近 Selenium | 高性能爬虫、并发场景 |
| **写法** | 顺序写，从上到下 | 需要 `async/await` |
| **推荐程度** | **初学推荐**，迁移成本低 | 进阶使用 |

```python
# 同步写法（推荐初学者使用）
from playwright.sync_api import sync_playwright

def test_login():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        page.goto("https://example.com")
        page.fill("#username", "admin")
        browser.close()

# 异步写法（需要 pytest-asyncio 配合）
import asyncio
from playwright.async_api import async_playwright

async def test_login_async():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()
        await page.goto("https://example.com")
        await page.fill("#username", "admin")
        await browser.close()
```

### 3.3 浏览器启动选项

```python
# 完整参数配置（对照 Selenium 的理解）
browser = p.chromium.launch(
    headless=False,         # False=显示窗口 True=无头模式（Jenkins上用）
    slow_mo=200,            # 每个操作间隔200ms（调试时用，相当于每个步骤加了 time.sleep(0.2)）
    args=["--start-maximized"],  # 启动参数（等同于 selenium 的 options）
    channel="chrome"        # 使用本地已安装的 Chrome（默认用内置 Chromium）
)

# 创建页面时配置
page = browser.new_page(
    viewport={"width": 1920, "height": 1080},  # 窗口大小
    locale="zh-CN",         # 语言
    record_video_dir="./videos",  # 录屏保存目录（Selenium 做不到！）
)
```

---

## 4. ==元素定位:black_flag:==

> Playwright 的元素定位比 Selenium **更简单**。绝大多数场景直接用 CSS 选择器或文本定位即可。

### 4.1 定位方式速查表

| 定位方式 | Playwright 写法 | Selenium 对照 |
| :--- | :--- | :--- |
| CSS 选择器 | `page.locator("#id")` `page.locator(".class")` | `find_element(By.CSS_SELECTOR, ".class")` |
| 文本内容 | `page.get_by_text("登录")` | `find_element(By.LINK_TEXT, "登录")` |
| 标签+文本 | `page.locator("button:has-text('登录')")` | xpath: `//button[text()='登录']` |
| placeholder | `page.get_by_placeholder("请输入用户名")` | xpath: `//*[@placeholder='请输入用户名']` |
| label 标签 | `page.get_by_label("用户名")` | 需手动写 xpath |
| role 角色 | `page.get_by_role("button", name="提交")` | 无对应 |
| XPath | `page.locator("xpath=//input[@id='username']")` | `find_element(By.XPATH, "...")` |
| 第N个元素 | `page.locator(".item").nth(2)` | `find_elements(...)[2]` |

### 4.2 locator 核心用法（替代 find_element）

> **Playwright 用 `locator()` 替代 Selenium 所有的 `find_element` 系列方法。**

```python
# Selenium 中
driver.find_element(By.ID, "username").send_keys("admin")
driver.find_element(By.NAME, "password").send_keys("123456")
driver.find_element(By.CLASS_NAME, "btn").click()

# Playwright 中（统一用 locator）
page.locator("#username").fill("admin")        # ID 定位
page.locator("[name='password']").fill("123456")  # 属性定位
page.locator(".btn").click()                  # class 定位
```

**Playwright 定位方式的优先级（推荐度从高到低）：**

```yacas
1. get_by_text() / get_by_label() / get_by_placeholder() —— 最稳定，接近用户行为
2. locator("CSS选择器") —— 最灵活，优先用 CSS 而非 XPath
3. locator("xpath=...") —— 兜底方案，CSS 搞不定再用
```

### 4.3 案例：TPSHOP 前台登录（Playwright 版）

```python
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    page = browser.new_page()
    page.goto("https://hmshop-test.itheima.net/Home/User/login.html")

    # 方式1：CSS 选择器定位
    page.locator(".text_cmu").fill("13800000001")
    page.locator("#password").fill("123456")
    page.locator("[name='verify_code']").fill("8888")
    page.locator(".J-login-submit").click()

    # 方式2：文本定位（更推荐，更接近用户行为）
    # page.get_by_placeholder("手机号").fill("13800000001")
    # page.get_by_placeholder("密码").fill("123456")
    # page.get_by_placeholder("验证码").fill("8888")
    # page.get_by_text("登 录").click()

    # 断言 —— 获取文本
    result = page.locator(".red.userinfo").text_content()
    assert result == "测试账号"

    browser.close()
```

### 4.4 复杂元素定位（对照 Selenium XPath/CSS 扩展）

```python
# ==== Selenium 中的高级定位 ====
# driver.find_element(By.XPATH, "//*[contains(@placeholder,'用户名')]")
# driver.find_element(By.XPATH, "//*[@name='username' and @type='text']")
# driver.find_element(By.XPATH, "//form/div/input")
# driver.find_element(By.CSS_SELECTOR, "p>input")
# driver.find_element(By.CSS_SELECTOR, "input[id*='pass']")

# ==== Playwright 等价写法 ====
# 模糊匹配：包含某属性值
page.locator("input[placeholder*='用户名']")
# 多属性组合
page.locator("input[name='username'][type='text']")
# 层级关系：后端子孙关系（空格=下级）
page.locator("form div input")
# 层级关系：直接子元素（> = 父子）
page.locator("p > input")
# 局部属性值匹配
page.locator("input[id*='pass']")
# 文本内容匹配
page.locator("//*[text()='百度']")
page.locator("a:has-text('百度')")   # 等价，但更推荐
```

---

## 5. ==常见操作对照（Selenium → Playwright）:black_flag:==

### 5.1 操作速查表

| 操作 | Selenium | Playwright |
| :--- | :--- | :--- |
| 输入内容 | `element.send_keys("text")` | `page.locator(...).fill("text")` |
| 清空输入 | `element.clear()` | `page.locator(...).clear()` |
| 点击 | `element.click()` | `page.locator(...).click()` |
| 双击 | （需 ActionChains） | `page.locator(...).dblclick()` |
| 获取文本 | `element.text` | `page.locator(...).text_content()` |
| 获取属性 | `element.get_attribute("href")` | `page.locator(...).get_attribute("href")` |
| 判断可见 | `element.is_displayed()` | `page.locator(...).is_visible()` |
| 判断可用 | `element.is_enabled()` | `page.locator(...).is_enabled()` |
| 鼠标悬停 | `ActionChains(driver).move_to_element(el).perform()` | `page.locator(...).hover()` |
| 拖拽 | `ActionChains(driver).drag_and_drop(a,b).perform()` | `page.locator(a).drag_to(page.locator(b))` |
| 右键点击 | `ActionChains(driver).context_click(el).perform()` | `page.locator(...).click(button="right")` |
| 文件上传 | `element.send_keys("文件路径")` | `page.locator("input[type=file]").set_input_files("路径")` |
| 截图 | `driver.get_screenshot_as_file("路径")` | `page.screenshot(path="路径.png")` |
| 元素截图 | 需要裁剪 | `page.locator(...).screenshot(path="el.png")` |
| 键盘按键 | `element.send_keys(Keys.ENTER)` | `page.locator(...).press("Enter")` |
| 组合键 | `ActionChains...key_down(Keys.CTRL)...` | `page.keyboard.press("Control+A")` |
| 下拉框选择 | `Select(el).select_by_value("value")` | `page.locator("select").select_option(value="value")` |

### 5.2 等待机制 —— 最核心的变化

```python
# Selenium 中的三种等待
import time
time.sleep(2)                         # 1.强制等待（不推荐）
driver.implicitly_wait(10)           # 2.隐式等待（全局）
from selenium.webdriver.support.ui import WebDriverWait
WebDriverWait(driver, 10).until(...)  # 3.显式等待（特定元素）

# Playwright 中 —— 大部分情况不需要写等待！
# 因为 locator.fill() / locator.click() 会自动等待元素可见且可操作
page.locator("#username").fill("admin")  # 自动等待 #username 出现并可输入
page.locator(".btn").click()             # 自动等待 .btn 出现并可点击

# 少数需要显式等待的场景：
page.wait_for_selector(".item", state="visible")    # 等待元素可见
page.wait_for_timeout(2000)                          # 硬等（调试用）
page.wait_for_load_state("networkidle")             # 等待网络请求完成
page.wait_for_url("**/success**")                   # 等待 URL 跳转成功
```

### 5.3 frame 处理

```python
# Selenium 中
frame = driver.find_element(By.ID, "iframe-id")
driver.switch_to.frame(frame)      # 进入 frame
# ... 操作 frame 内元素 ...
driver.switch_to.default_content()  # 退出 frame

# Playwright 中 —— 无需切换，直接用 frame_locator
page.frame_locator("#iframe-id").locator("#username").fill("admin")
# 等价于：定位到 iframe，然后在其中操作元素，一步到位
```

### 5.4 多窗口处理

```python
# Selenium 中
handles = driver.window_handles
driver.switch_to.window(handles[1])  # 切换到新窗口

# Playwright 中（方式1：监听新页面事件）
with page.expect_popup() as popup_info:
    page.locator("a.new-window-link").click()  # 点击会打开新窗口的链接
new_page = popup_info.value
new_page.locator("#new-page-element").fill("hello")

# Playwright 中（方式2：直接管理所有页面）
page1 = browser.new_page()  # 打开新标签页
page1.goto("https://xxx.com")
page2 = browser.new_page()  # 再开一个标签页
page2.goto("https://yyy.com")
# 直接操作 page1 或 page2，无需"切换"！
```

### 5.5 弹框处理

```python
# Selenium 中
alert = driver.switch_to.alert
alert.accept()   # 确认弹框
alert.dismiss()  # 取消弹框

# Playwright 中（方式1：自动处理）
page.on("dialog", lambda dialog: dialog.accept())  # 自动确认所有弹框

# Playwright 中（方式2：手动处理）
with page.expect_event("dialog") as dialog_info:
    page.locator(".alert-trigger").click()
dialog = dialog_info.value
dialog.accept()  # 确认
# dialog.dismiss()  # 取消
```

### 5.6 下拉框处理

```python
# Selenium（原生 select+option 下拉框）
from selenium.webdriver.support.select import Select
Select(element).select_by_value("value")

# Playwright —— 直接 select_option，更简洁
page.locator("select.my-select").select_option(value="value1")
page.locator("select.my-select").select_option(label="选项文字")
page.locator("select.my-select").select_option(index=1)  # 选第2个

# 非原生下拉框（div 拼出来的）—— 和普通点击一样
page.locator(".dropdown-trigger").click()
page.locator(".dropdown-item:has-text('选项A')").click()
```

### 5.7 模拟键盘操作

```python
# Selenium
from selenium.webdriver.common.keys import Keys
element.send_keys(Keys.ENTER)

# Playwright
page.locator("#search").press("Enter")      # 单个按键
page.keyboard.press("Control+A")            # 组合键
page.keyboard.type("hello world", delay=100) # 模拟打字（delay=每字符间隔ms）
```

### 5.8 滚动条处理

```python
# Selenium
js = "window.scrollTo(0, 1000)"
driver.execute_script(js)

# Playwright —— 多种方式
# 方式1：滚动到元素（最常用）
page.locator("#bottom-element").scroll_into_view_if_needed()
# 方式2：JS 滚动
page.evaluate("window.scrollTo(0, 1000)")
# 方式3：像素滚动
page.mouse.wheel(0, 1000)
```

---

## 6. ==浏览器上下文（Context）—— Playwright 独有特性:black_flag:==

> 这是 Playwright 比 Selenium **最大的设计亮点之一**。

### 6.1 什么是 Browser Context

```yacas
可以把 Browser Context 理解为"一个独立的浏览器会话"。
每个 Context 相当于一个全新的浏览器配置文件：
  - 独立的 Cookie / localStorage
  - 独立的登录态
  - 不共享任何数据
同一个 Browser 可以创建多个 Context，它们相互隔离。
```

### 6.2 Context 解决的实际问题

```yacas
场景1：同时用10个账号登录测试 → 10个 Context，互不影响
场景2：验证"两个用户同时操作"的场景 → 不同 Context 模拟不同用户
场景3：测试不同权限的用户 → 每个 Context 对应一个角色
场景4：避免 Cookie 残留 → 每个测试用例用独立 Context，天然隔离
```

### 6.3 Context 代码示例

```python
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch()

    # 创建用户A的 Context（模拟用户A的浏览器环境）
    ctx_a = browser.new_context(
        storage_state="user_a_cookies.json",  # 从文件加载登录态
        locale="zh-CN"
    )
    page_a = ctx_a.new_page()
    page_a.goto("https://xxx.com")

    # 创建用户B的 Context（完全独立）
    ctx_b = browser.new_context()
    page_b = ctx_b.new_page()
    page_b.goto("https://xxx.com")

    # A 和 B 的 Cookie、登录状态完全隔离，互不影响！
    page_a.locator("#welcome").text_content()  # 显示用户A的信息
    page_b.locator("#welcome").text_content()  # 显示用户B的信息

    ctx_a.close()
    ctx_b.close()
    browser.close()
```

### 6.4 复用登录态（解决验证码！）

> 对照你学过的：Selenium 中通过 cookie 跳过验证码。Playwright 提供了更优雅的 `storage_state`。

```python
# 步骤1：手动登录一次，保存状态
with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    page = browser.new_page()
    page.goto("https://hmshop-test.itheima.net/Home/User/login.html")
    page.fill("#username", "13800000001")
    page.fill("#password", "123456")
    page.fill("#verify_code", "8888")
    page.click(".J-login-submit")
    # 保存登录态到本地文件
    page.context.storage_state(path="login_state.json")
    browser.close()

# 步骤2：后续测试直接加载登录态，跳过登录
with sync_playwright() as p:
    browser = p.chromium.launch()
    # 加载已保存的登录态
    context = browser.new_context(storage_state="login_state.json")
    page = context.new_page()
    page.goto("https://hmshop-test.itheima.net")  # 已经是登录状态！
    # 直接进入功能测试，无需反复登录
    browser.close()
```

---

## 7. ==Playwright + PO 模式项目结构:black_flag:==

### 7.1 项目目录结构（对照你已有的 Selenium 项目）

```yacas
playwright_project/
├── base/
│   └── base_page.py          # 基础页面类（封装通用操作）
├── pages/
│   ├── page_login.py          # 登录页（继承 BasePage）
│   ├── page_register.py       # 注册页
│   └── page_back_login.py     # 后台登录页
├── scripts/
│   ├── conftest.py            # pytest fixture 前后置（浏览器管理）
│   ├── test_login.py          # 登录测试用例
│   └── test_business.py       # 业务流程测试用例
├── data/
│   ├── login_data.json        # 测试数据
│   └── register_data.json
├── report/                    # allure 临时报告
├── new_report/                # allure HTML 报告
├── logs/                      # 日志文件
├── img/                       # 截图
├── tools/
│   ├── driver_tools.py        # Playwright 驱动工具
│   ├── logger.py              # 日志封装
│   └── read_data.py           # 读取 JSON 数据
├── config.py                  # 项目配置
├── pytest.ini                 # pytest 配置
└── cmd_allure.py              # 生成 allure 报告
```

### 7.2 base_page.py（基础层封装）

```python
# base/base_page.py
from playwright.sync_api import Page

class BasePage:
    """页面基类，封装通用操作方法"""

    def __init__(self, page: Page):
        self.page = page

    def open_url(self, url: str):
        """打开指定 URL"""
        self.page.goto(url)

    def click(self, selector: str):
        """点击元素"""
        self.page.locator(selector).click()

    def fill(self, selector: str, text: str):
        """输入文本"""
        self.page.locator(selector).clear()
        self.page.locator(selector).fill(text)

    def get_text(self, selector: str) -> str:
        """获取元素文本"""
        return self.page.locator(selector).text_content().strip()

    def get_text_by_placeholder(self, placeholder_text: str) -> str:
        """通过 placeholder 获取元素文本"""
        return self.page.get_by_placeholder(placeholder_text).text_content()

    def is_visible(self, selector: str) -> bool:
        """判断元素是否可见"""
        return self.page.locator(selector).is_visible()

    def screenshot(self, path: str):
        """页面截图"""
        self.page.screenshot(path=path)
```

### 7.3 page_login.py（页面层封装）

```python
# pages/page_login.py
from playwright.sync_api import Page
from base.base_page import BasePage

class LoginPage(BasePage):
    """登录页面对象"""

    def __init__(self, page: Page):
        super().__init__(page)
        # 页面 URL
        self.url = "https://hmshop-test.itheima.net/Home/User/login.html"

    def open_login_url(self):
        """打开登录页面"""
        self.open_url(self.url)

    def input_phone(self, phone: str):
        """输入手机号"""
        self.fill(".text_cmu", phone)

    def input_password(self, pwd: str):
        """输入密码"""
        self.fill("#password", pwd)

    def input_verify_code(self, code: str):
        """输入验证码"""
        self.fill("[name='verify_code']", code)

    def click_login_btn(self):
        """点击登录按钮"""
        self.click(".J-login-submit")

    def get_success_text(self) -> str:
        """获取登录成功后的用户名文本"""
        return self.get_text(".red.userinfo")

    def get_fail_text(self) -> str:
        """获取登录失败后的提示文本"""
        return self.get_text(".layui-layer-content")

    # ===== 业务方法：封装完整登录流程 =====
    def login(self, phone: str, pwd: str, code: str = "8888"):
        """完整登录流程"""
        self.input_phone(phone)
        self.input_password(pwd)
        self.input_verify_code(code)
        self.click_login_btn()
```

### 7.4 conftest.py（fixture 管理前后置）

```python
# scripts/conftest.py
import pytest
from playwright.sync_api import sync_playwright, Browser, Page
from config import BASE_URL

@pytest.fixture(scope="function")
def browser():
    """每个测试用例独立一个浏览器实例"""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        yield browser
        browser.close()


@pytest.fixture(scope="function")
def page(browser: Browser):
    """每个测试用例独立一个页面（独立 Context）"""
    context = browser.new_context(
        viewport={"width": 1920, "height": 1080},
        locale="zh-CN"
    )
    page = context.new_page()
    # 设置默认超时（相当于 Selenium 的隐式等待）
    page.set_default_timeout(10000)
    yield page
    context.close()


@pytest.fixture(scope="function")
def logged_in_context(browser: Browser):
    """加载已保存的登录态（跳过登录步骤）"""
    context = browser.new_context(
        storage_state="login_state.json",  # 提前保存的登录状态
        viewport={"width": 1920, "height": 1080}
    )
    page = context.new_page()
    yield page
    context.close()


@pytest.fixture(scope="session", autouse=True)
def session_setup():
    """全局前置：整个测试 session 执行一次"""
    print("\n===== 开始执行 Playwright 自动化测试 =====")
    yield
    print("\n===== 测试结束 =====")
```

### 7.5 test_login.py（测试脚本层）

```python
# scripts/test_login.py
import pytest
import allure
from pages.page_login import LoginPage
from tools.read_data import read_json
from tools import logger


class TestLogin:
    """登录功能测试"""

    # ===== 参数化登录测试 =====
    arg_names = "phone,pwd,expect,img"

    @pytest.mark.parametrize(arg_names, read_json("login_data.json"))
    def test_login(self, page, phone, pwd, expect, img):
        """登录测试：覆盖成功和失败场景"""
        # 创建页面对象
        login_page = LoginPage(page)

        try:
            # 打开登录页
            login_page.open_login_url()
            # 执行登录
            login_page.login(phone, pwd)
            # 等待页面响应
            page.wait_for_timeout(1000)

            # 判断登录结果
            if phone == expect:  # 预期成功
                result = login_page.get_success_text()
            else:  # 预期失败
                result = login_page.get_fail_text()

            # 日志记录
            logger.info(f"登录测试 —— 手机号：{phone}，结果：{result}")
            # 断言
            assert expect in result, f"预期包含'{expect}'，实际结果'{result}'"

        except Exception as e:
            # 失败截图
            login_page.screenshot(f"./img/{img}")
            logger.error(f"用例执行异常：{e}")
            raise


    def test_login_success(self, page):
        """登录成功 —— 单条用例示例"""
        login_page = LoginPage(page)
        login_page.open_login_url()
        login_page.login("13800000001", "123456", "8888")
        result = login_page.get_success_text()
        assert "测试账号" in result
```

### 7.6 测试数据 JSON

```json
[
    {"phone": "13800000001", "pwd": "123456", "expect": "测试账号", "img": "login_success.png"},
    {"phone": "13800000001", "pwd": "wrong_pwd", "expect": "密码错误", "img": "login_fail_pwd.png"},
    {"phone": "13800000002", "pwd": "123456", "expect": "账号不存在", "img": "login_fail_user.png"}
]
```

### 7.7 driver_tools.py（驱动工具封装）

```python
# tools/driver_tools.py
from playwright.sync_api import sync_playwright, Playwright, Browser, BrowserContext, Page

_playwright: Playwright = None
_browser: Browser = None
_context: BrowserContext = None
_page: Page = None


def get_playwright() -> Playwright:
    """获取 Playwright 实例（单例）"""
    global _playwright
    if _playwright is None:
        _playwright = sync_playwright().start()
    return _playwright


def get_browser(headless: bool = False) -> Browser:
    """获取 Browser 实例"""
    global _browser
    if _browser is None:
        pw = get_playwright()
        _browser = pw.chromium.launch(headless=headless)
    return _browser


def get_page() -> Page:
    """获取 Page 实例"""
    global _context, _page
    browser = get_browser()
    _context = browser.new_context(
        viewport={"width": 1920, "height": 1080},
        locale="zh-CN"
    )
    _page = _context.new_page()
    _page.set_default_timeout(10000)
    return _page


def quit_driver():
    """关闭浏览器驱动"""
    global _browser, _context, _playwright
    if _context:
        _context.close()
        _context = None
    if _browser:
        _browser.close()
        _browser = None
    if _playwright:
        _playwright.stop()
        _playwright = None
```

### 7.8 config.py（项目配置）

```python
# config.py
# 测试环境地址
BASE_URL = "https://hmshop-test.itheima.net"

# 默认测试账号
DEFAULT_USER = "13800000001"
DEFAULT_PWD = "123456"
DEFAULT_CODE = "8888"

# 浏览器设置
HEADLESS = False      # 是否无头模式（Jenkins 上设 True）
SLOW_MO = 0           # 操作间隔（ms），调试时可设为 200
TIMEOUT = 10000       # 默认超时（ms）

# 截图路径
IMG_DIR = "./img"

# allure 报告路径
REPORT_DIR = "./report"
ALLURE_RESULTS = "./new_report"
```

### 7.9 pytest.ini

```ini
[pytest]
# 命令行参数：-s 显示print，--alluredir 生成报告
addopts = -s --alluredir report --clean-alluredir
# 测试文件搜索路径
testpaths = ./scripts
# 文件名匹配规则
python_files = test*.py
# 类名匹配规则
python_classes = Test*
# 方法名匹配规则
python_functions = test*
```

### 7.10 cmd_allure.py

```python
# cmd_allure.py
import os

# 生成 allure 离线 HTML 报告
run_cmd = "allure generate ./report -o ./new_report --clean"
os.system(run_cmd)
```

---

## 8. ==Playwright 高级特性:black_flag:==

### 8.1 网络拦截（Mock API 返回）

> Selenium 做接口 mock 非常麻烦，Playwright 内置支持。

```python
# 拦截并修改 API 返回值（模拟后端异常）
def mock_api(page):
    def handle_route(route):
        """拦截所有对 /api/user/info 的请求，返回假数据"""
        if "/api/user/info" in route.request.url:
            route.fulfill(
                status=200,
                content_type="application/json",
                body='{"username":"mock_user","balance":9999.99}'
            )
        else:
            route.continue_()  # 不拦截的请求正常放行

    page.route("**/api/**", handle_route)

# 使用场景：后端还没开发完 → Mock 接口继续测试前端
# 使用场景：模拟后端 500 错误 → 验证前端的错误提示
```

### 8.2 截获请求/响应

```python
# 监听所有请求
page.on("request", lambda request: print(f"发起请求：{request.url}"))

# 监听所有响应
page.on("response", lambda response: print(f"响应：{response.status} {response.url}"))

# 获取某个请求的响应数据（用于断言 API 返回）
with page.expect_response(lambda resp: "/api/login" in resp.url) as resp_info:
    page.locator(".btn").click()
response = resp_info.value
assert response.status == 200
assert "token" in response.json()
```

### 8.3 Trace Viewer —— 超强调试工具

> 你的 Selenium 流程中：失败只知道报错信息，很难回溯操作过程。
> Playwright Trace Viewer 能**回放整个测试过程**，包括截图、DOM 快照、网络请求、操作时间轴。

```python
# 步骤1：录制 trace
browser = p.chromium.launch()
context = browser.new_context()
# 开启 trace 录制
context.tracing.start(screenshots=True, snapshots=True)

page = context.new_page()
page.goto("https://hmshop-test.itheima.net")
page.fill("#username", "admin")
page.click(".btn")
# ... 测试操作 ...

# 步骤2：保存 trace
context.tracing.stop(path="trace.zip")
browser.close()

# 步骤3：在终端查看 trace
# 运行：playwright show-trace trace.zip
# 或者打开 https://trace.playwright.dev 上传 trace.zip
```

### 8.4 自动截图/录屏

```python
# 1. 失败自动截图（pytest 钩子自动实现）
def test_login(self, page):
    # ... 测试代码 ...
    try:
        assert "成功" in result
    except AssertionError:
        page.screenshot(path=f"./img/fail_{int(time.time())}.png")
        raise

# 2. 录制视频
context = browser.new_context(
    record_video_dir="./videos",       # 录屏保存目录
    record_video_size={"width": 1920, "height": 1080}
)
page = context.new_page()
# ... 执行测试 ...
context.close()  # 视频自动保存为 .webm 格式

# 3. 步骤截图（每个操作后截图，用于报告）
page.goto("...")
page.screenshot(path="img/step1_home.png")
page.fill("#username", "admin")
page.screenshot(path="img/step2_input.png")
```

### 8.5 Codegen —— 录制操作生成代码

```bash
# 直接在终端运行，打开浏览器和代码生成器
playwright codegen https://hmshop-test.itheima.net

# 你手动操作浏览器，Playwright 自动生成对应的 Python 代码！
# 适合：快速生成元素定位、新手学习、反驳"写自动化脚本太费时间"的质疑
```

### 8.6 并发测试

```python
# 配合 pytest-xdist 实现多浏览器并发执行
# 终端命令：
pytest -n 4  # 4个线程并发执行

# conftest.py 中确保 fixture 是隔离的（scope="function" 即可）
# Playwright 的 Context 机制天然支持并发，无需 Selenium Grid
```

---

## 9. ==Jenkins 持续集成（Playwright 版）:black_flag:==

### 9.1 Jenkins 构建配置

```yacas
构建步骤：
1. 拉取 Git 仓库代码
2. 安装依赖：pip install -r requirements.txt
3. 安装浏览器：playwright install chromium
4. 执行测试：pytest
5. 生成报告：python cmd_allure.py
6. 发布 allure 报告
```

### 9.2 Jenkins Execute Shell 命令

```bash
# Windows 构建命令
pip install playwright
playwright install chromium
pytest
python cmd_allure.py
```

### 9.3 Jenkins 关键配置差异

```yacas
与 Selenium 项目的主要区别：
1. 无需在 Jenkins 机器上下载 chromedriver.exe
2. 只需要在 Jenkins 机器上安装一次：playwright install chromium
3. 如果 Jenkins 运行在 Linux 服务端，headless 模式默认支持
4. Jenkins 项目配置中添加 --headless 参数：
   ── 在 conftest.py 中通过环境变量控制 headless：
       headless = os.getenv("HEADLESS", "False") == "True"
   ── Jenkins 构建时设置环境变量 HEADLESS=True
```

### 9.4 依赖文件 requirements.txt

```
playwright==1.48.0
pytest==8.3.0
allure-pytest==2.13.0
pytest-xdist==3.6.0
```

---

## 10. ==Playwright 应用注意事项（对照 Selenium 经验）:black_flag:==

### 10.1 元素定位不到问题排查

```yacas
1. locator 写法是否正确（打开浏览器F12验证选择器）
2. 元素是否在 iframe 中（使用 frame_locator 而非普通 locator）
3. 元素是否在 Shadow DOM 中（Playwright 的 locator 可以穿透 Shadow DOM）
   —— 这也是 Playwright 的一大优势！Selenium 需要 JS 才能操作 Shadow DOM
4. 是否设置了合理的超时时间（默认 30s，可通过 page.set_default_timeout() 调整）
5. 是否需要等待元素特定状态（wait_for_selector 的 state 参数）
```

### 10.2 稳定性建议

```yacas
1. 优先用 get_by_text/get_by_label/get_by_placeholder（最稳定）
2. 其次用 CSS 选择器（.class / #id / [attr=value]）
3. 最后用 XPath（复杂度高时使用）
4. 不要混用 page.locator 和 page.query_selector（后者不自动等待）
5. 每个测试用例用独立 Context，避免用例间数据污染
6. 使用 trace 而非截图来排查失败（trace 包含完整上下文）
```

### 10.3 Headless 模式

```yacas
# 本地调试：headless=False（看得到浏览器操作）
# Jenkins/服务器：headless=True（无 UI 运行）
browser = p.chromium.launch(headless=True)
```

### 10.4 与 Selenium 共存

```yacas
Playwright 和 Selenium 可以同时存在于一个项目中，互不影响。
如果你已有大量 Selenium 代码，可以：
  - 新模块用 Playwright 写
  - 老模块逐步迁移（因为 PO 模式封装好了，迁移成本很低）
  - 两者的 pytest fixture 可以并存，通过不同 conftest.py 隔离
```

---

## 11. ==完整示例：TPSHOP 登录→搜索→下单 业务流程:black_flag:==

```python
# scripts/test_business_flow.py
import pytest
from pages.page_login import LoginPage
from pages.page_shop import ShopPage
from pages.page_order import OrderPage


class TestBusinessFlow:

    def test_login_search_order(self, page):
        """核心业务流程：登录 → 搜索商品 → 加入购物车 → 下单"""
        # 1. 登录
        login_page = LoginPage(page)
        login_page.open_login_url()
        login_page.login("13800000001", "123456", "8888")
        assert "测试账号" in login_page.get_success_text()

        # 2. 搜索商品
        shop_page = ShopPage(page)
        shop_page.search_product("手机")
        assert shop_page.has_results()

        # 3. 加入购物车
        shop_page.add_first_item_to_cart()
        msg = page.locator(".success-msg").text_content()
        assert "加入成功" in msg

        # 4. 下单
        order_page = OrderPage(page)
        order_page.go_to_cart()
        order_page.submit_order()
        order_text = order_page.get_order_result()
        assert "下单成功" in order_text
```

---

## 12. Selenium → Playwright 迁移总结

| 迁移内容 | 关键变化 |
| :--- | :--- |
| **驱动管理** | 从手动 chromedriver → `playwright install` 自动 |
| **元素定位** | 从 `find_element(By.XXX, "value")` → `page.locator("selector")` |
| **等待机制** | 从三种等待方式 → 几乎不需要写等待 |
| **frame 切换** | 从手动 `switch_to.frame()` → `frame_locator()` 直接定位 |
| **多窗口** | 从句柄切换 → `expect_popup()` 或直接管理多个 page 对象 |
| **if/else 与 setTimeout** | 从 `time.sleep()` → auto-wait + `wait_for_selector()` |
| **po 模式** | 基本一致，只需改定位和操作方法 |
| **fixture** | 从 `webdriver.Chrome()` → `sync_playwright().chromium.launch()` |
| **jenkins** | 无需装 chromedriver，`playwright install` 即可 |
| **allure** | 用法完全一致，无变化 |
| **截图路径** | `driver.get_screenshot_as_file()` → `page.screenshot(path=)` |

---

> **学习建议**：
> 1. 先用 Playwright 写一个登录用例，感受"不用写等待"的快感
> 2. 按 PO 模式把你现有的 Selenium 项目用 Playwright 重写 2-3 个页面
> 3. 深入研究 Trace Viewer 和 Codegen，这两个工具能极大提升效率
> 4. Jenkins 配置和 allure 报告部分无需变化，直接复用
