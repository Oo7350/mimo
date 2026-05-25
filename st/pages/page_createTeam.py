from selenium.webdriver.common.by import By

from base.page_base import BasePage



class CreateTeam(BasePage):
    """创建团队页面"""
    __left_menu_loc=(By.XPATH,'/html/body/div[1]/section/section/aside/ul/li[3]')
    __create_team_loc=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[1]/button')
    __team_name=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[3]/div/div/div/form/div[1]/div/div/div/input')
    __team_desc=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[3]/div/div/div/form/div[2]/div/div/textarea')
    __team_create_btn=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[3]/div/div/footer/button[2]')
    __result_toast=(By.XPATH,'/html/body/div[3]/p')

    def click_menu(self):
        """点击菜单"""
        self.fd_element(self.__left_menu_loc).click()

    def click_create_team(self):
        """点击创建团队"""
        self.fd_element(self.__create_team_loc).click()

    def input_team_name(self,team_name):
        """输入团队名称"""
        self.fd_element(self.__team_name).send_keys(team_name)

    def input_team_desc(self,team_desc):
        """输入团队描述"""
        self.fd_element(self.__team_desc).send_keys(team_desc)

    def click_team_create_btn(self):
        """点击创建团队按钮"""
        self.fd_element(self.__team_create_btn).click()

    def get_result(self):
        """获取创建结果"""
        return self.fd_element(self.__result_toast).text

    def create_team(self,team_name,team_desc):
        """创建团队"""
        self.click_menu()
        self.click_create_team()
        self.input_team_name(team_name)
        self.input_team_desc(team_desc)
        self.click_team_create_btn()

