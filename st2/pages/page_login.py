"""Mimo 登录页面对象 —— Playwright 版
所有定位器作为参数暴露，可在调用时传入覆盖默认值。
"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class LoginPage(BasePage):
    """Mimo 登录页面"""

    # ---- 默认定位器（可在调用时覆盖）----
    LOC_INPUT_USERNAME = "//input[@placeholder='请输入用户名']"
    LOC_INPUT_PASSWORD = "//input[@placeholder='请输入密码']"
    LOC_BTN_LOGIN = "/html/body/div[1]/div/div/form/div[3]/div/button"
    LOC_ERROR_MSG = "//div[contains(@class,'el-message--error')]//p"

    def __init__(self, page: Page):
        super().__init__(page)
        self.url = BASE_URL + "/login"

    def open_login_page(self, loc_username: str = None, loc_password: str = None, loc_btn: str = None):
        self.open_url(self.url)

    def input_username(self, username: str, loc: str = None):
        self.fill(loc or self.LOC_INPUT_USERNAME, username)

    def input_password(self, password: str, loc: str = None):
        self.fill(loc or self.LOC_INPUT_PASSWORD, password)

    def click_login_btn(self, loc: str = None):
        self.click(loc or self.LOC_BTN_LOGIN)

    def login(self, username: str = "admin", password: str = "123456",
              loc_username: str = None, loc_password: str = None, loc_btn: str = None):
        self.open_url(self.url)
        self.input_username(username, loc_username)
        self.input_password(password, loc_password)
        self.click_login_btn(loc_btn)

    def get_welcome_text(self) -> str:
        try:
            return self.get_text("//h2")
        except Exception:
            return ""

    def get_error_text(self, loc: str = None) -> str:
        return self.get_text(loc or self.LOC_ERROR_MSG)
