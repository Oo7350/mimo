"""conftest.py —— Playwright fixture 管理前后置 + API fixtures

对照 Selenium st/conftest.py：
  - webdriver.Edge(service=...) + 手动驱动管理 → sync_playwright().chromium.launch()
  - yield driver → yield page（使用 Context 隔离每个用例）
  - driver.quit() → context.close() + browser.close()
"""
import pytest
from playwright.sync_api import sync_playwright, Browser, Page
import os
from config import HEADLESS, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, API_BASE_URL, TEST_ACCOUNTS
from api.mimo_api_client import MimoAPIClient


@pytest.fixture(scope="function")
def browser():
    """每个测试用例独立一个浏览器实例"""
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=HEADLESS,
            args=["--start-maximized"]
        )
        yield browser
        browser.close()


@pytest.fixture(scope="function")
def page(browser: Browser):
    """每个测试用例独立页面（独立 Context，隔离 Cookie/Storage）"""
    context = browser.new_context(
        viewport={"width": VIEWPORT_WIDTH, "height": VIEWPORT_HEIGHT},
        locale="zh-CN"
    )
    page = context.new_page()
    page.set_default_timeout(10000)
    yield page
    context.close()


@pytest.fixture(scope="function")
def logged_in_page(browser: Browser):
    """加载已保存登录态的页面（跳过登录步骤）"""
    storage_file = os.path.join(os.path.dirname(__file__), "login_state.json")
    if os.path.exists(storage_file):
        context = browser.new_context(
            storage_state=storage_file,
            viewport={"width": VIEWPORT_WIDTH, "height": VIEWPORT_HEIGHT}
        )
    else:
        context = browser.new_context(
            viewport={"width": VIEWPORT_WIDTH, "height": VIEWPORT_HEIGHT},
            locale="zh-CN"
        )
    page = context.new_page()
    page.set_default_timeout(10000)
    yield page
    context.close()


@pytest.fixture(scope="session", autouse=True)
def session_setup():
    """全局前置：整个测试 session 执行一次"""
    print("\n===== 开始执行 Playwright 自动化测试（Mimo）=====")
    yield
    print("\n===== Playwright 测试结束 =====")


# ==================== API 测试 fixtures ====================

@pytest.fixture(scope="function")
def api_client():
    """API 客户端 —— 未登录状态（公开接口 / 认证失败测试）"""
    client = MimoAPIClient(base_url=API_BASE_URL)
    yield client
    client.session.close()


@pytest.fixture(scope="function")
def api_client_admin():
    """API 客户端 —— 已登录管理员"""
    client = MimoAPIClient(base_url=API_BASE_URL)
    client.login(
        TEST_ACCOUNTS["admin"]["username"],
        TEST_ACCOUNTS["admin"]["password"]
    )
    yield client
    client.session.close()


@pytest.fixture(scope="function")
def api_client_member():
    """API 客户端 —— 已登录普通成员（zhangsan）"""
    client = MimoAPIClient(base_url=API_BASE_URL)
    client.login(
        TEST_ACCOUNTS["zhangsan"]["username"],
        TEST_ACCOUNTS["zhangsan"]["password"]
    )
    yield client
    client.session.close()


@pytest.fixture(scope="function")
def api_client_member2():
    """API 客户端 —— 已登录普通成员（lisi）"""
    client = MimoAPIClient(base_url=API_BASE_URL)
    client.login(
        TEST_ACCOUNTS["lisi"]["username"],
        TEST_ACCOUNTS["lisi"]["password"]
    )
    yield client
    client.session.close()
