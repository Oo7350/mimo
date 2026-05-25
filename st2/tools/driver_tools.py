"""Playwright 驱动工具封装

对照 Selenium 版 DriverTools：
  - 不再需要 chromedriver.exe / msedgedriver.exe
  - playwright install 即可，内置浏览器
  - Context 隔离，无需手动切换窗口
"""
from playwright.sync_api import sync_playwright, Playwright, Browser, BrowserContext, Page
from config import HEADLESS, SLOW_MO, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, TIMEOUT

_playwright: Playwright = None
_browser: Browser = None
_context: BrowserContext = None
_page: Page = None

"""get_playwright() - 获取 Playwright 引擎实例
Playwright 是一个自动化测试工具，用来控制浏览器
使用单例模式，确保只启动一次
get_browser() - 启动并获取浏览器实例
基于 Playwright 启动 Chromium 浏览器
可以设置是否显示浏览器界面（headless）
也是单例模式，整个程序只打开一个浏览器
get_page() - 创建并获取页面实例
每次调用都会创建新的独立页面（类似新的标签页）
每个页面有独立的 Cookie 和存储空间，互不影响
设置页面大小和超时时间
返回的 page 对象可以用来访问网页、点击按钮等操作"""

def get_playwright() -> Playwright:
    """获取 Playwright 单例"""
    global _playwright
    # 如果 _playwright 还没有初始化（为 None）
    if _playwright is None:
        # 启动 Playwright 并赋值给全局变量 _playwright
        # sync_playwright().start() 会启动 Playwright 引擎，用于控制浏览器
        _playwright = sync_playwright().start()
    # 返回 Playwright 实例（确保整个程序只启动一次）
    return _playwright


def get_browser(headless: bool = HEADLESS) -> Browser:
    """获取 Browser 实例"""
    global _browser
    # 如果 _browser 还没有初始化（为 None）
    if _browser is None:
        # 先获取 Playwright 实例
        pw = get_playwright()
        # 通过 Playwright 启动 Chromium 浏览器
        _browser = pw.chromium.launch(
            headless=headless,       # 是否无头模式（不显示浏览器界面）
            slow_mo=SLOW_MO,         # 每个操作延迟的毫秒数，方便观察测试过程
            args=["--start-maximized"]  # 浏览器启动时最大化窗口
        )
    # 返回浏览器实例（确保整个程序只启动一个浏览器）
    return _browser


def get_page() -> Page:
    """获取 Page 实例（独立 Context，隔离 Cookie/Storage）"""
    global _context, _page
    # 先获取浏览器实例
    browser = get_browser()
    # 创建一个新的浏览器上下文（Context）
    # Context 类似于浏览器的无痕窗口，每个 Context 都有独立的 Cookie、LocalStorage 等
    _context = browser.new_context(
        viewport={"width": VIEWPORT_WIDTH, "height": VIEWPORT_HEIGHT},  # 设置页面视口大小
        locale="zh-CN"  # 设置语言环境为中文
    )
    # 在 Context 中创建一个新页面（Page）
    _page = _context.new_page()
    # 设置页面的默认超时时间（毫秒），超过这个时间操作会失败
    _page.set_default_timeout(TIMEOUT)
    # 返回页面实例，用于后续的页面操作
    return _page


def quit_driver():
    """关闭浏览器驱动"""
    global _browser, _context, _playwright
    if _context:
        _context.close()
        _context = None
    if _browser:
        _browser.close()
        _browser = None
    if _playwright:
        _playwright.stop()
        _playwright = None
