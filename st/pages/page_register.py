from selenium.webdriver.common.by import By

from base.page_base import BasePage
from config import BASE_URL


class Register(BasePage):
    """注册页面"""
    __url = BASE_URL+'/register'
    __username_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[1]/div/div[1]/div/input')
    __email_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[2]/div/div[1]/div/input')
    __password_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[3]/div/div/div/input')
    __rePassword_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[4]/div/div[1]/div/input')
    __register_btn_loc = (By.XPATH, '/html/body/div[1]/div/div/form/div[5]/div/button')
    __result=(By.XPATH,'/html/body/div[1]/div/div/form/div[3]/div/button')

    def open_url(self):
        """打开注册页面"""
        self.driver.get(self.__url)

    def input_username(self, username):
        """输入用户名"""
        self.fd_element(self.__username_loc).send_keys(username)

    def input_email(self, email):
        """输入邮箱"""
        self.fd_element(self.__email_loc).send_keys(email)

    def input_password(self, password):
        """输入密码"""
        self.fd_element(self.__password_loc).send_keys(password)

    def input_rePassword(self, rePassword):
        """输入确认密码"""
        self.fd_element(self.__rePassword_loc).send_keys(rePassword)

    def click_register_btn(self):
        """点击注册按钮"""
        self.fd_element(self.__register_btn_loc).click()

    def register(self, username, email, password, rePassword):
        """注册操作"""
        self.open_url()
        self.input_username(username)
        self.input_email(email)
        self.input_password(password)
        self.input_rePassword(rePassword)
        self.click_register_btn()

    def get_result(self):
        """获取注册结果"""
        return self.fd_element(self.__result).text
