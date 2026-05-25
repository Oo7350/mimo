"""Mimo 工作台 / 首页页面对象 —— Playwright 版"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class DashboardPage(BasePage):
    """Mimo 工作台（登录后首页）"""

    def __init__(self, page: Page):
        super().__init__(page)
        self.url = BASE_URL

    def open_dashboard(self):
        """打开工作台"""
        self.open_url(self.url)

    def get_dashboard_title(self) -> str:
        """获取工作台标题"""
        return self.get_text("h2")

    def is_dashboard_visible(self) -> bool:
        """判断工作台是否可见"""
        return self.is_visible("h2")

    def click_menu(self, menu_name: str):
        """点击侧边栏菜单"""
        self.click(f".el-menu-item:has-text('{menu_name}')")

    def get_user_name_in_header(self) -> str:
        """获取右上角用户名"""
        return self.get_text(".el-dropdown")

    def logout(self):
        """退出登录"""
        # 点击右上角用户菜单
        self.click(".el-dropdown")
        self.page.wait_for_timeout(500)
        # 点击退出
        self.click(":has-text('退出登录')")
