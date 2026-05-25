"""Mimo 注册页面对象 —— Playwright 版"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class RegisterPage(BasePage):
    """Mimo 注册页面"""

    def __init__(self, page: Page):
        super().__init__(page)
        self.url = BASE_URL + "/register"

    def open_register_page(self):
        """打开注册页面"""
        self.open_url(self.url)

    def input_username(self, username: str):
        """输入用户名"""
        self.fill("input[placeholder='用户名']", username)

    def input_email(self, email: str):
        """输入邮箱"""
        self.fill("input[placeholder='邮箱']", email)

    def input_password(self, password: str):
        """输入密码"""
        el_password = self.page.locator("input[placeholder='密码']")
        # 注册页有两个密码框，取第一个
        el_password.first.clear()
        el_password.first.fill(password)

    def input_confirm_password(self, password: str):
        """输入确认密码"""
        self.fill("input[placeholder='确认密码']", password)

    def click_register_btn(self):
        """点击注册按钮"""
        self.click("button:has-text('注册')")

    # ===== 业务方法 =====
    def register(self, username: str, email: str, password: str):
        """完整注册流程"""
        self.open_register_page()
        self.input_username(username)
        self.input_email(email)
        self.input_password(password)
        self.input_confirm_password(password)
        self.click_register_btn()

    def get_register_result(self) -> str:
        """获取注册结果——跳转到登录页即为成功"""
        return self.page.url
