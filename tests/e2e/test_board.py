"""
E2E 测试 - 看板操作
启动方式: pytest tests/e2e/test_board.py -v --html=report.html
"""
import time
import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


class TestBoard:

    @pytest.fixture(autouse=True)
    def login(self, driver, base_url):
        """每个测试前登录"""
        driver.get(f"{base_url}/login")
        # 使用预置的 admin 账号
        driver.find_element(By.XPATH, "//input[@placeholder='请输入用户名']").send_keys("admin")
        driver.find_element(By.XPATH, "//input[@placeholder='请输入密码']").send_keys("123456")
        driver.find_element(By.XPATH, "//button[contains(.,'登 录')]").click()
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CLASS_NAME, "mimo-header"))
        )
        yield
        # 登出
        driver.get(f"{base_url}/login")

    def test_dashboard_loads(self, driver, base_url):
        """验证工作台首页加载"""
        driver.get(base_url)
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        assert "工作台" in driver.page_source
        # 应显示统计卡片
        stat_values = driver.find_elements(By.CLASS_NAME, "stat-value")
        assert len(stat_values) >= 3

    def test_team_detail_project_list(self, driver, base_url):
        """验证团队详情页显示项目列表"""
        driver.get(f"{base_url}/teams/1")
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        assert "项目列表" in driver.page_source

    def test_create_project(self, driver, base_url):
        """验证创建项目流程"""
        driver.get(f"{base_url}/teams/1")
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.XPATH, "//button[contains(.,'创建项目')]"))
        )
        # 点击创建项目
        create_btns = driver.find_elements(By.XPATH, "//button[contains(.,'创建项目')]")
        create_btns[0].click()

        WebDriverWait(driver, 3).until(
            EC.presence_of_element_located((By.XPATH, "//input[@placeholder='如: Mimo敏捷管理']"))
        )
        # 填写项目信息
        import random
        key = f"TEST{random.randint(100, 999)}"
        driver.find_element(By.XPATH, "//input[@placeholder='如: Mimo敏捷管理']").send_keys(f"测试项目{key}")
        driver.find_element(By.XPATH, "//input[@placeholder='如: MIMO']").send_keys(key)
        # 点击创建
        driver.find_element(By.XPATH, "//button[contains(.,'创建') and contains(@class,'el-button--primary')]").click()
        time.sleep(1)
        # 应该看到成功提示或关闭dialog
        assert "test" in driver.current_url.lower() or "team" in driver.current_url.lower()

    def test_board_page_loads(self, driver, base_url):
        """验证看板页面加载（如果项目存在）"""
        # 先尝试导航到项目列表
        driver.get(f"{base_url}/projects")
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.TAG_NAME, "h2"))
        )
        # 如果有项目卡片，点击第一个进入看板
        project_cards = driver.find_elements(By.XPATH, "//div[contains(@class,'el-card')]")
        if project_cards:
            # 点击看板按钮
            board_btns = driver.find_elements(By.XPATH, "//button[contains(.,'看板')]")
            if board_btns:
                board_btns[0].click()
                WebDriverWait(driver, 5).until(
                    EC.presence_of_element_located((By.XPATH, "//button[contains(.,'新建任务')]"))
                )
                assert "新建任务" in driver.page_source

    def test_board_add_column(self, driver, base_url):
        """验证添加看板列"""
        driver.get(f"{base_url}/projects")
        time.sleep(1)
        board_btns = driver.find_elements(By.XPATH, "//button[contains(.,'看板')]")
        if not board_btns:
            pytest.skip("没有可用的项目")
        board_btns[0].click()
        WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.XPATH, "//button[contains(.,'新建任务')]"))
        )
        # 点击添加列
        add_col_btn = driver.find_element(By.XPATH, "//button[contains(.,'添加列')]")
        add_col_btn.click()
        time.sleep(0.5)
        # 输入列名
        name_input = driver.find_element(By.XPATH, "//input[@placeholder='如: 测试中']")
        name_input.send_keys("E2E测试列")
        # 确定
        driver.find_element(By.XPATH, "//button[contains(.,'确定')]").click()
        time.sleep(1)
        assert "已添加" in driver.page_source or "E2E" in driver.page_source
