"""Mimo Sprint 模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog


class TestSprintAPI:
    """Sprint 模块：创建 + 查看 + 列表 + 启动 + 完成"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()
        cls._project_id = None
        cls._sprint_id = None

    def _ensure_project(self, api):
        """确保有项目"""
        if self._project_id:
            return self._project_id
        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            teams = api.team.get_my_teams().json()["data"]
            if not teams:
                api.team.create_team({"name": "Sprint测试团队", "description": "自动化"})
                teams = api.team.get_my_teams().json()["data"]
            api.project.create_project({
                "name": "Sprint测试项目",
                "key": "SPRINT",
                "description": "自动化测试",
                "template": "SCRUM",
                "teamId": teams[0]["id"]
            })
            projects = api.project.get_my_projects().json()["data"]
        self._project_id = projects[0]["id"]
        return self._project_id

    # ==================== 创建 Sprint ====================

    @pytest.mark.parametrize(
        "name,goal,startDate,endDate,status,errno,errmsg",
        [(d["name"], d["goal"], d["startDate"], d["endDate"],
          d["status"], d["errno"], d["errmsg"])
         for d in read_json("create_sprint.json")]
    )
    def test_create_sprint_parametrized(
        self, api_client_admin, name, goal, startDate, endDate, status, errno, errmsg
    ):
        """创建 Sprint 参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        resp = api.sprint.create_sprint({
            "projectId": project_id,
            "name": name,
            "goal": goal,
            "startDate": startDate,
            "endDate": endDate
        })

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self._sprint_id = resp_json["data"]["id"]
            self.logger.info(f"创建 Sprint 成功: id={self._sprint_id}")
        elif errmsg:
            assert errmsg in resp_json.get("message", "")

    # ==================== Sprint 列表 & 详情 ====================

    def test_get_sprints_by_project(self, api_client_admin):
        """获取项目 Sprint 列表"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        resp = api.sprint.get_sprints_by_project(project_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        self.logger.info(f"项目 Sprint 数量: {len(data['data'])}")

        if data["data"]:
            self._sprint_id = data["data"][0]["id"]

    def test_get_sprint_detail(self, api_client_admin):
        """获取 Sprint 详情"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        # 获取 Sprint 列表
        sprints = api.sprint.get_sprints_by_project(project_id).json()["data"]
        if not sprints:
            pytest.skip("没有可用的 Sprint")

        sprint_id = sprints[0]["id"]
        resp = api.sprint.get_sprint(sprint_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] == sprint_id
        self.logger.info(f"Sprint 详情: {data['data']['name']}")

    # ==================== Sprint 生命周期 ====================

    def test_sprint_lifecycle(self, api_client_admin):
        """Sprint 完整生命周期：创建 → 启动 → 完成"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        # 创建
        resp = api.sprint.create_sprint({
            "projectId": project_id,
            "name": "生命周期测试Sprint",
            "goal": "测试完整生命周期",
            "startDate": "2026-07-01",
            "endDate": "2026-07-14"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 Sprint")
        sprint_id = resp.json()["data"]["id"]
        self.logger.info(f"Sprint 创建: id={sprint_id}")

        # 获取详情确认 PLANNING 状态
        detail = api.sprint.get_sprint(sprint_id).json()["data"]
        assert detail["status"] == "PLANNING"

        # 启动 Sprint
        resp = api.sprint.start_sprint(sprint_id)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        detail = api.sprint.get_sprint(sprint_id).json()["data"]
        assert detail["status"] == "ACTIVE"
        self.logger.info(f"Sprint {sprint_id} 启动成功")

        # 完成 Sprint
        resp = api.sprint.complete_sprint(sprint_id)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        detail = api.sprint.get_sprint(sprint_id).json()["data"]
        assert detail["status"] == "COMPLETED"
        self.logger.info(f"Sprint {sprint_id} 完成")

    def test_get_sprint_no_token(self, api_client):
        """未登录获取 Sprint 应 401"""
        api = MimoAPI(api_client)
        resp = api.sprint.get_sprint(1)
        assert resp.status_code == 401
