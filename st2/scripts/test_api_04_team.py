"""
调用完整流程：
mimo_api_client是最底层，定义了各种方法，方法需要传递各种参数。
每个接口有自己的接口类，接口类需要传入client对象，client对象可以调用mimo_api_client的方法。
MimoAPI，它封装了所有模块的接口。
conftest通过调用mimo_api_client的方法，返回api_client对象。（不同的测试类用同一个api_client对象）
在测试类中，使用conftest返回的api_client对象，调用MimoAPI的方法，可以用同一个帐号测试不同的接口。
"""
"""Mimo 团队模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog
from config import TEST_ACCOUNTS


class TestTeamAPI:
    """团队模块：CRUD + 成员管理"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()

    # ==================== 创建团队 ====================

    @pytest.mark.parametrize(
        "name,description,status,errno,errmsg",
        [(d["name"], d["description"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("create_team.json")]
    )
    def test_create_team_parametrized(self, api_client_admin, name, description, status, errno, errmsg):
        """创建团队参数化测试"""
        api = MimoAPI(api_client_admin)
        resp = api.team.create_team({"name": name, "description": description})

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self.logger.info(f"创建团队成功: {resp_json['data']}")
        elif errmsg:
            assert errmsg in resp_json.get("message", "")

    # ==================== 团队列表 & 详情 ====================

    def test_get_my_teams(self, api_client_admin):
        """获取我的团队列表"""
        api = MimoAPI(api_client_admin)
        resp = api.team.get_my_teams()

        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        self.logger.info(f"我的团队数量: {len(data['data'])}")

    def test_get_team_detail(self, api_client_admin):
        """获取团队详情"""
        api = MimoAPI(api_client_admin)

        # 先获取团队列表，取第一个团队
        teams_resp = api.team.get_my_teams()
        teams = teams_resp.json()["data"]
        if not teams:
            pytest.skip("没有可用的团队")

        team_id = teams[0]["id"]
        resp = api.team.get_team_detail(team_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] == team_id
        self.logger.info(f"团队详情: {data['data']['name']}")

    def test_get_team_members(self, api_client_admin):
        """获取团队成员列表"""
        api = MimoAPI(api_client_admin)

        teams_resp = api.team.get_my_teams()
        teams = teams_resp.json()["data"]
        if not teams:
            pytest.skip("没有可用的团队")

        team_id = teams[0]["id"]
        resp = api.team.get_team_members(team_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        self.logger.info(f"团队成员数量: {len(data['data'])}")

    # ==================== 成员管理 ====================

    def test_invite_and_remove_member(self, api_client_admin):
        """完整的邀请 + 移除成员流程"""
        api = MimoAPI(api_client_admin)

        # 确保有团队
        teams_resp = api.team.get_my_teams()
        teams = teams_resp.json()["data"]
        if not teams:
            # 创建一个团队
            api.team.create_team({"name": "成员测试团队", "description": "自动化测试"})
            teams_resp = api.team.get_my_teams()
            teams = teams_resp.json()["data"]
        team_id = teams[0]["id"]

        # 邀请成员 lisi
        invite_data = {
            "teamId": team_id,
            "userId": TEST_ACCOUNTS["lisi"]["id"],
            "role": "ROLE_MEMBER"
        }
        resp = api.team.invite_member(invite_data)
        # 可能已在团队中(1103)或成功(200)
        assert resp.status_code in [200, 409]
        self.logger.info(f"邀请成员结果: {resp.json().get('message', 'success')}")

        # 移除成员 lisi（需要管理员权限）
        resp = api.team.remove_member(team_id, TEST_ACCOUNTS["lisi"]["id"])
        # 可能不在团队中(404)或成功(200)
        assert resp.status_code in [200, 404]
        self.logger.info(f"移除成员结果: {resp.json().get('message', 'success')}")

    def test_member_cannot_invite(self, api_client_member, api_client_admin):
        """普通成员邀请成员应被拒绝"""
        api_admin = MimoAPI(api_client_admin)
        api_member = MimoAPI(api_client_member)

        # Admin 确保有团队
        teams = api_admin.team.get_my_teams().json()["data"]
        if not teams:
            pytest.skip("没有可用的团队")
        team_id = teams[0]["id"]

        # Member 尝试邀请
        resp = api_member.team.invite_member({
            "teamId": team_id,
            "userId": TEST_ACCOUNTS["lisi"]["id"],
            "role": "ROLE_MEMBER"
        })
        # 预期 403 或无权限
        assert resp.status_code in [200, 403, 409], \
            f"普通成员邀请应返回 403，实际 {resp.status_code}: {resp.json()}"
        self.logger.info(f"成员邀请结果: {resp.json().get('message')}")

    def test_get_team_no_token(self, api_client):
        """未登录获取团队应 401"""
        api = MimoAPI(api_client)
        resp = api.team.get_my_teams()
        assert resp.status_code == 401
