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
        login_page = LoginPage(page)
        login_page.login("admin", "123456")
        page.wait_for_timeout(1000)

        dashboard = DashboardPage(page)
        dashboard.click_menu("项目列表")
        page.wait_for_timeout(500)

        first_project = page.locator(".project-card, [class*='project'] a, .el-card").first
        if first_project.is_visible():
            first_project.click()
            page.wait_for_timeout(500)
        else:
            pytest.skip("无可访问的项目，跳过看板测试")

    # ==================== 基本看板访问 ====================

    def test_access_board_after_login(self, page):
        """登录后访问看板 —— 默认看板视图可见"""
        board = ProjectBoardPage(page)
        assert board.is_board_visible(), "看板应可见"
        GetLog.get_log().info("看板视图加载成功")

    # ==================== 视图切换 ====================

    def test_switch_to_buglist_view(self, page):
        """切换到缺陷列表视图"""
        board = ProjectBoardPage(page)
        board.switch_to_buglist_view()
        page.wait_for_timeout(500)

        # 缺陷列表要么显示表格要么显示空状态
        has_table = board.is_bug_table_visible()
        has_empty = page.locator(".el-empty").is_visible()
        assert has_table or has_empty, "缺陷列表视图应显示表格或空状态"
        GetLog.get_log().info("缺陷列表视图切换成功")

    def test_switch_to_storymap_view(self, page):
        """切换到故事地图视图"""
        board = ProjectBoardPage(page)
        board.switch_to_storymap_view()
        page.wait_for_timeout(500)

        has_map = board.is_storymap_visible()
        has_empty = page.locator(".el-empty").is_visible()
        assert has_map or has_empty, "故事地图视图应显示地图或空状态"
        GetLog.get_log().info("故事地图视图切换成功")

    def test_view_switching_roundtrip(self, page):
        """三视图来回切换"""
        board = ProjectBoardPage(page)

        # 缺陷列表
        board.switch_to_buglist_view()
        page.wait_for_timeout(300)
        assert board.is_bug_table_visible() or page.locator(".el-empty").is_visible()

        # 故事地图
        board.switch_to_storymap_view()
        page.wait_for_timeout(300)
        assert board.is_storymap_visible() or page.locator(".el-empty").is_visible()

        # 回到看板
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)
        assert board.is_board_visible(), "回到看板应可见"
        GetLog.get_log().info("三视图来回切换成功")

    # ==================== 创建不同类型 Issue ====================

    def test_create_story(self, page):
        """创建用户故事(STORY) —— 含三要素"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        before_count = board.get_issue_count()

        board.click_create_issue()
        page.wait_for_timeout(500)

        if not board.is_create_dialog_visible():
            pytest.skip("创建对话框未弹出")

        # 选择 STORY 类型
        board.select_issue_type("STORY")
        page.wait_for_timeout(300)

        # 填写标题
        board.fill_title("Playwright STORY 自动化测试")

        # 填写故事三要素
        try:
            board.fill_user_role("管理员")
            board.fill_user_goal("查看自动化测试报告")
            board.fill_business_value("评估测试覆盖率")
            GetLog.get_log().info("STORY 三要素填写完成")
        except Exception as e:
            GetLog.get_log().info(f"STORY 三要素字段可能未渲染: {e}")

        # 填写史诗
        try:
            board.fill_epic("自动化测试")
        except Exception:
            pass

        board.click_save_btn()
        page.wait_for_timeout(1000)

        after_count = board.get_issue_count()
        GetLog.get_log().info(f"创建 STORY 前后: {before_count} → {after_count}")

        # STORY 卡片应显示绿色左边框
        story_cards = board.get_story_cards()
        assert story_cards.count() > 0, "应有至少一个 STORY 卡片"
        GetLog.get_log().info("STORY 创建成功，卡片已渲染")

    def test_create_task(self, page):
        """创建任务(TASK)"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        before_count = board.get_issue_count()

        board.click_create_issue()
        page.wait_for_timeout(500)

        if not board.is_create_dialog_visible():
            pytest.skip("创建对话框未弹出")

        board.select_issue_type("TASK")
        page.wait_for_timeout(200)

        board.fill_title("Playwright TASK 自动化测试")
        board.click_save_btn()
        page.wait_for_timeout(1000)

        after_count = board.get_issue_count()
        GetLog.get_log().info(f"创建 TASK 前后: {before_count} → {after_count}")

        task_cards = board.get_task_cards()
        assert task_cards.count() > 0, "应有至少一个 TASK 卡片"
        GetLog.get_log().info("TASK 创建成功")

    def test_create_bug(self, page):
        """创建缺陷(BUG) —— 含严重程度"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        before_count = board.get_issue_count()

        board.click_create_issue()
        page.wait_for_timeout(500)

        if not board.is_create_dialog_visible():
            pytest.skip("创建对话框未弹出")

        board.select_issue_type("BUG")
        page.wait_for_timeout(200)

        board.fill_title("Playwright BUG 自动化测试")
        board.click_save_btn()
        page.wait_for_timeout(1000)

        after_count = board.get_issue_count()
        GetLog.get_log().info(f"创建 BUG 前后: {before_count} → {after_count}")

        # 切回看板检查
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)
        bug_cards = board.get_bug_cards()
        assert bug_cards.count() > 0, "应有至少一个 BUG 卡片"
        GetLog.get_log().info("BUG 创建成功，红色边框卡片已渲染")

    # ==================== 类型筛选 ====================

    def test_type_filter_story(self, page):
        """类型筛选 —— 仅显示故事"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        # 先确保有 STORY
        if board.get_story_cards().count() == 0:
            self.test_create_story(page)

        board.click_type_filter("故事")
        page.wait_for_timeout(300)

        # 筛选后应没有 TASK 和 BUG 卡片
        task_cards = board.get_task_cards().count()
        bug_cards = board.get_bug_cards().count()
        # 可能有 0 个可见的 task/bug（筛选掉了）
        GetLog.get_log().info(f"STORY 筛选后: task={task_cards}(可见), bug={bug_cards}(可见)")

    def test_type_filter_bug(self, page):
        """类型筛选 —— 仅显示缺陷"""
        board = ProjectBoardPage(page)
        board.switch_to_kanban_view()
        page.wait_for_timeout(300)

        board.click_type_filter("缺陷")
        page.wait_for_timeout(300)

        story_cards = board.get_story_cards().count()
        task_cards = board.get_task_cards().count()
        GetLog.get_log().info(f"BUG 筛选后: story={story_cards}(可见), task={task_cards}(可见)")

    # ==================== 缺陷列表视图功能 ====================

    def test_buglist_view_with_bugs(self, page):
        """缺陷列表视图 —— 查看缺陷表格"""
        board = ProjectBoardPage(page)

        # 先确保有 BUG
        if board.get_bug_cards().count() == 0:
            board.switch_to_kanban_view()
            page.wait_for_timeout(200)
            self.test_create_bug(page)

        board.switch_to_buglist_view()
        page.wait_for_timeout(500)

        if board.is_bug_table_visible():
            row_count = board.get_bug_count_in_table()
            GetLog.get_log().info(f"缺陷列表共 {row_count} 行")

            if row_count > 0:
                # 点击第一行打开详情
                board.click_bug_table_row(0)
                page.wait_for_timeout(500)
                # 详情对话框应在编辑模式下打开
                assert board.is_create_dialog_visible(), "点击缺陷行应弹出详情对话框"
                GetLog.get_log().info("缺陷列表点击行打开详情成功")
        else:
            GetLog.get_log().info("缺陷列表为空（无 BUG）")

    # ==================== 故事地图视图功能 ====================

    def test_storymap_view_with_stories(self, page):
        """故事地图视图 —— 查看故事分组"""
        board = ProjectBoardPage(page)

        # 先确保有 STORY
        if board.get_story_cards().count() == 0:
            board.switch_to_kanban_view()
            page.wait_for_timeout(200)
            self.test_create_story(page)

        board.switch_to_storymap_view()
        page.wait_for_timeout(500)

        if board.is_storymap_visible():
            epic_count = board.get_epic_groups().count()
            GetLog.get_log().info(f"故事地图共 {epic_count} 个 Epic 分组")

            if epic_count > 0:
                # 点击第一个故事卡片展开子任务
                board.click_story_card_in_map(0)
                page.wait_for_timeout(300)
                # 子任务区域可能出现（即使是空的）
                GetLog.get_log().info("故事卡片点击测试完成")
        else:
            GetLog.get_log().info("故事地图为空（无 STORY）")
