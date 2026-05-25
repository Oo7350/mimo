"""Mimo 仪表盘模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.logger import GetLog


class TestDashboardAPI:
    """仪表盘模块"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()

    def test_get_dashboard_success(self, api_client_admin):
        """获取仪表盘数据"""
        api = MimoAPI(api_client_admin)
        resp = api.dashboard.get_dashboard()

        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        dashboard = data["data"]

        # 验证核心字段
        assert "username" in dashboard
        assert "totalTasks" in dashboard
        assert "inProgressTasks" in dashboard
        assert "completedTasks" in dashboard
        assert "myProjects" in dashboard
        assert isinstance(dashboard["myProjects"], list)
        assert "recentActivities" in dashboard

        self.logger.info(f"仪表盘: {dashboard['username']}, 总任务{dashboard['totalTasks']}")

    def test_get_dashboard_no_token(self, api_client):
        """未登录获取仪表盘应 401"""
        api = MimoAPI(api_client)
        resp = api.dashboard.get_dashboard()
        assert resp.status_code == 401
        self.logger.info("未登录获取仪表盘返回 401")
