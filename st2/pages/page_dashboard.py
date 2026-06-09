"""Mimo 工作台 / 首页页面对象 —— Playwright 版"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class DashboardPage(BasePage):
    """Mimo 工作台（登录后首页）"""

    LOC_H2 = "//h2"
    LOC_MENU_ITEM = "//li[contains(@class,'el-menu-item')]"
    LOC_PROJECT_CARD = "//div[contains(@class,'el-card')]"
    LOC_USER_DROPDOWN = "//span[contains(@class,'el-dropdown')]"
    LOC_LOGOUT = "//li[contains(text(),'退出登录')]"

    def __init__(self, page: Page):
        super().__init__(page)
        self.url = BASE_URL

    def open_dashboard(self):
        self.open_url(self.url)

    def get_dashboard_title(self) -> str:
        return self.get_text(self.LOC_H2)

    def is_dashboard_visible(self) -> bool:
        return self.is_visible(self.LOC_H2)

    def click_menu(self, menu_text: str, loc_menu: str = None):
        self.click(f"{loc_menu or self.LOC_MENU_ITEM}[contains(.,'{menu_text}')]")

    def get_user_name_in_header(self, loc: str = None) -> str:
        return self.get_text(loc or self.LOC_USER_DROPDOWN)

    def logout(self):
        self.click(self.LOC_USER_DROPDOWN)
        self.page.wait_for_timeout(500)
        self.click(self.LOC_LOGOUT)

    def navigate_to_project_list(self):
        self.click_menu("项目列表")
        self.page.wait_for_timeout(500)

    def click_first_project(self, loc_card: str = None) -> bool:
        card = self.page.locator(loc_card or self.LOC_PROJECT_CARD).first
        if card.is_visible():
            card.click()
            self.page.wait_for_timeout(500)
            return True
        return False

    def get_project_cards(self, loc: str = None):
        return self.page.locator(loc or self.LOC_PROJECT_CARD)
