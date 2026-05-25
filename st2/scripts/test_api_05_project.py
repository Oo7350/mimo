"""Mimo 项目模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog
from config import TEST_ACCOUNTS


class TestProjectAPI:
    """项目模块：CRUD + 成员"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()

    # ==================== 创建项目 ====================

    @pytest.mark.parametrize(
        "name,key,description,template,status,errno,errmsg",
        [(d["name"], d["key"], d["description"], d["template"],
          d["status"], d["errno"], d["errmsg"])
         for d in read_json("create_project.json")]
    )
    def test_create_project_parametrized(
        self, api_client_admin, name, key, description, template, status, errno, errmsg
    ):
        """创建项目参数化测试"""
        api = MimoAPI(api_client_admin)

        # 需要 team_id
        teams = api.team.get_my_teams().json()["data"]
        if not teams:
            # 创建团队
            api.team.create_team({"name": "项目测试团队", "description": "自动化"})
            teams = api.team.get_my_teams().json()["data"]
        team_id = teams[0]["id"]

        resp = api.project.create_project({
            "name": name,
            "key": key,
            "description": description,
            "template": template,
            "teamId": team_id
        })

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self.logger.info(f"创建项目成功: {resp_json['data']}")
        elif errmsg:
            assert errmsg in resp_json.get("message", "")

    # ==================== 项目列表 & 详情 ====================

    def test_get_my_projects(self, api_client_admin):
        """获取我的项目列表"""
        api = MimoAPI(api_client_admin)
        resp = api.project.get_my_projects()

        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        self.logger.info(f"我的项目数量: {len(data['data'])}")

    def test_get_project_detail(self, api_client_admin):
        """获取项目详情"""
        api = MimoAPI(api_client_admin)

        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            pytest.skip("没有可用的项目")

        project_id = projects[0]["id"]
        resp = api.project.get_project_detail(project_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] == project_id
        self.logger.info(f"项目详情: {data['data']['name']}")

    def test_get_projects_by_team(self, api_client_admin):
        """按团队查询项目"""
        api = MimoAPI(api_client_admin)

        teams = api.team.get_my_teams().json()["data"]
        if not teams:
            pytest.skip("没有可用的团队")

        team_id = teams[0]["id"]
        resp = api.project.get_projects_by_team(team_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        self.logger.info(f"团队{team_id}下的项目数量: {len(data['data'])}")

    # ==================== 项目成员 ====================

    def test_add_project_member(self, api_client_admin):
        """添加项目成员"""
        api = MimoAPI(api_client_admin)

        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            pytest.skip("没有可用的项目")

        project_id = projects[0]["id"]
        resp = api.project.add_project_member(
            project_id,
            TEST_ACCOUNTS["zhangsan"]["id"]
        )
        # 可能已在项目中(409)或成功(200)
        assert resp.status_code in [200, 409]
        self.logger.info(f"添加成员结果: {resp.json().get('message')}")

    def test_get_project_no_token(self, api_client):
        """未登录获取项目应 401"""
        api = MimoAPI(api_client)
        resp = api.project.get_my_projects()
        assert resp.status_code == 401
