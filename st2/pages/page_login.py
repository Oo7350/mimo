"""Mimo 登录页面对象 —— Playwright 版

对照 Selenium st/pages/page_login.py：
  - find_element(By.XPATH, ...) → page.locator("css-selector")
  - send_keys() → fill()（自动等待 + 清空 + 填充）
  - driver.get() → page.goto()
"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class LoginPage(BasePage):
    """Mimo 登录页面"""

    def __init__(self, page: Page):
        super().__init__(page)
        self.url = BASE_URL + "/login"

    def open_login_page(self):
        """打开登录页面"""
        self.open_url(self.url)

    def input_username(self, username: str):
        """输入用户名"""
        self.fill("input[placeholder='用户名']", username)

    def input_password(self, password: str):
        """输入密码"""
        self.fill("input[placeholder='密码']", password)

    def click_login_btn(self):
        """点击登录按钮"""
        self.click("button:has-text('登录')")

    # ===== 业务方法：封装完整登录流程 =====
    def login(self, username: str = "admin", password: str = "123456"):
        """完整登录流程（打开页面 + 输入 + 点击）"""
        self.open_login_page()
        self.input_username(username)
        self.input_password(password)
        self.click_login_btn()

    def get_welcome_text(self) -> str:
        """获取登录后工作台标题"""
        return self.get_text("h2")

    def get_error_text(self) -> str:
        """获取登录失败的错误提示"""
        return self.get_text(".el-message--error")
