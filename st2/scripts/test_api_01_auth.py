"""Mimo 认证模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog


"""
调用完整流程：
mimo_api_client是最底层，定义了各种方法，方法需要传递各种参数。
每个接口有自己的接口类，接口类需要传入client对象，client对象可以调用mimo_api_client的方法。
MimoAPI，它封装了所有模块的接口。
conftest通过调用mimo_api_client的方法，返回api_client对象。（不同的测试类用同一个api_client对象）
在测试类中，使用conftest返回的api_client对象，调用MimoAPI的方法，可以用同一个帐号测试不同的接口。
"""


class TestAuthAPI:
    """认证模块：登录 + 注册"""
    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()

    # ==================== 登录 ====================

    @pytest.mark.parametrize(
        "username,password,status,errno,errmsg",
        [(d["username"], d["password"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("login.json")]
    )
    def test_login_parametrized(self, api_client, username, password, status, errno, errmsg):
        """登录参数化测试：覆盖成功 + 各种失败场景"""
        api = MimoAPI(api_client)
        resp = api.auth.login({"username": username, "password": password})

        assert resp.status_code == status, f"HTTP状态码应为{status}，实际{resp.status_code}"
        resp_json = resp.json()
        assert resp_json["code"] == errno, f"业务码应为{errno}，实际{resp_json['code']}"

        if errno == 200:
            assert "token" in resp_json["data"], "登录成功应返回 token"
            self.logger.info(f"登录成功: {username}")
        elif errmsg:
            assert errmsg in resp_json.get("message", ""), \
                f"消息应包含'{errmsg}'，实际'{resp_json.get('message')}'"

    def test_login_returns_user_info(self, api_client):
        """登录成功时返回用户信息"""
        api = MimoAPI(api_client) #使用传入的api_client创建的api对象 #这个 api 对象可以调用所有模块的接口。
        """
        如果没有api对象，只能调用单个模块的接口，无法调用其他模块的接口。
        auth = AuthAPI(api_client)
        user = UserAPI(api_client)
        project = ProjectAPI(api_client)
        """
        resp = api.auth.login({"username": "admin", "password": "123456"})
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "token" in data
        assert "userInfo" in data
        user_info = data["userInfo"]
        assert user_info["username"] == "admin"
        assert user_info["role"] == "ROLE_ADMIN"
        self.logger.info(f"用户信息: {user_info}")

    def test_login_token_can_be_used(self, api_client):
        """登录后 token 可用于认证接口"""
        api = MimoAPI(api_client)
        api.auth.login({"username": "admin", "password": "123456"})

        resp = api.user.get_profile()
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info("Token 认证通过，成功获取个人信息")

    # ==================== 注册 ====================

    @pytest.mark.parametrize(
        "username,password,confirmPassword,email,status,errno,errmsg",
        [(d["username"], d["password"], d["confirmPassword"],
          d["email"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("register.json")]
    )
    def test_register_parametrized(self, api_client, username, password, confirmPassword, email, status, errno, errmsg):
        """注册参数化测试：覆盖成功 + 重复用户名/邮箱 + 参数校验"""
        api = MimoAPI(api_client)
        resp = api.auth.register({
            "username": username,
            "password": password,
            "confirmPassword": confirmPassword,
            "email": email
        })

        assert resp.status_code == status, f"HTTP状态码应为{status}，实际{resp.status_code}"
        resp_json = resp.json()
        assert resp_json["code"] == errno, f"业务码应为{errno}，实际{resp_json['code']}"

        if errno == 200:
            self.logger.info(f"注册成功: {username}")
        elif errmsg:
            assert errmsg in resp_json.get("message", ""), \
                f"消息应包含'{errmsg}'，实际'{resp_json.get('message')}'"

    def test_register_then_login(self, api_client):
        """注册后可用新账号登录"""
        api = MimoAPI(api_client)
        import time
        username = f"auto_{int(time.time()) % 100000}"
        api.auth.register({
            "username": username,
            "password": "123456",
            "confirmPassword": "123456",
            "email": f"{username}@mimo.dev"
        })

        resp = api.auth.login({"username": username, "password": "123456"})
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"注册后登录成功: {username}")
