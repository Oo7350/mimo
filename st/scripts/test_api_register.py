# 导包
import pytest
from api.mimo_api import MimoAPI
from tools import *


# 创建测试类
class TestRegister:
    # 前置处理
    def setup_class(self):
        # 初始化Mimo API接口类
        self.mimo_api = MimoAPI()

    # 注册测试
    @pytest.mark.parametrize("username,password,confirmPassword,email,status,errno,errmsg", read_json("register.json"))
    def test_register(self, username, password, confirmPassword, email, status, errno, errmsg):
        data = {
            "username": username,
            "password": password,
            "confirmPassword": confirmPassword,
            "email": email
        }
        response = self.mimo_api.register(data)
        # 断言
        print(response.json())
        assert response.status_code == status
        assert response.json()['code'] == errno
        if errno == 200:
            assert errmsg in response.json()['message']
        else:
            assert errmsg in str(response.json())
