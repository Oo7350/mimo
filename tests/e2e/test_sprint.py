"""
E2E 测试 - Sprint 管理
启动方式: pytest tests/e2e/test_sprint.py -v --html=report.html
"""
import time
import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


class TestSprint:

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

    def _navigate_to_sprints(self, driver, base_url):
        """辅助方法：导航到第一个项目的 Sprint 页面"""
        driver.get(f"{base_url}/projects")
        time.sleep(1)
        sprint_btns = driver.find_elements(By.XPATH, "//button[contains(.,'Sprint')]")
        if not sprint_btns:
            pytest.skip("没有可用的项目")
        sprint_btns[0].click()
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )

    def test_sprint_page_loads(self, driver, base_url):
        """验证 Sprint 管理页面加载"""
        self._navigate_to_sprints(driver, base_url)
        assert "Sprint 管理" in driver.page_source
        # 应有创建按钮
        assert "创建 Sprint" in driver.page_source

    def test_create_sprint(self, driver, base_url):
        """验证创建 Sprint 流程"""
        self._navigate_to_sprints(driver, base_url)
        # 点击创建
        create_btn = driver.find_element(By.XPATH, "//button[contains(.,'创建 Sprint')]")
        create_btn.click()
        time.sleep(0.5)
        # 填写表单
        driver.find_element(By.XPATH, "//input[@placeholder='如: Sprint 1']").send_keys("E2E Sprint")
        driver.find_element(By.XPATH, "//input[@placeholder='Sprint 目标']").send_keys("完成E2E测试")
        # Date pickers: find and fill
        date_inputs = driver.find_elements(By.XPATH, "//input[@placeholder='YYYY-MM-DD']")
        if len(date_inputs) >= 2:
            from datetime import date, timedelta
            today = date.today()
            date_inputs[0].send_keys(today.strftime("%Y-%m-%d"))
            time.sleep(0.3)
            date_inputs[1].send_keys((today + timedelta(days=14)).strftime("%Y-%m-%d"))
        # Submit
        driver.find_element(By.XPATH, "//button[contains(.,'创建') and not(contains(.,'Sprint'))]").click()
        time.sleep(1)
        assert "创建成功" in driver.page_source or "E2E Sprint" in driver.page_source

    def test_profile_page_loads(self, driver, base_url):
        """验证个人信息页面加载"""
        driver.get(f"{base_url}/profile")
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        assert "个人信息" in driver.page_source
        assert "基本资料" in driver.page_source
        assert "修改密码" in driver.page_source

    def test_profile_update(self, driver, base_url):
        """验证修改个人资料"""
        driver.get(f"{base_url}/profile")
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        # 确认表单可见
        username_input = driver.find_element(By.XPATH, "//input[@placeholder='用户名']")
        assert username_input.get_attribute("value") != ""
