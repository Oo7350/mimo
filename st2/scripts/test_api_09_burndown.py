"""Mimo 燃尽图模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.logger import GetLog


class TestBurndownAPI:
    """燃尽图模块"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()

    def _get_active_sprint(self, api):
        """获取一个活跃或有数据的 Sprint"""
        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            pytest.skip("没有可用的项目")
        project_id = projects[0]["id"]

        sprints = api.sprint.get_sprints_by_project(project_id).json()["data"]
        # 优先找 ACTIVE 的
        for s in sprints:
            if s["status"] == "ACTIVE":
                return s["id"]
        # 否则取第一个已完成的
        for s in sprints:
            if s["status"] == "COMPLETED":
                return s["id"]
        # 都没有则创建并启动
        resp = api.sprint.create_sprint({
            "projectId": project_id,
            "name": "燃尽图测试Sprint",
            "goal": "测试燃尽图",
            "startDate": "2026-08-01",
            "endDate": "2026-08-14"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 Sprint")
        sprint_id = resp.json()["data"]["id"]
        api.sprint.start_sprint(sprint_id)
        return sprint_id

    def test_get_burndown(self, api_client_admin):
        """获取燃尽图数据"""
        api = MimoAPI(api_client_admin)
        sprint_id = self._get_active_sprint(api)

        resp = api.burndown.get_burndown(sprint_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200

        burndown = data["data"]
        assert "sprintName" in burndown
        assert "startDate" in burndown
        assert "endDate" in burndown
        assert "totalPoints" in burndown
        assert "points" in burndown
        self.logger.info(f"燃尽图: {burndown['sprintName']}, 总点数 {burndown['totalPoints']}")

    def test_create_snapshot(self, api_client_admin):
        """手动生成燃尽快照"""
        api = MimoAPI(api_client_admin)
        sprint_id = self._get_active_sprint(api)

        resp = api.burndown.create_snapshot(sprint_id)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"Sprint {sprint_id} 快照生成成功")

    def test_get_burndown_no_token(self, api_client):
        """未登录获取燃尽图应 401"""
        api = MimoAPI(api_client)
        resp = api.burndown.get_burndown(1)
        assert resp.status_code == 401
