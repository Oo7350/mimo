import time

from selenium.webdriver.common.by import By

from base.page_base import BasePage


class CreateProject(BasePage):
    """创建项目页面"""
    __left_menu_loc=(By.XPATH,'/html/body/div[1]/section/section/aside/ul/li[2]')
    __left_menu_loc2=(By.XPATH,'/html/body/div[1]/section/section/aside/ul/li[2]/ul/li')
    __create_project_loc=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[1]/button')
    __select_team_loc=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[2]/div[1]/div/div[1]')
    __create_project_loc2=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[2]/div[2]/div/div[2]/div[9]/button')
    __project_name=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[4]/div/div/div/form/div[1]/div/div/div/input')
    __project_key=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[4]/div/div/div/form/div[2]/div/div/div/input')
    __confirm_btn=(By.XPATH,'/html/body/div[1]/section/section/main/div/div[4]/div/div/footer/button[2]')
    __result_toast=(By.XPATH,'/html/body/div[3]/p')

    def click_menu(self):
        """点击菜单"""
        self.fd_element(self.__left_menu_loc).click()
        time.sleep(1)

    def click_menu2(self):
        """点击菜单"""
        self.fd_element(self.__left_menu_loc2).click()

    def click_create_project(self):
        """点击创建项目"""
        self.fd_element(self.__create_project_loc).click()

    def click_select_team(self):
        """点击选择团队"""
        self.fd_element(self.__select_team_loc).click()

    def click_create_project2(self):
        """点击创建项目"""
        self.fd_element(self.__create_project_loc2).click()

    def input_project_info(self,project_name,project_key):
        """输入项目信息"""
        self.fd_element(self.__project_name).send_keys(project_name)
        self.fd_element(self.__project_key).send_keys(project_key)

    def click_confirm_btn(self):
        """点击确认按钮"""
        self.fd_element(self.__confirm_btn).click()

    def create_project(self,project_name,project_key):
        """创建项目"""
        self.click_menu()
        self.click_menu2()
        self.click_create_project()
        self.click_select_team()
        self.click_create_project2()
        self.input_project_info(project_name,project_key)
        self.click_confirm_btn()


    def get_result(self):
        """获取创建结果"""
        return self.fd_element(self.__result_toast).text