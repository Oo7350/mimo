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
