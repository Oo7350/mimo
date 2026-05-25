"""Mimo 登录功能测试 —— Playwright 版

对照 Selenium st/scripts/test_01_login.py：
  - setup_method / teardown_method 不再需要
  - 使用 conftest.py 中的 page fixture 管理生命周期
  - 无需手动 DriverTools.get_driver() / quit_driver()
"""
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

        try:
            login_page.open_login_page()
            login_page.login(username, password)
            page.wait_for_timeout(1000)

            if expect_status == 200:
                # 预期成功 —— 断言跳转到了主页
                assert "/login" not in page.url, f"登录未跳转，当前URL: {page.url}"
                current_title = login_page.get_welcome_text()
                GetLog.get_log().info(f"登录成功 —— 用户名：{username}，页面标题：{current_title}")
            else:
                # 预期失败 —— 断言仍在登录页或出现错误提示
                error_text = login_page.get_error_text()
                GetLog.get_log().info(f"登录失败 —— 用户名：{username}，错误信息：{error_text}")
                assert expect_msg in error_text or "/login" in page.url, \
                    f"预期包含'{expect_msg}'，实际错误'{error_text}'"

        except Exception as e:
            login_page.screenshot(f"./img/login_fail_{username}.png")
            GetLog.get_log().error(f"登录用例异常：{e}")
            raise

    def test_login_success(self, page):
        """登录成功 —— 正向用例"""
        login_page = LoginPage(page)
        login_page.login("admin", "123456")
        page.wait_for_timeout(1000)
        # 断言跳转到了工作台
        assert "/login" not in page.url, "登录后应跳转到主页"
        assert login_page.is_visible("h2"), "工作台标题应可见"

    def test_login_fail_wrong_password(self, page):
        """登录失败：密码错误"""
        login_page = LoginPage(page)
        login_page.login("admin", "wrongpassword")
        page.wait_for_timeout(1000)
        error = login_page.get_error_text()
        assert "密码" in error or "login" in page.url, "应提示密码错误或停留在登录页"
