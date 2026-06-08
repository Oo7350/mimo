"""Mimo 任务模块 API 测试 —— 覆盖 STORY / TASK / BUG 三种类型"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog
from config import TEST_ACCOUNTS


class TestIssueAPI:
    """任务模块：CRUD + 查询 + 类型专属功能"""

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

        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            teams = api.team.get_my_teams().json()["data"]
            if not teams:
                api.team.create_team({"name": "任务测试团队", "description": "自动化"})
                teams = api.team.get_my_teams().json()["data"]
            api.project.create_project({
                "name": "全覆盖任务测试项目",
                "key": "FULL",
                "description": "STORY/TASK/BUG 全覆盖测试",
                "template": "SCRUM",
                "teamId": teams[0]["id"]
            })
            projects = api.project.get_my_projects().json()["data"]
        self._project_id = projects[0]["id"]

        board = api.board.get_board(self._project_id).json()["data"]
        self._column_id = board["columns"][0]["id"]

        return self._project_id, self._column_id

    # ==================== 创建任务 (TASK) ====================

    @pytest.mark.parametrize(
        "title,issue_type,priority,status,errno,errmsg",
        [(d.get("title"), d.get("type"), d.get("priority"),
          d["status"], d["errno"], d["errmsg"])
         for d in read_json("create_issue.json")]
    )
    def test_create_task_parametrized(
        self, api_client_admin, title, issue_type, priority, status, errno, errmsg
    ):
        """创建任务(TASK)参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        body = {
            "projectId": project_id,
            "columnId": column_id,
            "title": title,
            "type": issue_type,
            "priority": priority
        }

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

    # ==================== 创建故事 (STORY) ====================

    @pytest.mark.parametrize(
        "title,issue_type,priority,userRole,userGoal,businessValue,epic,storyPoints,status,errno",
        [(d["title"], d["type"], d["priority"],
          d.get("userRole", ""), d.get("userGoal", ""), d.get("businessValue", ""),
          d.get("epic", ""), d.get("storyPoints"), d["status"], d["errno"])
         for d in read_json("create_story.json")]
    )
    def test_create_story_parametrized(
        self, api_client_admin, title, issue_type, priority, userRole,
        userGoal, businessValue, epic, storyPoints, status, errno
    ):
        """创建故事(STORY)参数化测试 —— 含用户故事三要素 + 史诗"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        body = {
            "projectId": project_id,
            "columnId": column_id,
            "title": title,
            "type": issue_type,
            "priority": priority,
            "userRole": userRole,
            "userGoal": userGoal,
            "businessValue": businessValue,
            "epic": epic,
            "storyPoints": storyPoints
        }

        resp = api.issue.create_issue(body)
        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            data = resp_json["data"]
            assert data["type"] == "STORY"
            if userRole:
                assert data["userRole"] == userRole
            if userGoal:
                assert data["userGoal"] == userGoal
            if businessValue:
                assert data["businessValue"] == businessValue
            if epic:
                assert data["epic"] == epic
            self._issue_id = data["id"]
            self.logger.info(f"创建故事成功: {data.get('issueKey')} epic={epic}")

    # ==================== 创建缺陷 (BUG) ====================

    @pytest.mark.parametrize(
        "title,issue_type,priority,severity,stepsToRepro,environment,"
        "expectedResult,actualResult,foundVersion,status,errno",
        [(d["title"], d["type"], d["priority"], d["severity"],
          d.get("stepsToRepro", ""), d.get("environment", ""),
          d.get("expectedResult", ""), d.get("actualResult", ""),
          d.get("foundVersion", ""), d["status"], d["errno"])
         for d in read_json("create_bug.json")]
    )
    def test_create_bug_parametrized(
        self, api_client_admin, title, issue_type, priority, severity,
        stepsToRepro, environment, expectedResult, actualResult,
        foundVersion, status, errno
    ):
        """创建缺陷(BUG)参数化测试 —— 含完整缺陷报告要素"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        body = {
            "projectId": project_id,
            "columnId": column_id,
            "title": title,
            "type": issue_type,
            "priority": priority,
            "severity": severity,
            "stepsToRepro": stepsToRepro,
            "environment": environment,
            "expectedResult": expectedResult,
            "actualResult": actualResult,
            "foundVersion": foundVersion
        }

        resp = api.issue.create_issue(body)
        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            data = resp_json["data"]
            assert data["type"] == "BUG"
            assert data["severity"] == severity
            # BUG 创建后默认 bugStatus = NEW
            assert data["bugStatus"] == "NEW"
            if environment:
                assert environment in (data.get("environment") or "")
            if expectedResult:
                assert data["expectedResult"] == expectedResult
            if actualResult:
                assert data["actualResult"] == actualResult
            self._issue_id = data["id"]
            self.logger.info(
                f"创建缺陷成功: {data.get('issueKey')} "
                f"severity={severity} bugStatus=NEW"
            )

    # ==================== 缺陷状态流转 (BUG 专属) ====================

    @allure.feature("缺陷状态管理")
    @pytest.mark.parametrize(
        "from_status,to_status,expected_status,expected_errno,desc",
        [(d["from"], d["to"], d["status"], d["errno"], d["desc"])
         for d in read_json("bug_status.json")]
    )
    def test_bug_status_transition(
        self, api_client_admin, from_status, to_status,
        expected_status, expected_errno, desc
    ):
        """缺陷状态流转参数化测试 —— 覆盖合法与非法转换"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 创建一个 BUG
        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": f"状态流转测试 BUG ({desc})",
            "type": "BUG",
            "priority": "MEDIUM",
            "severity": "MAJOR"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 BUG 用于状态流转测试")
        bug_id = resp.json()["data"]["id"]

        # 如果需要前置状态，先逐步流转
        if from_status != "NEW":
            transition_path = {
                "CONFIRMED": ["NEW"],
                "IN_PROGRESS": ["NEW", "CONFIRMED"],
                "RESOLVED": ["NEW", "CONFIRMED", "IN_PROGRESS"],
                "VERIFIED": ["NEW", "CONFIRMED", "IN_PROGRESS", "RESOLVED"],
                "CLOSED": ["NEW", "CONFIRMED", "IN_PROGRESS", "RESOLVED", "VERIFIED"],
                "REOPENED": ["NEW", "CLOSED"],
            }
            path = transition_path.get(from_status, [])
            for step_status in path:
                resp = api.issue.update_bug_status(bug_id, step_status)
                if resp.json()["code"] != 200:
                    pytest.skip(f"无法将 BUG 从 NEW 流转到 {step_status}")

        # 执行目标状态转换
        resp = api.issue.update_bug_status(bug_id, to_status)
        assert resp.status_code == expected_status
        resp_json = resp.json()
        assert resp_json["code"] == expected_errno, (
            f"{desc}: 期望 code={expected_errno}, 实际 {resp_json}"
        )

        if expected_errno == 200:
            # 验证状态已更新
            detail = api.issue.get_issue(bug_id).json()["data"]
            assert detail["bugStatus"] == to_status, (
                f"BUG 状态应为 {to_status}, 实际为 {detail['bugStatus']}"
            )
            self.logger.info(f"BUG 状态流转成功: {from_status} → {to_status}")
        else:
            self.logger.info(f"BUG 状态流转被正确拒绝: {from_status} → {to_status}")

    # ==================== 验收标准管理 (STORY 专属) ====================

    @allure.feature("验收标准管理")
    @pytest.mark.parametrize(
        "text,expected_status,expected_errno,desc",
        [(d["text"], d["status"], d["errno"], d["desc"])
         for d in read_json("acceptance_criteria.json")]
    )
    def test_add_acceptance_criteria_parametrized(
        self, api_client_admin, text, expected_status, expected_errno, desc
    ):
        """添加验收标准参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 创建一个 STORY
        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "验收标准测试故事",
            "type": "STORY",
            "priority": "MEDIUM",
            "storyPoints": 5
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 STORY 用于验收标准测试")
        story_id = resp.json()["data"]["id"]

        resp = api.issue.add_acceptance_criteria(story_id, text)
        assert resp.status_code == expected_status
        resp_json = resp.json()
        assert resp_json["code"] == expected_errno

        if expected_errno == 200:
            criteria_list = resp_json["data"]
            assert len(criteria_list) > 0
            assert criteria_list[-1]["text"] == text
            assert criteria_list[-1]["done"] == False
            assert "id" in criteria_list[-1]
            self.logger.info(f"验收标准添加成功: {text}")

    def test_acceptance_criteria_full_lifecycle(self, api_client_admin):
        """验收标准完整生命周期：新增 → 勾选 → 更新文本 → 删除"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 创建 STORY
        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "AC 生命周期测试故事",
            "type": "STORY",
            "priority": "MEDIUM",
            "epic": "测试模块",
            "storyPoints": 3
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 STORY")
        story_id = resp.json()["data"]["id"]

        # 1. 新增验收标准
        resp = api.issue.add_acceptance_criteria(story_id, "登录响应不超过 3 秒")
        assert resp.json()["code"] == 200
        criteria_list = resp.json()["data"]
        assert len(criteria_list) == 1
        criteria_id = criteria_list[0]["id"]
        self.logger.info(f"验收标准已添加, id={criteria_id}")

        # 2. 勾选完成
        resp = api.issue.update_acceptance_criteria(
            story_id, criteria_id, {"done": True})
        assert resp.json()["code"] == 200
        updated = resp.json()["data"]
        assert updated[0]["done"] == True
        self.logger.info("验收标准已标记完成")

        # 3. 更新文本
        resp = api.issue.update_acceptance_criteria(
            story_id, criteria_id, {"text": "登录响应不超过 2 秒"})
        assert resp.json()["code"] == 200
        assert resp.json()["data"][0]["text"] == "登录响应不超过 2 秒"
        self.logger.info("验收标准文本已更新")

        # 4. 新增第二条
        resp = api.issue.add_acceptance_criteria(story_id, "支持 SSO 单点登录")
        assert resp.json()["code"] == 200
        assert len(resp.json()["data"]) == 2
        ac2_id = resp.json()["data"][1]["id"]

        # 5. 删除第一条
        resp = api.issue.delete_acceptance_criteria(story_id, criteria_id)
        assert resp.json()["code"] == 200
        assert len(resp.json()["data"]) == 1
        assert resp.json()["data"][0]["id"] == ac2_id
        self.logger.info("验收标准完整生命周期测试通过")

    def test_acceptance_criteria_bug_rejected(self, api_client_admin):
        """BUG 类型不能添加验收标准"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "缺陷不能有 AC",
            "type": "BUG",
            "priority": "MEDIUM",
            "severity": "MINOR"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 BUG")
        bug_id = resp.json()["data"]["id"]

        resp = api.issue.add_acceptance_criteria(bug_id, "这个不应该成功")
        assert resp.json()["code"] == 400
        self.logger.info("BUG 类型添加验收标准被正确拒绝")

    def test_bug_status_change_on_task_rejected(self, api_client_admin):
        """TASK 类型不能变更 bug_status"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "任务不能有缺陷状态",
            "type": "TASK",
            "priority": "MEDIUM"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 TASK")
        task_id = resp.json()["data"]["id"]

        resp = api.issue.update_bug_status(task_id, "CONFIRMED")
        assert resp.json()["code"] == 400
        self.logger.info("TASK 类型变更 bug_status 被正确拒绝")

    # ==================== 查询增强 ====================

    @pytest.mark.parametrize(
        "page,size,filters,status,errno,errmsg",
        [(d["page"], d["size"], d["filters"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("query_issues.json")]
    )
    def test_query_issues_parametrized(self, api_client_admin, page, size, filters, status, errno, errmsg):
        """查询任务参数化测试（含新筛选字段）"""
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
            data = resp_json["data"]
            records = data if isinstance(data, list) else data.get("records", data)
            self.logger.info(f"查询结果: {len(records)} 条记录")

    def test_query_by_bug_status(self, api_client_admin):
        """按 BUG 状态筛选"""
        api = MimoAPI(api_client_admin)
        project_id, _ = self._ensure_project_and_column(api)

        resp = api.issue.query_issues({
            "projectId": project_id,
            "type": "BUG",
            "bugStatus": "NEW",
            "size": 50
        })
        assert resp.json()["code"] == 200
        data = resp.json()["data"]
        records = data if isinstance(data, list) else data.get("records", data)
        for r in records:
            assert r["type"] == "BUG"
            assert r["bugStatus"] == "NEW"
        self.logger.info(f"按 BUG 状态筛选: {len(records)} 条 NEW 状态缺陷")

    def test_query_by_epic(self, api_client_admin):
        """按史诗(Epic)筛选 STORY"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 确保有史诗故事
        api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "Epic 筛选测试故事",
            "type": "STORY",
            "priority": "MEDIUM",
            "epic": "测试Epic筛选",
            "storyPoints": 3
        })

        resp = api.issue.query_issues({
            "projectId": project_id,
            "type": "STORY",
            "epic": "测试Epic筛选",
            "size": 10
        })
        assert resp.json()["code"] == 200
        data = resp.json()["data"]
        records = data if isinstance(data, list) else data.get("records", data)
        assert len(records) > 0
        for r in records:
            assert r["type"] == "STORY"
            assert r["epic"] == "测试Epic筛选"
        self.logger.info(f"按 Epic 筛选: {len(records)} 条")

    # ==================== 详情 & 更新 ====================

    def test_get_story_detail_with_ac(self, api_client_admin):
        """获取故事详情 —— 验证 acceptanceCriteria / subTasks / epic 字段"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "AC 详情验证故事",
            "type": "STORY",
            "priority": "HIGH",
            "epic": "验证模块",
            "userRole": "测试员",
            "userGoal": "验证 AC 字段",
            "businessValue": "确保数据完整",
            "storyPoints": 5
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 STORY")
        story_id = resp.json()["data"]["id"]

        # 添加验收标准
        api.issue.add_acceptance_criteria(story_id, "验收标准 A")
        api.issue.add_acceptance_criteria(story_id, "验收标准 B")

        # 获取详情
        resp = api.issue.get_issue(story_id)
        data = resp.json()["data"]
        assert data["type"] == "STORY"
        assert data["epic"] == "验证模块"
        assert data["userRole"] == "测试员"
        assert data["userGoal"] == "验证 AC 字段"
        assert data["businessValue"] == "确保数据完整"
        assert isinstance(data.get("acceptanceCriteria"), list)
        assert len(data["acceptanceCriteria"]) == 2
        assert data.get("subTasks") is not None  # 列表（可能为空）
        self.logger.info(
            f"故事详情验证通过: AC={len(data['acceptanceCriteria'])}条, "
            f"subTasks={len(data.get('subTasks') or [])}个"
        )

    def test_get_bug_detail_with_report(self, api_client_admin):
        """获取缺陷详情 —— 验证 bugStatus / environment / expectedResult 等字段"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "BUG 详情验证缺陷",
            "type": "BUG",
            "priority": "HIGH",
            "severity": "CRITICAL",
            "environment": "Windows 11; Edge 125; v2.0.0",
            "expectedResult": "页面正常渲染",
            "actualResult": "页面崩溃",
            "foundVersion": "2.0.0"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 BUG")
        bug_id = resp.json()["data"]["id"]

        # 获取详情
        resp = api.issue.get_issue(bug_id)
        data = resp.json()["data"]
        assert data["type"] == "BUG"
        assert data["bugStatus"] == "NEW"
        assert data["severity"] == "CRITICAL"
        assert "Windows 11" in (data.get("environment") or "")
        assert data["expectedResult"] == "页面正常渲染"
        assert data["actualResult"] == "页面崩溃"
        assert data["foundVersion"] == "2.0.0"
        self.logger.info(f"缺陷详情验证通过: bugStatus={data['bugStatus']}")

    def test_get_issue_detail(self, api_client_admin):
        """获取任务详情（兼容原有 TASK 测试）"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        if not self._issue_id:
            resp = api.issue.create_issue({
                "projectId": project_id,
                "columnId": column_id,
                "title": "详情测试任务(TASK)",
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
        self.logger.info(f"任务详情: {data['data'].get('issueKey', '')}")

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

    def test_update_bug_fields(self, api_client_admin):
        """更新缺陷专属字段"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "BUG 字段更新测试",
            "type": "BUG",
            "priority": "MEDIUM",
            "severity": "MAJOR"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 BUG")
        bug_id = resp.json()["data"]["id"]

        resp = api.issue.update_issue({
            "id": bug_id,
            "severity": "CRITICAL",
            "environment": "Linux; Firefox 130; v3.0.0",
            "expectedResult": "更新后期望",
            "actualResult": "更新后实际",
            "foundVersion": "3.0.0",
            "fixedVersion": "3.0.1"
        })
        assert resp.json()["code"] == 200

        # 验证更新生效
        detail = api.issue.get_issue(bug_id).json()["data"]
        assert detail["severity"] == "CRITICAL"
        assert "Linux" in (detail.get("environment") or "")
        assert detail["expectedResult"] == "更新后期望"
        assert detail["actualResult"] == "更新后实际"
        assert detail["fixedVersion"] == "3.0.1"
        self.logger.info("BUG 专属字段更新验证通过")

    def test_update_story_fields(self, api_client_admin):
        """更新故事专属字段"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "STORY 字段更新测试",
            "type": "STORY",
            "priority": "MEDIUM",
            "epic": "原始史诗"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建 STORY")
        story_id = resp.json()["data"]["id"]

        resp = api.issue.update_issue({
            "id": story_id,
            "userRole": "产品经理",
            "userGoal": "查看需求优先级排序",
            "businessValue": "合理安排迭代计划",
            "epic": "需求管理模块"
        })
        assert resp.json()["code"] == 200

        detail = api.issue.get_issue(story_id).json()["data"]
        assert detail["userRole"] == "产品经理"
        assert detail["userGoal"] == "查看需求优先级排序"
        assert detail["businessValue"] == "合理安排迭代计划"
        assert detail["epic"] == "需求管理模块"
        self.logger.info("STORY 专属字段更新验证通过")

    # ==================== TASK 挂载到 STORY ====================

    def test_task_parent_story(self, api_client_admin):
        """任务挂载到故事下"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

        # 创建 STORY
        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "父故事",
            "type": "STORY",
            "priority": "MEDIUM",
            "epic": "父子关系测试",
            "storyPoints": 8
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建父 STORY")
        parent_id = resp.json()["data"]["id"]

        # 创建子 TASK
        resp = api.issue.create_issue({
            "projectId": project_id,
            "columnId": column_id,
            "title": "子任务_前端开发",
            "type": "TASK",
            "priority": "HIGH",
            "parentId": parent_id
        })
        assert resp.json()["code"] == 200
        child_data = resp.json()["data"]
        assert child_data["parentId"] == parent_id
        assert child_data.get("parentIssueKey") is not None

        # 通过父 STORY 的 subTasks 验证
        detail = api.issue.get_issue(parent_id).json()["data"]
        assert detail.get("subTasks") is not None
        sub_ids = [s["id"] for s in detail["subTasks"]]
        assert child_data["id"] in sub_ids
        self.logger.info(
            f"父子关系验证通过: STORY {parent_id} → TASK {child_data['id']}"
        )

    # ==================== 删除 ====================

    def test_delete_issue(self, api_client_admin):
        """删除任务（软删除）"""
        api = MimoAPI(api_client_admin)
        project_id, column_id = self._ensure_project_and_column(api)

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
