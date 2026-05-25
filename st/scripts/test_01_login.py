from pages.page_login import Login
from tools import DriverTools


class TestLogin:
    def setup_method(self):
        dr = DriverTools.get_driver()
        self.login = Login(driver=dr)
        self.login.open_url()

    def teardown_method(self):
        """测试方法：清理"""
        #退出浏览器驱动
        DriverTools.quit_driver()

    def test_login(self):
        """测试方法：测试登录"""
        # 登录
        self.login.login("admin", "123456")
        # 断言
        assert self.login.get_result() == "工作台"
