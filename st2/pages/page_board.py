"""Mimo 项目看板页面对象 —— Playwright 版
所有关键定位器以类属性 + 方法参数形式暴露，可在测试用例中覆盖。
"""
from playwright.sync_api import Page
from base.base_page import BasePage
from config import BASE_URL


class ProjectBoardPage(BasePage):
    """Mimo 项目看板页面

    定位器参数命名规则：
      loc_xxx  —— 方法参数，传入后覆盖类默认值
      LOC_XXX —— 类级默认定位器，可直接修改
    """

    # ========== 类级默认定位器 ==========

    # 看板
    LOC_BOARD = "//div[contains(@class,'board')]"
    LOC_CREATE_BTN = "#create-task-btn"
    LOC_DIALOG = "//div[@role='dialog' and contains(@class,'el-dialog')]"
    LOC_DIALOG_FOOTER_PRIMARY = (
        "//div[contains(@class,'el-dialog__footer')]//button[contains(@class,'el-button--primary')]")
    LOC_DIALOG_FOOTER_CANCEL = (
        "//div[contains(@class,'el-dialog__footer')]//button[contains(.,'取消')]")

    # 视图切换
    LOC_BTN_KANBAN = "//button[contains(text(),'看板')]"
    LOC_BTN_BUGLIST = "//button[contains(text(),'缺陷列表')]"
    LOC_BTN_STORYMAP = "//button[contains(text(),'故事地图')]"
    LOC_BUG_TABLE = "//div[contains(@class,'bug-table-view')]"
    LOC_STORYMAP = "//div[contains(@class,'storymap')]"

    # 看板列
    LOC_COLUMNS = "//div[contains(@class,'board__column')]"
    LOC_COLUMN_BODY = "//div[contains(@class,'board__column-body')]"

    # 卡片
    LOC_STORY_CARD = "//div[contains(@class,'story-card')]"
    LOC_TASK_CARD = "//div[contains(@class,'task-card')]"
    LOC_BUG_CARD = "//div[contains(@class,'bug-card')]"
    LOC_ALL_CARDS = (
        "//div[contains(@class,'story-card') or contains(@class,'task-card') or contains(@class,'bug-card')]")

    # 类型筛选
    LOC_TYPE_FILTER_BTN = "//div[contains(@class,'board__type-filters')]//button"

    # 缺陷列表
    LOC_BUG_TABLE_ROW = "//div[contains(@class,'bug-table-view')]//tbody/tr"

    # 故事地图
    LOC_STORYMAP_EPIC = "//div[contains(@class,'storymap__epic')]"
    LOC_STORYMAP_CARD = "//div[contains(@class,'storymap__story-card')]"
    LOC_STORYMAP_SUBTASKS = "//div[contains(@class,'storymap__subtasks')]"

    # 空状态
    LOC_EMPTY = "//div[contains(@class,'el-empty')]"

    # 对话框通用字段
    LOC_TITLE_INPUT = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'标题')]/..//input")
    LOC_PRIORITY_SELECT = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'优先级')]/..//input")
    LOC_ASSIGNEE_SELECT = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'指派人')]/..//input")
    LOC_DROPDOWN_ITEM = "//li[contains(@class,'el-select-dropdown__item')]"

    # STORY 字段
    LOC_STORY_USER_ROLE = (
        "//div[contains(@class,'story-form__three-parts')]//span[contains(text(),'作为')]"
        "/following-sibling::*//input")
    LOC_STORY_USER_GOAL = (
        "//div[contains(@class,'story-form__three-parts')]//span[contains(text(),'希望')]"
        "/following-sibling::*//input")
    LOC_STORY_BUSINESS_VALUE = (
        "//div[contains(@class,'story-form__three-parts')]//span[contains(text(),'以便')]"
        "/following-sibling::*//input")
    LOC_STORY_EPIC = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'史诗')]/..//input")
    LOC_STORY_DESC = (
        "//div[contains(@class,'el-dialog')]//div[contains(@class,'story-form')]//textarea")

    # BUG 字段
    LOC_BUG_SEVERITY_SELECT = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'严重程度')]/..//input")
    LOC_BUG_STATUS_SELECT = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'缺陷状态')]/..//input")
    LOC_BUG_STEPS = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'复现步骤')]/..//textarea")
    LOC_BUG_EXPECTED = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'期望结果')]/..//textarea")
    LOC_BUG_ACTUAL = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'实际结果')]/..//textarea")
    LOC_BUG_FOUND_VER = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'发现版本')]/..//input")
    LOC_BUG_FIXED_VER = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'修复版本')]/..//input")
    LOC_BUG_ENV_OS = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'环境信息')]/..//input[@placeholder='操作系统']")
    LOC_BUG_ENV_BROWSER = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'环境信息')]/..//input[@placeholder='浏览器']")
    LOC_BUG_ENV_APPVER = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'环境信息')]/..//input[@placeholder='App 版本']")
    LOC_BUG_DESC = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'补充描述')]/..//textarea")

    # TASK 字段
    LOC_TASK_DESC = (
        "//div[contains(@class,'el-dialog')]//div[contains(@class,'task-form')]//textarea")
    LOC_TASK_PARENT_STORY = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'所属故事')]/..//input")

    # 标签
    LOC_LABEL_INPUT = (
        "//div[contains(@class,'el-dialog')]//label[contains(text(),'标签')]/..//input")

    # 类型选择（创建时）
    LOC_TYPE_BTN = "//label[contains(@class,'el-radio-button')]"

    def __init__(self, page: Page):
        super().__init__(page)

    # ========== 看板可见性 ==========

    def is_board_visible(self, loc: str = None) -> bool:
        return self.is_visible(loc or self.LOC_BOARD)

    def is_empty_state(self, loc: str = None) -> bool:
        return self.is_visible(loc or self.LOC_EMPTY)

    # ========== 视图切换 ==========

    def switch_to_kanban_view(self, loc: str = None):
        self.click(loc or self.LOC_BTN_KANBAN)

    def switch_to_buglist_view(self, loc: str = None):
        self.click(loc or self.LOC_BTN_BUGLIST)

    def switch_to_storymap_view(self, loc: str = None):
        self.click(loc or self.LOC_BTN_STORYMAP)

    def is_bug_table_visible(self, loc: str = None) -> bool:
        return self.is_visible(loc or self.LOC_BUG_TABLE)

    def is_storymap_visible(self, loc: str = None) -> bool:
        return self.is_visible(loc or self.LOC_STORYMAP)

    # ========== 看板列 ==========

    def get_board_columns(self, loc: str = None):
        return self.page.locator(loc or self.LOC_COLUMNS)

    def get_column_body(self, index: int = 0, loc: str = None):
        return self.page.locator(loc or self.LOC_COLUMN_BODY).nth(index)

    # ========== 卡片 ==========

    def get_story_cards(self, loc: str = None):
        return self.page.locator(loc or self.LOC_STORY_CARD)

    def get_task_cards(self, loc: str = None):
        return self.page.locator(loc or self.LOC_TASK_CARD)

    def get_bug_cards(self, loc: str = None):
        return self.page.locator(loc or self.LOC_BUG_CARD)

    def get_all_cards(self, loc: str = None):
        return self.page.locator(loc or self.LOC_ALL_CARDS)

    def get_card_count(self) -> int:
        return self.get_all_cards().count()

    # ========== 拖拽 ==========

    def drag_card_to_column(self, card_index: int, column_index: int):
        card = self.get_all_cards().nth(card_index)
        col = self.get_column_body(column_index)
        card.drag_to(col)

    # ========== 创建按钮 & 对话框 ==========

    def click_create_issue(self, loc: str = None):
        self.click(loc or self.LOC_CREATE_BTN)

    def is_dialog_visible(self, loc: str = None) -> bool:
        return self.is_visible(loc or self.LOC_DIALOG)

    def select_issue_type(self, issue_type: str, loc: str = None):
        """issue_type: 'STORY'|'TASK'|'BUG'"""
        text = {"STORY": "故事", "TASK": "任务", "BUG": "缺陷"}[issue_type]
        self.click(f"{loc or self.LOC_TYPE_BTN}[contains(.,'{text}')]")

    def click_save_create(self, loc: str = None):
        self.click(loc or self.LOC_DIALOG_FOOTER_PRIMARY)

    def click_cancel(self, loc: str = None):
        self.click(loc or self.LOC_DIALOG_FOOTER_CANCEL)

    # ========== 通用字段 ==========

    def fill_title(self, title: str, loc: str = None):
        self.fill(loc or self.LOC_TITLE_INPUT, title)

    def select_priority(self, label: str, loc_select: str = None, loc_item: str = None):
        self.click(loc_select or self.LOC_PRIORITY_SELECT)
        self.click(f"{loc_item or self.LOC_DROPDOWN_ITEM}[contains(.,'{label}')]")

    def select_assignee(self, username: str, loc_select: str = None, loc_item: str = None):
        self.click(loc_select or self.LOC_ASSIGNEE_SELECT)
        self.click(f"{loc_item or self.LOC_DROPDOWN_ITEM}[contains(.,'{username}')]")

    def fill_label(self, text: str, loc: str = None):
        inp = self.page.locator(loc or self.LOC_LABEL_INPUT)
        inp.fill(text)
        inp.press("Enter")

    # ========== STORY 专属 ==========

    def fill_user_role(self, role: str, loc: str = None):
        self.fill(loc or self.LOC_STORY_USER_ROLE, role)

    def fill_user_goal(self, goal: str, loc: str = None):
        self.fill(loc or self.LOC_STORY_USER_GOAL, goal)

    def fill_business_value(self, value: str, loc: str = None):
        self.fill(loc or self.LOC_STORY_BUSINESS_VALUE, value)

    def fill_epic(self, epic: str, loc: str = None):
        self.fill(loc or self.LOC_STORY_EPIC, epic)

    def fill_story_description(self, desc: str, loc: str = None):
        self.fill(loc or self.LOC_STORY_DESC, desc)

    # ========== BUG 专属 ==========

    def select_bug_severity(self, label: str, loc_select: str = None, loc_item: str = None):
        self.click(loc_select or self.LOC_BUG_SEVERITY_SELECT)
        self.click(f"{loc_item or self.LOC_DROPDOWN_ITEM}[contains(.,'{label}')]")

    def select_bug_status(self, label: str, loc_select: str = None, loc_item: str = None):
        self.click(loc_select or self.LOC_BUG_STATUS_SELECT)
        self.click(f"{loc_item or self.LOC_DROPDOWN_ITEM}[contains(.,'{label}')]")

    def fill_steps_to_repro(self, steps: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_STEPS, steps)

    def fill_expected_result(self, text: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_EXPECTED, text)

    def fill_actual_result(self, text: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_ACTUAL, text)

    def fill_found_version(self, version: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_FOUND_VER, version)

    def fill_fixed_version(self, version: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_FIXED_VER, version)

    def fill_bug_env_os(self, os: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_ENV_OS, os)

    def fill_bug_env_browser(self, browser: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_ENV_BROWSER, browser)

    def fill_bug_env_appver(self, version: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_ENV_APPVER, version)

    def fill_bug_description(self, desc: str, loc: str = None):
        self.fill(loc or self.LOC_BUG_DESC, desc)

    # ========== TASK 专属 ==========

    def fill_task_description(self, desc: str, loc: str = None):
        self.fill(loc or self.LOC_TASK_DESC, desc)

    def select_parent_story(self, keyword: str, loc_select: str = None, loc_item: str = None):
        self.click(loc_select or self.LOC_TASK_PARENT_STORY)
        self.click(f"{loc_item or self.LOC_DROPDOWN_ITEM}[contains(.,'{keyword}')]")

    # ========== 缺陷列表 ==========

    def get_bug_table_rows(self, loc: str = None):
        return self.page.locator(loc or self.LOC_BUG_TABLE_ROW)

    def click_bug_table_row(self, index: int = 0, loc: str = None):
        rows = self.get_bug_table_rows(loc)
        if rows.count() > index:
            rows.nth(index).click()

    # ========== 故事地图 ==========

    def get_epic_groups(self, loc: str = None):
        return self.page.locator(loc or self.LOC_STORYMAP_EPIC)

    def click_story_in_map(self, index: int = 0, loc: str = None):
        self.page.locator(loc or self.LOC_STORYMAP_CARD).nth(index).click()

    def are_subtasks_expanded(self, loc: str = None) -> bool:
        return self.is_visible(loc or self.LOC_STORYMAP_SUBTASKS)

    # ========== 类型筛选 ==========

    def click_type_filter(self, filter_text: str, loc: str = None):
        self.click(f"{loc or self.LOC_TYPE_FILTER_BTN}[contains(.,'{filter_text}')]")
