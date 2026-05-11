"""
E2E 测试 - 用户登录
启动方式: pytest tests/e2e/test_login.py -v --html=report.html
"""
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


class TestLogin:

    def test_login_page_loads(self, driver, base_url):
        """验证登录页正常加载"""
        driver.get(f"{base_url}/login")
        assert "Mimo" in driver.title

    def test_login_empty_form(self, driver, base_url):
        """验证空表单提交校验"""
        driver.get(f"{base_url}/login")
        # 直接点击登录按钮
        driver.find_element(By.XPATH, "//button[@type='button']").click()
        # 应显示校验错误提示
        error_msgs = driver.find_elements(By.CLASS_NAME, "el-form-item__error")
        assert len(error_msgs) >= 2

    def test_login_success(self, driver, base_url):
        """验证正常登录流程（需预置测试用户）"""
        driver.get(f"{base_url}/login")
        # 填入测试账号
        username_input = driver.find_element(By.XPATH, "//input[@placeholder='请输入用户名']")
        password_input = driver.find_element(By.XPATH, "//input[@placeholder='请输入密码']")
        username_input.send_keys("testuser")
        password_input.send_keys("test123456")
        # 点击登录
        driver.find_element(By.XPATH, "//button[contains(.,'登 录')]").click()
        # 等待跳转到主页
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CLASS_NAME, "mimo-header"))
        )
        # 验证顶栏存在
        header = driver.find_element(By.CLASS_NAME, "mimo-header")
        assert "Mimo" in header.text

    def test_navigate_to_register(self, driver, base_url):
        """验证跳转到注册页"""
        driver.get(f"{base_url}/login")
        driver.find_element(By.LINK_TEXT, "立即注册").click()
        WebDriverWait(driver, 5).until(lambda d: "/register" in d.current_url)
        assert "注册" in driver.page_source
