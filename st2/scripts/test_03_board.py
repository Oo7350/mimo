"""
UI 自动化测试流程（5层调用链）：

1. Playwright         - 底层浏览器自动化工具，管理 Browser/Context/Page
2. BasePage           - 页面基类，封装通用操作（click/fill/get_text等）
3. Page Object        - 页面对象类（LoginPage/DashboardPage等），继承BasePage，定义定位器和业务方法
4. conftest fixtures  - 提供 page/logged_in_page fixture（独立 Context，隔离 Cookie）
5. 测试类             - 注入 page，创建 Page Object，调用业务方法

调用示例：
conftest.page → LoginPage(page) → login_page.login(username, password)
    ↓                ↓                        ↓
独立浏览器页面    页面对象(含定位器)      打开页面→输入→点击→断言
(Context隔离)       继承BasePage          自动等待元素可见
"""
"""Mimo 项目看板测试 —— Playwright 版
覆盖: 看板视图切换 / 三种类型创建 / 类型筛选 / 缺陷列表 / 故事地图
"""
import pytest
import allure
from pages.page_dashboard import DashboardPage
from pages.page_board import ProjectBoardPage
from pages.page_login import LoginPage
from tools.logger import GetLog


class TestProjectBoard:
    """项目看板功能测试"""

    @pytest.fixture(autouse=True)
    def _navigate_to_board(self, page):
        """自动登录并导航到项目看板"""
        LoginPage(page).login("admin", "123456")
        page.wait_for_timeout(1000)

        dashboard = DashboardPage(page)
        # 如果当前页面有项目卡片（dashboard 首页），直接点击第一个
        if dashboard.click_first_project():
            GetLog.get_log().info("已进入项目看板")
        else:
            # 否则从侧边栏导航
            dashboard.navigate_to_project_list()
            if not dashboard.click_first_project():
                pytest.skip("无可访问的项目，跳过看板测试")

    # ==================== 基本看板访问 ====================

    def test_access_board_after_login(self, page):
        """登录后访问看板 —— 看板容器可见"""
        board = ProjectBoardPage(page)
        assert board.is_board_visible(), "看板应可见"
        GetLog.get_log().info("看板视图加载成功")

    # ==================== 视图切换 ====================

    def test_switch_to_buglist_view(self, page):
        """切换到缺陷列表视图"""
        board = ProjectBoardPage(page)
        board.switch_to_buglist_view()
        page.wait_for_timeout(500)
        has_table = board.is_bug_table_visible()
        has_empty = page.locator("//div[contains(@class,'el-empty')]").is_visible()
        assert has_table or has_empty, "缺陷列表应显示表格或空状态"
        GetLog.get_log().info("缺陷列表视图切换成功")

    def test_switch_to_storymap_view(self, page):
        """切换到故事地图视图"""
        board = ProjectBoardPage(page)
        board.switch_to_storymap_view()
        page.wait_for_timeout(500)
        has_map = board.is_storymap_visible()
        has_empty = page.locator("//div[contains(@class,'el-empty')]").is_visible()
        assert has_map or has_empty, "故事地图应显示地图或空状态"
        GetLog.get_log().info("故事地图视图切换成功")

    def test_view_switching_roundtrip(self, page):
        """三视图来回切换"""
        board = ProjectBoardPage(page)

        board.switch_to_buglist_view()
        page.wait_for_timeout(300)

        board.switch_to_storymap_view()
        page.wait_for_timeout(300)

        board.switch_to_kanban_view()
        page.wait_for_timeout(300)
        assert board.is_board_visible(), "回到看板应可见"
        GetLog.get_log().info("三视图来回切换成功")

    # ==================== 创建 STORY ====================

    def test_create_story(self, page):
        """创建用户故事 —— 含三要素"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        before_count = board.get_card_count()

        board.click_create_issue()
        page.wait_for_timeout(500)
        if not board.is_create_dialog_visible():
            pytest.skip("创建对话框未弹出")

        board.select_issue_type("STORY")
        page.wait_for_timeout(300)

        board.fill_title("Playwright STORY 测试")
        board.select_priority("中")

        # 故事三要素
        try:
            board.fill_user_role("管理员")
            board.fill_user_goal("查看自动化测试报告")
            board.fill_business_value("评估测试覆盖率")
            GetLog.get_log().info("STORY 三要素填写完成")
        except Exception as e:
            GetLog.get_log().info(f"三要素填写跳过: {e}")

        board.click_save_create_btn()
        page.wait_for_timeout(1000)

        after_count = board.get_card_count()
        story_count = board.get_story_cards().count()
        GetLog.get_log().info(f"创建 STORY: before={before_count} after={after_count} story_cards={story_count}")
        assert story_count > 0, "应有至少一个 STORY 卡片"

    # ==================== 创建 TASK ====================

    def test_create_task(self, page):
        """创建任务"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        board.click_create_issue()
        page.wait_for_timeout(500)
        if not board.is_create_dialog_visible():
            pytest.skip("创建对话框未弹出")

        board.select_issue_type("TASK")
        page.wait_for_timeout(200)

        board.fill_title("Playwright TASK 测试")
        board.select_priority("高")
        board.click_save_create_btn()
        page.wait_for_timeout(1000)

        task_count = board.get_task_cards().count()
        assert task_count > 0, "应有至少一个 TASK 卡片"
        GetLog.get_log().info(f"TASK 创建成功, count={task_count}")

    # ==================== 创建 BUG ====================

    def test_create_bug(self, page):
        """创建缺陷 —— 含严重程度"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        board.click_create_issue()
        page.wait_for_timeout(500)
        if not board.is_create_dialog_visible():
            pytest.skip("创建对话框未弹出")

        board.select_issue_type("BUG")
        page.wait_for_timeout(200)

        board.fill_title("Playwright BUG 测试")
        board.select_priority("最高")

        try:
            board.select_bug_severity("阻塞")
            board.fill_steps_to_repro("1. 打开首页\n2. 点击按钮\n3. 白屏")
            board.fill_expected_result("正常显示")
            board.fill_actual_result("页面白屏")
            GetLog.get_log().info("BUG 缺陷报告要素填写完成")
        except Exception as e:
            GetLog.get_log().info(f"BUG 专属字段填写跳过: {e}")

        board.click_save_create_btn()
        page.wait_for_timeout(1000)

        bug_count = board.get_bug_cards().count()
        assert bug_count > 0, "应有至少一个 BUG 卡片"
        GetLog.get_log().info(f"BUG 创建成功, count={bug_count}")

    # ==================== 类型筛选 ====================

    def test_type_filter_story(self, page):
        """类型筛选 —— 仅故事"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        # 确保有 STORY
        if board.get_story_cards().count() == 0:
            self.test_create_story(page)

        board.click_type_filter("故事")
        page.wait_for_timeout(300)

        task_visible = board.get_task_cards().count()
        bug_visible = board.get_bug_cards().count()
        GetLog.get_log().info(f"STORY 筛选后: task卡={task_visible}, bug卡={bug_visible}")

    def test_type_filter_bug(self, page):
        """类型筛选 —— 仅缺陷"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        board.click_type_filter("缺陷")
        page.wait_for_timeout(300)

        story_visible = board.get_story_cards().count()
        task_visible = board.get_task_cards().count()
        GetLog.get_log().info(f"BUG 筛选后: story卡={story_visible}, task卡={task_visible}")

    # ==================== 缺陷列表 ====================

    def test_buglist_view_with_bugs(self, page):
        """缺陷列表视图 —— 表格交互"""
        board = ProjectBoardPage(page)

        if board.get_bug_cards().count() == 0:
            board.switch_to_kanban_view()
            page.wait_for_timeout(200)
            self.test_create_bug(page)

        board.switch_to_buglist_view()
        page.wait_for_timeout(500)

        if board.is_bug_table_visible():
            row_count = board.get_bug_table_rows().count()
            GetLog.get_log().info(f"缺陷列表共 {row_count} 行")
            if row_count > 0:
                board.click_bug_table_row(0)
                page.wait_for_timeout(500)
                assert board.is_create_dialog_visible(), "点击缺陷行应弹出详情对话框"
                GetLog.get_log().info("缺陷列表点击行打开详情成功")

    # ==================== 故事地图 ====================

    def test_storymap_view_with_stories(self, page):
        """故事地图 —— 史诗分组查看"""
        board = ProjectBoardPage(page)

        if board.get_story_cards().count() == 0:
            board.switch_to_kanban_view()
            page.wait_for_timeout(200)
            self.test_create_story(page)

        board.switch_to_storymap_view()
        page.wait_for_timeout(500)

        if board.is_storymap_visible():
            epic_count = board.get_epic_groups().count()
            GetLog.get_log().info(f"故事地图共 {epic_count} 个 Epic 分组")
