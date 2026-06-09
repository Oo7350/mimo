"""基础页面类 —— Playwright 版本

对照 Selenium 的 BasePage：
  - fd_element() → locator()（自动等待，无需显式 WebDriverWait）
  - driver.get_screenshot_as_file() → page.screenshot(path=)
  - 无需 driver.switch_to.frame() → 直接用 frame_locator()
"""
from playwright.sync_api import Page


class BasePage:
    """页面基类，封装 Playwright 通用操作方法"""

    def __init__(self, page: Page):
        self.page = page

    def open_url(self, url: str):
        """打开指定 URL"""
        self.page.goto(url, wait_until="networkidle")

    @staticmethod
    def _as_locator(selector: str):
        """Playwright 默认按 CSS 解析，xpath 需加前缀。自动判断。"""
        s = selector.strip()
        if s.startswith("/") or s.startswith("("):
            return f"xpath={s}"
        return s

    def click(self, selector: str):
        """点击元素（自动等待可点击，支持 css 和 xpath）"""
        self.page.locator(self._as_locator(selector)).click()

    def fill(self, selector: str, text: str):
        """输入文本（自动等待可见 + 清空 + 填充）"""
        loc = self._as_locator(selector)
        self.page.locator(loc).clear()
        self.page.locator(loc).fill(text)

    def get_text(self, selector: str) -> str:
        """获取元素文本内容"""
        return self.page.locator(self._as_locator(selector)).text_content().strip()

    def get_text_by_placeholder(self, placeholder: str) -> str:
        """通过 placeholder 属性获取元素文本"""
        return self.page.get_by_placeholder(placeholder).text_content()

    def is_visible(self, selector: str) -> bool:
        return self.page.locator(self._as_locator(selector)).is_visible()

    def is_enabled(self, selector: str) -> bool:
        return self.page.locator(self._as_locator(selector)).is_enabled()

    def get_attribute(self, selector: str, attr: str) -> str:
        return self.page.locator(self._as_locator(selector)).get_attribute(attr)

    def hover(self, selector: str):
        self.page.locator(self._as_locator(selector)).hover()

    def select_option(self, selector: str, value: str = None, label: str = None):
        """下拉框选择"""
        if value:
            self.page.locator(selector).select_option(value=value)
        elif label:
            self.page.locator(selector).select_option(label=label)

    def screenshot(self, path: str):
        """页面截图"""
        self.page.screenshot(path=path, full_page=True)

    def element_screenshot(self, selector: str, path: str):
        """元素截图"""
        self.page.locator(selector).screenshot(path=path)

    def wait_for_selector(self, selector: str, state: str = "visible"):
        """等待元素到达指定状态"""
        self.page.wait_for_selector(selector, state=state)

    def wait_for_timeout(self, ms: int):
        """强制等待（调试用，一般不使用）"""
        self.page.wait_for_timeout(ms)

    def scroll_into_view(self, selector: str):
        """滚动到元素可见"""
        self.page.locator(selector).scroll_into_view_if_needed()
