"""Mimo 项目看板页面对象 —— Playwright 版"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class ProjectBoardPage(BasePage):
    """Mimo 项目看板页面"""

    def __init__(self, page: Page):
        super().__init__(page)

    def is_board_visible(self) -> bool:
        """判断看板是否可见"""
        return self.is_visible(".board-container") or self.is_visible("[class*='board']")

    def get_board_columns(self):
        """获取所有面板列"""
        return self.page.locator(".board-column, [class*='column']")

    def click_create_issue(self):
        """点击创建 Issue 按钮"""
        self.click("button:has-text('创建')")

    def drag_issue(self, source_selector: str, target_column_selector: str):
        """拖拽 Issue 到目标列"""
        self.page.locator(source_selector).drag_to(
            self.page.locator(target_column_selector)
        )

    def get_issue_count(self) -> int:
        """获取当前看板 Issue 数量"""
        return self.page.locator(".issue-card, [class*='issue']").count()
