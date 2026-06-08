"""Mimo 项目看板页面对象 —— Playwright 版
覆盖: 看板视图 / 缺陷列表视图 / 故事地图视图 / 差异化卡片 / 创建对话框
"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class ProjectBoardPage(BasePage):
    """Mimo 项目看板页面"""

    def __init__(self, page: Page):
        super().__init__(page)

    # ---- 视图切换 ----

    def is_board_visible(self) -> bool:
        """判断看板是否可见"""
        return self.is_visible(".board") or self.is_visible("[class*='board']")

    def switch_to_kanban_view(self):
        """切换到看板视图"""
        self.click("button:has-text('看板')")

    def switch_to_buglist_view(self):
        """切换到缺陷列表视图"""
        self.click("button:has-text('缺陷列表')")

    def switch_to_storymap_view(self):
        """切换到故事地图视图"""
        self.click("button:has-text('故事地图')")

    def is_bug_table_visible(self) -> bool:
        """缺陷列表表格是否可见"""
        return self.is_visible(".el-table") or self.is_visible("[class*='bug-table']")

    def is_storymap_visible(self) -> bool:
        """故事地图是否可见"""
        return self.is_visible("[class*='storymap']")

    # ---- 看板列 ----

    def get_board_columns(self):
        """获取所有看板列"""
        return self.page.locator("[class*='board__column'], .board-column")

    # ---- Issue 卡片（按类型区分）----

    def get_story_cards(self):
        """获取所有 STORY 卡片"""
        return self.page.locator("[class*='story-card']")

    def get_task_cards(self):
        """获取所有 TASK 卡片"""
        return self.page.locator("[class*='task-card']")

    def get_bug_cards(self):
        """获取所有 BUG 卡片"""
        return self.page.locator("[class*='bug-card']")

    def get_issue_count(self) -> int:
        """获取当前看板 Issue 总数"""
        return (self.get_story_cards().count() +
                self.get_task_cards().count() +
                self.get_bug_cards().count())

    def get_bug_count_in_table(self) -> int:
        """获取缺陷列表表格行数"""
        return self.page.locator(".el-table__body tr").count()

    # ---- 创建 Issue ----

    def click_create_issue(self):
        """点击创建 Issue 按钮"""
        self.click("#create-task-btn")

    def select_issue_type(self, issue_type: str):
        """在创建对话框中选择类型: STORY / TASK / BUG"""
        type_map = {
            "STORY": "故事",
            "TASK": "任务",
            "BUG": "缺陷",
        }
        label = type_map.get(issue_type, issue_type)
        self.click(f"label:has-text('{label}')")

    def is_create_dialog_visible(self) -> bool:
        """创建对话框是否可见"""
        return self.is_visible(".el-dialog")

    # ---- 创建对话框 - 通用字段 ----

    def fill_title(self, title: str):
        """填写标题"""
        self.fill("input[placeholder*='标题']", title)

    def fill_description(self, desc: str):
        """填写描述"""
        self.fill("textarea[placeholder*='描述']", desc)

    def click_save_btn(self):
        """点击保存/创建按钮"""
        self.click(".el-dialog button:has-text('创建'), .el-dialog button:has-text('保存')")

    # ---- 创建对话框 - STORY 专属 ----

    def fill_user_role(self, role: str):
        """填写用户角色（作为...）"""
        self.page.locator("[class*='story-form'] input").nth(0).fill(role)

    def fill_user_goal(self, goal: str):
        """填写目标功能（我希望...）"""
        self.page.locator("[class*='story-form'] input").nth(1).fill(goal)

    def fill_business_value(self, value: str):
        """填写业务价值（以便...）"""
        self.page.locator("[class*='story-form'] input").nth(2).fill(value)

    def fill_epic(self, epic: str):
        """填写史诗"""
        self.fill("input[placeholder*='史诗']", epic)

    # ---- 创建对话框 - BUG 专属 ----

    def select_severity(self, severity_label: str):
        """选择严重程度"""
        self.page.locator(".el-form-item:has-text('严重程度') .el-select").click()
        self.page.locator(f".el-select-dropdown__item:has-text('{severity_label}')").click()

    def fill_steps_to_repro(self, steps: str):
        """填写复现步骤"""
        self.fill("textarea[placeholder*='复现步骤'], textarea[placeholder*='描述复现']", steps)

    def fill_expected_result(self, text: str):
        """填写期望结果"""
        self.page.locator(".el-form-item:has-text('期望结果') textarea").fill(text)

    def fill_actual_result(self, text: str):
        """填写实际结果"""
        self.page.locator(".el-form-item:has-text('实际结果') textarea").fill(text)

    def fill_found_version(self, version: str):
        """填写发现版本"""
        self.fill("input[placeholder*='发现版本'], .el-form-item:has-text('发现版本') input", version)

    # ---- 类型筛选 ----

    def click_type_filter(self, filter_type: str):
        """点击类型筛选按钮: 全部 / 故事 / 任务 / 缺陷"""
        self.click(f"button:has-text('{filter_type}')")

    # ---- 拖拽 ----

    def drag_issue(self, source_selector: str, target_column_selector: str):
        """拖拽 Issue 到目标列"""
        self.page.locator(source_selector).drag_to(
            self.page.locator(target_column_selector)
        )

    def drag_first_story_card_to_column(self, column_index: int):
        """将第一个 STORY 卡片拖到指定列"""
        card = self.get_story_cards().first
        col = self.get_board_columns().nth(column_index)
        card.drag_to(col)

    # ---- 缺陷列表视图 ----

    def click_bug_table_row(self, index: int = 0):
        """点击缺陷列表第 N 行"""
        self.page.locator(".el-table__body tr").nth(index).click()

    def filter_bugs_by_severity(self, severity_label: str):
        """按严重程度筛选缺陷"""
        self.page.locator("input[placeholder*='严重程度']").click()
        self.page.locator(f".el-select-dropdown__item:has-text('{severity_label}')").click()

    # ---- 故事地图视图 ----

    def get_epic_groups(self):
        """获取史诗分组数量"""
        return self.page.locator("[class*='storymap__epic']")

    def click_story_card_in_map(self, index: int = 0):
        """点击故事地图中的第 N 个故事卡片"""
        self.page.locator("[class*='storymap__story-card']").nth(index).click()

    def are_subtasks_expanded(self) -> bool:
        """子任务是否展开"""
        return self.is_visible("[class*='storymap__subtasks']")
