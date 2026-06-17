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
"""Mimo 登录功能测试 —— Playwright 版"""
import pytest
import allure
from pages.page_login import LoginPage
from tools.read_data import read_json
from tools.logger import GetLog


class TestLogin:
    """登录功能测试类"""

    @pytest.mark.parametrize("username,password,expect_status,errno,expect_msg",
                             [(d["username"], d["password"], d["status"], d["errno"], d["errmsg"])
                              for d in read_json("login.json")])
    def test_login_parametrized(self, page, username, password, expect_status, errno, expect_msg):
        """登录测试：参数化覆盖成功/失败场景"""
        login_page = LoginPage(page)
        page.goto(login_page.url, wait_until="domcontentloaded")

        try:
            login_page.input_username(username)
            login_page.input_password(password)
            login_page.click_login_btn()

            # 等待页面响应——要么跳转走，要么留在登录页
            page.wait_for_load_state("networkidle")

            if expect_status == 200:
                assert "/login" not in page.url, (
                    f"登录未跳转，当前URL: {page.url}\n"
                    f"页面标题: {page.title()}"
                )
                GetLog.get_log().info(f"登录成功 —— 用户名：{username}")
            else:
                on_login = "/login" in page.url
                GetLog.get_log().info(f"登录失败 —— 用户名：{username}，仍在登录页: {on_login}")
                assert on_login, f"预期登录失败但跳转到了 {page.url}"

        except Exception as e:
            login_page.screenshot(f"./img/login_fail_{username}.png")
            GetLog.get_log().error(f"登录用例异常：{e}")
            raise

    def test_login_success(self, page):
        """登录成功 —— 正向用例"""
        login_page = LoginPage(page)
        page.goto(login_page.url, wait_until="domcontentloaded")
        login_page.input_username("zhangsan")
        login_page.input_password("123456")
        login_page.click_login_btn()
        page.wait_for_load_state("networkidle")

        assert "/login" not in page.url, (
            f"登录后应跳转，当前URL: {page.url}\n页面标题: {page.title()}"
        )

    def test_login_fail_wrong_password(self, page):
        """登录失败：密码错误"""
        login_page = LoginPage(page)
        page.goto(login_page.url, wait_until="domcontentloaded")
        login_page.input_username("admin")
        login_page.input_password("wrongpassword")
        login_page.click_login_btn()
        page.wait_for_load_state("networkidle")

        assert "/login" in page.url, (
            f"密码错误应停留在登录页，当前URL: {page.url}"
        )
