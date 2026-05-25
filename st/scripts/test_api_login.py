# 导包
import pytest
from api.mimo_api import MimoAPI
from tools import *


# 创建测试类
class TestLogin:
    # 初始化属性
    token = None

    # 前置处理
    def setup_class(self):
        # 初始化Mimo API接口类
        self.mimo_api = MimoAPI()

    # 登录测试
    @pytest.mark.parametrize("username,password,status,errno,errmsg", read_json("login.json"))
    def test_login(self, username, password, status, errno, errmsg):
        response = self.mimo_api.login({"username": username, "password": password})
        # 断言
        print(response.json())
        assert response.status_code == status
        assert response.json()['code'] == errno
        if errno == 200:
            assert errmsg in response.json()['message']
            # 保存token供其他测试使用
            TestLogin.token = response.json()['data']['token']
        else:
            assert errmsg in str(response.json())
