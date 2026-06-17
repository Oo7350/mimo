"""
调用完整流程：
mimo_api_client是最底层，定义了各种方法，方法需要传递各种参数。
每个接口有自己的接口类，接口类需要传入client对象，client对象可以调用mimo_api_client的方法。
MimoAPI，它封装了所有模块的接口。
conftest通过调用mimo_api_client的方法，返回api_client对象。（不同的测试类用同一个api_client对象）
在测试类中，使用conftest返回的api_client对象，调用MimoAPI的方法，可以用同一个帐号测试不同的接口。
"""
"""Mimo 用户模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog


class TestUserAPI:
    """用户模块：个人信息、修改密码"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()

    # ==================== 个人信息 ====================

    def test_get_profile_success(self, api_client_admin):
        """获取个人信息成功"""
        api = MimoAPI(api_client_admin)
        resp = api.user.get_profile()

        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert "data" in data
        profile = data["data"]
        assert profile["username"] == "admin"
        assert profile["email"] == "admin@mimo.dev"
        assert profile["role"] == "ROLE_ADMIN"
        self.logger.info(f"个人信息: {profile}")

    def test_get_profile_no_token(self, api_client):
        """未登录获取个人信息应 401"""
        api = MimoAPI(api_client)
        resp = api.user.get_profile()
        assert resp.status_code == 401
        self.logger.info("未登录获取个人信息返回 401")

    def test_update_profile_success(self, api_client_admin):
        """更新个人信息"""
        api = MimoAPI(api_client_admin)
        resp = api.user.update_profile({
            "username": "admin",
            "email": "admin@mimo.dev"
        })
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info("个人信息更新成功")

    # ==================== 修改密码 ====================

    @pytest.mark.parametrize(
        "oldPassword,newPassword,confirmPassword,status,errno,errmsg",
        [(d["oldPassword"], d["newPassword"], d["confirmPassword"],
          d["status"], d["errno"], d["errmsg"])
         for d in read_json("update_password.json")]
    )
    def test_update_password_parametrized(
        self, api_client_admin, oldPassword, newPassword, confirmPassword, status, errno, errmsg
    ):
        """修改密码参数化测试"""
        api = MimoAPI(api_client_admin)
        resp = api.user.update_password({
            "oldPassword": oldPassword,
            "newPassword": newPassword,
            "confirmPassword": confirmPassword
        })

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            self.logger.info("密码修改成功，正在恢复原密码...")
            # 密码测试完恢复原密码
            api.user.update_password({
                "oldPassword": newPassword,
                "newPassword": oldPassword,
                "confirmPassword": oldPassword
            })
        elif errmsg:
            assert errmsg in resp_json.get("message", ""), \
                f"消息应包含'{errmsg}'，实际'{resp_json.get('message')}'"
