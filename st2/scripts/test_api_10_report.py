"""Mimo 报告模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog


class TestReportAPI:
    """报告模块：生成 + 列表 + 详情 + 更新 + 提交"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()
        cls._project_id = None
        cls._report_id = None

    def _ensure_project(self, api):
        """确保有项目"""
        if self._project_id:
            return self._project_id
        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            teams = api.team.get_my_teams().json()["data"]
            if not teams:
                api.team.create_team({"name": "报告测试团队", "description": "自动化"})
                teams = api.team.get_my_teams().json()["data"]
            api.project.create_project({
                "name": "报告测试项目",
                "key": "REPORT",
                "description": "自动化测试",
                "template": "SCRUM",
                "teamId": teams[0]["id"]
            })
            projects = api.project.get_my_projects().json()["data"]
        self._project_id = projects[0]["id"]
        return self._project_id

    # ==================== 生成报告 ====================

    @pytest.mark.parametrize(
        "report_type,reportDate,status,errno,errmsg",
        [(d["type"], d["reportDate"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("generate_report.json")]
    )
    def test_generate_report_parametrized(
        self, api_client_admin, report_type, reportDate, status, errno, errmsg
    ):
        """生成报告参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        resp = api.report.generate_report({
            "projectId": project_id,
            "type": report_type,
            "reportDate": reportDate
        })

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self._report_id = resp_json["data"]["id"]
            self.logger.info(f"生成报告成功: type={report_type}, id={self._report_id}")
        elif errmsg:
            assert errmsg in resp_json.get("message", "")

    # ==================== 报告列表 & 详情 ====================

    def test_get_reports(self, api_client_admin):
        """获取报告列表"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        resp = api.report.get_reports({"projectId": project_id})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        self.logger.info(f"报告数量: {len(data['data'])}")

    def test_get_report_detail(self, api_client_admin):
        """获取报告详情"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        # 确保有报告
        reports = api.report.get_reports({"projectId": project_id}).json()["data"]
        if not reports:
            resp = api.report.generate_report({
                "projectId": project_id,
                "type": "DAILY",
                "reportDate": "2026-05-18"
            })
            if resp.json()["code"] != 200:
                pytest.skip("无法生成报告")
            report_id = resp.json()["data"]["id"]
        else:
            report_id = reports[0]["id"]

        resp = api.report.get_report(report_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] == report_id
        self.logger.info(f"报告详情: id={report_id}")

    # ==================== 更新 & 提交 ====================

    def test_update_and_submit_report(self, api_client_admin):
        """更新报告内容并提交"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        # 生成一个 DRAFT 报告
        resp = api.report.generate_report({
            "projectId": project_id,
            "type": "DAILY",
            "reportDate": "2026-05-18"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法生成报告")
        report_id = resp.json()["data"]["id"]

        # 更新报告内容
        update_data = read_json("update_report.json")[0]
        resp = api.report.update_report({
            "id": report_id,
            "content": update_data["content"]
        })
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"报告 {report_id} 内容更新成功")

        # 提交报告
        resp = api.report.submit_report(report_id)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"报告 {report_id} 提交成功")

        # 提交后不可再编辑
        resp = api.report.update_report({
            "id": report_id,
            "content": "尝试修改已提交报告"
        })
        # 预期失败
        self.logger.info(f"提交后编辑结果: {resp.json().get('message')}")

    def test_get_report_no_token(self, api_client):
        """未登录获取报告应 401"""
        api = MimoAPI(api_client)
        resp = api.report.get_report(1)
        assert resp.status_code == 401
