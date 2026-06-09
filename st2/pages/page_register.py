"""Mimo 注册页面对象 —— Playwright 版"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class RegisterPage(BasePage):
    """Mimo 注册页面"""

    LOC_INPUT_USERNAME = "//input[@placeholder='用户名']"
    LOC_INPUT_EMAIL = "//input[@placeholder='邮箱']"
    LOC_INPUT_PASSWORD = "//input[@type='password' and @placeholder='密码']"
    LOC_INPUT_CONFIRM = "//input[@placeholder='确认密码']"
    LOC_BTN_REGISTER = "//button[contains(text(),'注册')]"
    LOC_ERROR_MSG = "//div[contains(@class,'el-message--error')]//p"

    def __init__(self, page: Page):
        super().__init__(page)
        self.url = BASE_URL + "/register"

    def open_register_page(self):
        self.open_url(self.url)

    def input_username(self, username: str, loc: str = None):
        self.fill(loc or self.LOC_INPUT_USERNAME, username)

    def input_email(self, email: str, loc: str = None):
        self.fill(loc or self.LOC_INPUT_EMAIL, email)

    def input_password(self, password: str, loc: str = None):
        self.fill(loc or self.LOC_INPUT_PASSWORD, password)

    def input_confirm_password(self, password: str, loc: str = None):
        self.fill(loc or self.LOC_INPUT_CONFIRM, password)

    def click_register_btn(self, loc: str = None):
        self.click(loc or self.LOC_BTN_REGISTER)

    def register(self, username: str, email: str, password: str):
        self.open_register_page()
        self.input_username(username)
        self.input_email(email)
        self.input_password(password)
        self.input_confirm_password(password)
        self.click_register_btn()

    def get_register_result(self) -> str:
        return self.page.url

    def get_error_text(self, loc: str = None) -> str:
        return self.get_text(loc or self.LOC_ERROR_MSG)
