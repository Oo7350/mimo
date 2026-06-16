"""
UI 自动化测试流程（5层调用链）：

1. Playwright         - 底层浏览器自动化工具，管理 Browser/Context/Page
2. BasePage           - 页面基类，封装通用操作（click/fill/get_text等）
3. Page Object        - 页面对象类（LoginPage/DashboardPage等），继承BasePage，定义定位器和业务方法
4. conftest fixtures  - 提供 page/logged_in_page fixture（独立 Context，隔离 Cookie）
5. 测试类             - 注入 page，创建 Page Object，调用业务方法

调用示例：
conftest.page → LoginPage(page) → login_page.login(username, password)
    ↓                ↓                        ↓
独立浏览器页面    页面对象(含定位器)      打开页面→输入→点击→断言
(Context隔离)       继承BasePage          自动等待元素可见
"""
"""Mimo 注册功能测试 —— Playwright 版"""
import pytest
import allure
from pages.page_register import RegisterPage
from tools.logger import GetLog


class TestRegister:
    """注册功能测试类"""

    def test_register_success(self, page):
        """注册成功 —— 正向用例"""
        register_page = RegisterPage(page)
        register_page.register("testuser_pw", "test_pw@example.com", "123456")
        page.wait_for_timeout(1000)
        assert "/login" in page.url, f"注册成功后应跳转登录页，当前URL: {page.url}"
        GetLog.get_log().info("注册测试通过 —— 成功跳转到登录页")

    def test_register_duplicate_username(self, page):
        """注册失败：重复用户名"""
        register_page = RegisterPage(page)
        register_page.register("admin", "dup@example.com", "123456")
        page.wait_for_timeout(1000)
        error_visible = register_page.is_visible("//div[contains(@class,'el-message--error')]")
        assert error_visible or "/register" in page.url, \
            "重复用户名注册应提示错误或停留在注册页"
