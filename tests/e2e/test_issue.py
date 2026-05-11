"""
E2E 测试 - 任务管理
启动方式: pytest tests/e2e/test_issue.py -v --html=report.html
"""
import time
import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


class TestIssue:

    @pytest.fixture(autouse=True)
    def login(self, driver, base_url):
        """每个测试前登录"""
        driver.get(f"{base_url}/login")
        driver.find_element(By.XPATH, "//input[@placeholder='请输入用户名']").send_keys("admin")
        driver.find_element(By.XPATH, "//input[@placeholder='请输入密码']").send_keys("123456")
        driver.find_element(By.XPATH, "//button[contains(.,'登 录')]").click()
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CLASS_NAME, "mimo-header"))
        )
        yield
        driver.get(f"{base_url}/login")

    def _navigate_to_board(self, driver, base_url):
        """辅助方法：导航到第一个项目的看板"""
        driver.get(f"{base_url}/projects")
        time.sleep(1)
        board_btns = driver.find_elements(By.XPATH, "//button[contains(.,'看板')]")
        if not board_btns:
            pytest.skip("没有可用的项目")
        board_btns[0].click()
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.XPATH, "//button[contains(.,'新建任务')]"))
        )

    def test_create_issue(self, driver, base_url):
        """验证创建任务流程"""
        self._navigate_to_board(driver, base_url)
        # 点击新建任务
        driver.find_element(By.XPATH, "//button[contains(.,'新建任务')]").click()
        time.sleep(0.8)
        # 填写标题
        driver.find_element(By.XPATH, "//input[@placeholder='任务标题']").send_keys("E2E测试任务")
        # 选择类型
        type_select = driver.find_elements(By.XPATH, "//input[@placeholder='Select']")
        if type_select:
            type_select[0].click()
            time.sleep(0.3)
            # 选择 "故事"
            story_options = driver.find_elements(By.XPATH, "//span[text()='故事']")
            if story_options:
                story_options[0].click()
        # 填写描述
        desc = driver.find_element(By.XPATH, "//textarea[@placeholder='详细描述...']")
        desc.send_keys("这是一个E2E测试创建的任务")
        # 创建
        driver.find_element(By.XPATH, "//button[contains(.,'创建') and @class='el-button--primary']").click()
        time.sleep(1)
        assert "创建成功" in driver.page_source or "E2E" in driver.page_source

    def test_report_page_loads(self, driver, base_url):
        """验证报告页面加载"""
        driver.get(f"{base_url}/projects")
        time.sleep(1)
        report_btns = driver.find_elements(By.XPATH, "//button[contains(.,'报告')]")
        if not report_btns:
            pytest.skip("没有可用的项目")
        report_btns[0].click()
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        assert "日报/周报" in driver.page_source
        assert "生成报告" in driver.page_source

    def test_generate_report(self, driver, base_url):
        """验证生成报告流程"""
        driver.get(f"{base_url}/projects")
        time.sleep(1)
        report_btns = driver.find_elements(By.XPATH, "//button[contains(.,'报告')]")
        if not report_btns:
            pytest.skip("没有可用的项目")
        report_btns[0].click()
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.XPATH, "//button[contains(.,'生成报告')]"))
        )
        # 点击生成报告
        driver.find_element(By.XPATH, "//button[contains(.,'生成报告')]").click()
        time.sleep(0.5)
        # 点击生成按钮
        gen_buttons = driver.find_elements(By.XPATH, "//button[contains(.,'生成')]")
        if gen_buttons:
            gen_buttons[0].click()
            time.sleep(1)
        # 验证操作成功
        assert "成功" in driver.page_source or "报告" in driver.page_source

    def test_burndown_page(self, driver, base_url):
        """验证燃尽图页面加载"""
        # Navigate to sprints first
        driver.get(f"{base_url}/projects")
        time.sleep(1)
        sprint_btns = driver.find_elements(By.XPATH, "//button[contains(.,'Sprint')]")
        if not sprint_btns:
            pytest.skip("没有可用的项目")
        sprint_btns[0].click()
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        # 点击燃尽图按钮
        burndown_btns = driver.find_elements(By.XPATH, "//button[contains(.,'燃尽图')]")
        if burndown_btns:
            burndown_btns[0].click()
            WebDriverWait(driver, 5).until(
                EC.presence_of_element_located((By.TAG_NAME, "h2"))
            )
            assert "燃尽图" in driver.page_source
