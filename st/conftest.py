import time

import pytest
from selenium.webdriver.edge.service import Service
from selenium import webdriver

from pages.page_login import Login
from tools import DriverTools


@pytest.fixture
def web_driver():
    path=r"D:\Python\python_pdk\msedgedriver.exe"
    ser =Service(executable_path= path)
    driver=webdriver.Edge(service=ser)
    # 设置浏览器最大化
    driver.maximize_window()
    driver.implicitly_wait(10)#全局隐式等待
    yield driver
    # 退出浏览器驱动
    time.sleep(5)
    driver.quit()

@pytest.fixture
def login(web_driver,username="admin",password="123456"):
    """登录"""
    # 登录
    login = Login(driver=web_driver)
    login.open_url()
    login.login(username, password)
