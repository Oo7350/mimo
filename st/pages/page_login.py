from selenium.webdriver.common.by import By

from base.page_base import BasePage
from config import BASE_URL


class Login(BasePage):
    """登录页面"""
    # 定位元素
    __url=BASE_URL
    __username_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[1]/div/div/div/input')
    __password_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[2]/div/div/div/input')
    __login_btn_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[3]/div/button')
    __result=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[1]/h2')
    def open_url(self):
        """打开登录页面"""
        self.driver.get(self.__url)

    def input_username(self, username):
        """输入用户名"""
        self.fd_element(self.__username_loc).send_keys(username)

    def input_password(self, password):
        """输入密码"""
        self.fd_element(self.__password_loc).send_keys(password)

    def click_login_btn(self):
        """点击登录按钮"""
        self.fd_element(self.__login_btn_loc).click()

    def login(self, username, password):
        """登录操作"""
        self.open_url()
        self.input_username(username)
        self.input_password(password)
        self.click_login_btn()

    def get_result(self):
        """获取登录结果"""
        return self.fd_element(self.__result).text