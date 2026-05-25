"""Mimo 认证模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog


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
        api = MimoAPI(api_client)
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
