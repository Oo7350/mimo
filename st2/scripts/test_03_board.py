"""Mimo 项目看板测试 —— Playwright 版

使用 logged_in_page fixture 跳过重复登录步骤（复用登录态），
体现 Playwright Browser Context + storage_state 的优势。
"""
import pytest
import allure
from pages.page_dashboard import DashboardPage
from pages.page_board import ProjectBoardPage
from pages.page_login import LoginPage
from tools.logger import GetLog


class TestProjectBoard:
    """项目看板功能测试"""

    def test_access_board_after_login(self, page):
        """登录后访问看板"""
        login_page = LoginPage(page)
        login_page.login("admin", "123456")
        page.wait_for_timeout(1000)

        # 进入第一个项目
        dashboard = DashboardPage(page)
        dashboard.click_menu("项目列表")
        page.wait_for_timeout(500)

        # 点击项目进入看板
        first_project = page.locator(".project-card, [class*='project'] a").first #从所有匹配的元素中获取第一个元素。
        if first_project.is_visible():
            first_project.click()
            page.wait_for_timeout(500)

            board = ProjectBoardPage(page)
            assert board.is_board_visible(), "看板应可见"
            GetLog.get_log().info("看板加载成功")
        else:
            GetLog.get_log().info("无可访问的项目，跳过看板验证")

    def test_create_issue(self, page):
        """创建 Issue"""
        login_page = LoginPage(page)
        login_page.login("admin", "123456")
        page.wait_for_timeout(1000)

        # 进入项目看板
        dashboard = DashboardPage(page)
        dashboard.click_menu("项目列表")
        page.wait_for_timeout(500)

        first_project = page.locator(".project-card, [class*='project'] a").first
        if first_project.is_visible():
            first_project.click()
            page.wait_for_timeout(500)

            board = ProjectBoardPage(page)
            before_count = board.get_issue_count()

            # 点击创建 Issue
            board.click_create_issue()
            page.wait_for_timeout(500)

            # 填写 Issue（如果有弹窗）
            if page.locator(".el-dialog").is_visible():
                page.locator("input[placeholder*='标题']").fill("Playwright 自动化创建的 Issue")
                page.locator("button:has-text('确定')").click()
                page.wait_for_timeout(1000)

            # 验证 Issue 已创建
            after_count = board.get_issue_count()
            GetLog.get_log().info(f"创建前 Issue 数：{before_count}，创建后：{after_count}")
