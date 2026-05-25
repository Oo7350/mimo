import json
import logging
from logging import handlers
import time
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from config import PATH


class DriverTools(object):
    """浏览器驱动工具类"""

    # 设置浏览器驱动初始值
    driver = None

    # 获取浏览器驱动
    @classmethod
    def get_driver(cls):
        if cls.driver is None:
            path = r"D:\Python\python_pdk\msedgedriver.exe"
            ser = Service(executable_path=path)
            driver = webdriver.Edge(service=ser)
            # 设置浏览器最大化
            driver.maximize_window()
            driver.implicitly_wait(10)  # 全局隐式等待
            # 将driver赋值给类属性
            cls.driver = driver
        # 返回浏览器驱动对象
        return cls.driver

    # 退出浏览器驱动
    @classmethod
    def quit_driver(cls):
        # 判断浏览器驱动对象是否为空
        if cls.driver is not None:
            time.sleep(2)
            # 不为空退出
            cls.driver.quit()
            # 置空
            cls.driver = None


def read_json(file_name):
    """
    读取JSON文件并转换为格式为 [(), (), ...] 的列表
    :param file_name: json文件名
    :return: 列表
    """
    data = []
    file_path = PATH + "/data/" + file_name
    #以只读形式打开json文件，处理完成后自动关闭
    with open(file_path, mode='r', encoding='utf-8') as f:
        # 读取JSON文件并解析为Python对象
        tmp = json.load(f)
        for i in tmp:
            #i相当于字典
            a = tuple(i.values())
            data.append(a)
        # 返回列表
        return data

class GetLog:
    # 日志器
    __log = None

    @classmethod
    def get_log(cls):
        if cls.__log is None:
            # 获取日志器
            cls.__log = logging.getLogger()
            # 设置入口级别
            cls.__log.setLevel(logging.INFO)
            # 获取处理器
            filename = PATH + "/log/" + "web.log"
            tf = logging.handlers.TimedRotatingFileHandler(filename=filename,  # 日志文件名
                                                           when="midnight",  # 日志归档时间
                                                           interval=1,  # 每天归档一次
                                                           backupCount=3,  # 保留3天日志
                                                           encoding="utf-8")  # 日志编码格式
            # 获取格式器
            fmt = "%(asctime)s %(levelname)s [%(filename)s(%(funcName)s:%(lineno)d)] - %(message)s"
            fm = logging.Formatter(fmt)
            # 将格式器添加到处理器
            tf.setFormatter(fm)
            # 将处理器添加到日志器
            cls.__log.addHandler(tf)
        # 返回日志器
        return cls.__log

if __name__ == '__main__':
    print(read_json("login_data.json"))
