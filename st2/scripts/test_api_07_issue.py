"""Mimo 任务模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog
from config import TEST_ACCOUNTS


class TestIssueAPI:
    """任务模块：CRUD + 查询"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()
        cls._project_id = None
        cls._column_id = None
        cls._issue_id = None

    def _ensure_project_and_column(self, api):
        """确保有可用的项目和列"""
        if self._project_id and self._column_id:
            return self._project_id, self._column_id

        # 获取或创建项目
        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            teams = api.team.get_my_teams().json()["data"]
            if not teams:
                api.team.create_team({"name": "任务测试团队", "description": "自动化"})
                teams = api.team.get_my_teams().json()["data"]
            api.project.create_project({
                "name": "任务测试项目",
                "key": "ISSUE",
                "description": "自动化测试",
                "template": "SCRUM",
                "teamId": teams[0]["id"]
            })
            projects = api.project.get_my_projects().json()["data"]
        self._project_id = projects[0]["id"]

        # 获取第一列
        board = api.board.get_board(self._project_id).json()["data"]
        self._column_id = board["columns"][0]["id"]

        return self._project_id, self._column_id

    # ==================== 创建任务 ====================

    @pytest.mark.parametrize(
        "title,issue_type,priority,status,errno,errmsg",
        [(d.get("title"), d.get("type"), d.get("priority"),
          d["status"], d["errno"], d["errmsg"])
         for d in read_json("create_issue.json")]
    )
    def test_create_issue_parametrized(
        self, api_client_admin, title, issue_type, priority, status, errno, errmsg
    ):
        """创建任务参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        body = {
            "projectId": project_id,
            "columnId": column_id,
            "title": title,
            "type": issue_type,
            "priority": priority
        }

        # Bug 类型附加字段
        test_data_list = read_json("create_issue.json")
        for td in test_data_list:
            if td.get("title") == title and td.get("type") == "BUG":
                body["severity"] = td.get("severity", "MINOR")
                body["stepsToRepro"] = td.get("stepsToRepro", "")
                break

        resp = api.issue.create_issue(body)

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self._issue_id = resp_json["data"]["id"]
            issue_key = resp_json["data"].get("issueKey", "")
            self.logger.info(f"创建任务成功: {issue_key}")
        elif errmsg:
            assert errmsg in resp_json.get("message", "")

    def test_create_bug_with_severity(self, api_client_admin):
        """创建 Bug 类型任务含严重程度和复现步骤"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "API测试Bug任务",
            "type": "BUG",
            "priority": "HIGH",
            "severity": "CRITICAL",
            "stepsToRepro": "1. 打开首页\n2. 点击按钮\n3. 错误出现"
        })
        if resp.json()["code"] == 200:
            self._issue_id = resp.json()["data"]["id"]
            self.logger.info(f"Bug任务创建成功: {resp.json()['data'].get('issueKey')}")

    # ==================== 查询 ====================

    @pytest.mark.parametrize(
        "page,size,filters,status,errno,errmsg",
        [(d["page"], d["size"], d["filters"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("query_issues.json")]
    )
    def test_query_issues_parametrized(self, api_client_admin, page, size, filters, status, errno, errmsg):
        """查询任务参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id, _ = self._ensure_project_and_column(api)

        body = {
            "page": page,
            "size": size,
            "projectId": project_id,
            **filters
        }
        resp = api.issue.query_issues(body)

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self.logger.info(f"查询结果: {len(resp_json['data'].get('records', resp_json['data']))} 条记录")

    # ==================== 详情 & 更新 ====================

    def test_get_issue_detail(self, api_client_admin):
        """获取任务详情"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 确保有任务
        if not self._issue_id:
            resp = api.issue.create_issue({
                "projectId": project_id,
                "columnId": column_id,
                "title": "详情测试任务",
                "type": "TASK",
                "priority": "MEDIUM"
            })
            if resp.json()["code"] == 200:
                self._issue_id = resp.json()["data"]["id"]

        if not self._issue_id:
            pytest.skip("无法获取任务用于测试")

        resp = api.issue.get_issue(self._issue_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["id"] == self._issue_id
        self.logger.info(f"任务详情: {data['data'].get('issueKey', '')} - {data['data']['title']}")

    def test_update_issue(self, api_client_admin):
        """更新任务"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        if not self._issue_id:
            resp = api.issue.create_issue({
                "projectId": project_id,
                "columnId": column_id,
                "title": "更新测试任务",
                "type": "TASK",
                "priority": "LOW"
            })
            if resp.json()["code"] == 200:
                self._issue_id = resp.json()["data"]["id"]

        if not self._issue_id:
            pytest.skip("无法获取任务用于更新测试")

        resp = api.issue.update_issue({
            "id": self._issue_id,
            "projectId": project_id,
            "columnId": column_id,
            "title": "API测试任务（已更新）",
            "type": "TASK",
            "priority": "HIGH",
            "storyPoints": 8
        })
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"任务 {self._issue_id} 更新成功")

    # ==================== 删除 ====================

    def test_delete_issue(self, api_client_admin):
        """删除任务（软删除）"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 创建一个任务用于删除
        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "待删除任务",
            "type": "TASK",
            "priority": "LOWEST"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建任务用于删除测试")

        issue_id = resp.json()["data"]["id"]
        resp = api.issue.delete_issue(issue_id)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"任务 {issue_id} 删除成功")

        # 删除后应无法获取
        resp2 = api.issue.get_issue(issue_id)
        assert resp2.status_code == 404 or resp2.json().get("code") != 200

    def test_get_issue_no_token(self, api_client):
        """未登录获取任务应 401"""
        api = MimoAPI(api_client)
        resp = api.issue.get_issue(1)
        assert resp.status_code == 401
