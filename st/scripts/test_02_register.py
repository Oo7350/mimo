from pages.page_login import Login
from pages.page_register import Register
from tools import DriverTools


class TestRegister:
    def setup_method(self):
        dr = DriverTools.get_driver()
        self.register = Register(driver=dr)
        self.register.open_url()


    def teardown_method(self):
        """测试方法：清理"""
        #退出浏览器驱动
        DriverTools.quit_driver()

    def test_register(self):
        """测试方法：测试注册"""
        # 注册
        self.register.register("admin111111", "admin111112@qq.com","123456", "123456")
        # 断言
        assert   self.register.get_result() == "登 录"
