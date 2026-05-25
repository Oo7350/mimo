import os.path
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.select import Select
from selenium.webdriver.support.wait import WebDriverWait
from config import PATH
from tools import GetLog


class BasePage(object):

    def __init__(self, driver, timeout=10):
        # 获取浏览器对象
        self.driver = driver  # 相当于：self.driver = DriverTools.get_driver()
        self.default_timeout = timeout  # 默认等待时间

    def fd_element(self, loc):
        """
        元素定位的公共方法
        :param loc: 元素定位方式及属性值
        :return: 定位到的元素
        """
        try:
            # 推荐写法
            # element = WebDriverWait(self.driver, self.default_timeout).until(EC.presence_of_element_located(loc))
            element = WebDriverWait(self.driver, self.default_timeout).until(EC.visibility_of_element_located(loc))
            return element
        except Exception as e:
            GetLog.get_log().error(f"元素定位超时，定位信息：{loc}，错误详情：{e}")
            raise  # 重新抛出异常供上层处理

    def get_shot(self, file_name):
        """
        截图
        :param file_name: 截图文件名(带.png后缀)
        :return:无
        """
        # self.driver.get_screenshot_as_file(PATH + r"\img\pwd_error.png")
        # self.driver.get_screenshot_as_file(PATH + '/img/' + file_name)
        # 推荐
        file_path = os.path.join(PATH, 'img', file_name)
        self.driver.get_screenshot_as_file(file_path)

    def base_input(self, loc, text):
        """
        输入框输入公共方法
        :param loc: 元素定位方式及属性值
        :param text: 输入内容
        :return: 无
        """
        # self.fd_element(loc).clear()
        # self.fd_element(loc).send_keys(text)
        # 定位元素
        ele = self.fd_element(loc)
        # 清空输入框
        ele.clear()
        # 输入内容
        ele.send_keys(text)

    def base_click(self, loc):
        """
        点击公共方法
        :param loc: 元素定位方式及属性值
        :return: 无
        """
        self.fd_element(loc).click()

    def base_click_special(self, loc):
        """
        点击方法:处理元素不可见的定位
        :param loc: 元素定位方式及属性值
        :return: 无
        """
        ele = WebDriverWait(self.driver, self.default_timeout).until(EC.presence_of_element_located(loc))
        ele.click()

    def base_switch_handle(self, loc):
        """
        切换多窗口并获取指定元素
        :param loc: 定位的元素信息
        :return: 第二个窗口的页面元素
        """
        # 等待页面加载
        WebDriverWait(self.driver, self.default_timeout).until(lambda x: len(x.window_handles) > 1)
        handles = self.driver.window_handles
        self.driver.switch_to.window(handles[1])
        # 切换到新窗口进行定位
        element = self.fd_element(loc)
        return element

    def base_switch_frame(self, loc):
        """
        切换frame
        :param loc: frame的定位信息
        :return: 无
        """
        frame_ele = self.fd_element(loc)
        self.driver.switch_to.frame(frame_ele)

    def base_default_frame(self):
        """
        切换到默认frame
        :return: 无
        """
        self.driver.switch_to.default_content()

    def base_select_list(self, loc, text):
        """
        下拉框选择
        :param loc: 元素定位方式及属性值
        :param text: 选择下拉框中的文本
        :return: 无
        """
        ele = self.fd_element(loc)
        Select(ele).select_by_visible_text(text)

    def base_get_toast(self, toast_loc, timeout=10):
        """获取Toast提示文本"""
        try:
            # toast_loc = (By.XPATH, '//android.widget.Toast[1]')
            toast = WebDriverWait(self.driver, timeout).until(
                EC.presence_of_element_located(toast_loc))
            text = toast.text
            GetLog.get_log().info(f"获取Toast提示: {text}")
            return text
        except Exception as e:
            GetLog.get_log().error(f"获取Toast失败: {str(e)}")
            return ""

