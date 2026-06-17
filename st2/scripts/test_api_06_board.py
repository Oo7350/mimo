"""
调用完整流程：
mimo_api_client是最底层，定义了各种方法，方法需要传递各种参数。
每个接口有自己的接口类，接口类需要传入client对象，client对象可以调用mimo_api_client的方法。
MimoAPI，它封装了所有模块的接口。
conftest通过调用mimo_api_client的方法，返回api_client对象。（不同的测试类用同一个api_client对象）
在测试类中，使用conftest返回的api_client对象，调用MimoAPI的方法，可以用同一个帐号测试不同的接口。
"""
"""Mimo 看板模块 API 测试"""
import pytest
import allure
from api import MimoAPI
from tools.read_data import read_json
from tools.logger import GetLog


class TestBoardAPI:
    """看板模块：看板查看 + 列 CRUD + 排序 + 拖动"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()
        cls._project_id = None
        cls._column_ids = []

    def _ensure_project(self, api):
        """确保有可用的项目，返回 project_id"""
        if self._project_id:
            return self._project_id
        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            teams = api.team.get_my_teams().json()["data"]
            if not teams:
                api.team.create_team({"name": "看板测试团队", "description": "自动化"})
                teams = api.team.get_my_teams().json()["data"]
            resp = api.project.create_project({
                "name": "看板测试项目",
                "key": "BOARD",
                "description": "自动化测试",
                "template": "SCRUM",
                "teamId": teams[0]["id"]
            })
            projects = api.project.get_my_projects().json()["data"]
        self._project_id = projects[0]["id"]
        return self._project_id

    # ==================== 获取看板 ====================

    def test_get_board(self, api_client_admin):
        """获取项目看板"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        resp = api.board.get_board(project_id)
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        board = data["data"]
        assert board["projectId"] == project_id
        assert "columns" in board
        assert isinstance(board["columns"], list)
        self.logger.info(f"看板列数: {len(board['columns'])}")

    # ==================== 列 CRUD ====================

    @pytest.mark.parametrize(
        "name,color,status,errno,errmsg",
        [(d["name"], d["color"], d["status"], d["errno"], d["errmsg"])
         for d in read_json("create_column.json")]
    )
    def test_create_column_parametrized(self, api_client_admin, name, color, status, errno, errmsg):
        """创建看板列参数化测试"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        resp = api.board.create_column({
            "projectId": project_id,
            "name": name,
            "color": color
        })

        assert resp.status_code == status
        resp_json = resp.json()
        assert resp_json["code"] == errno

        if errno == 200:
            assert "data" in resp_json
            self._column_ids.append(resp_json["data"]["id"])
            self.logger.info(f"创建列成功: {resp_json['data']}")

    def test_edit_column(self, api_client_admin):
        """编辑看板列"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        # 获取现有列
        board = api.board.get_board(project_id).json()["data"]
        if not board["columns"]:
            pytest.skip("没有可用的看板列")

        column_id = board["columns"][0]["id"]
        resp = api.board.edit_column({
            "id": column_id,
            "name": "编辑后的列名",
            "color": "#F56C6C"
        })
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info("编辑列成功")

    # ==================== 排序列 ====================

    def test_sort_columns(self, api_client_admin):
        """排序看板列"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        board = api.board.get_board(project_id).json()["data"]
        column_ids = [c["id"] for c in board["columns"]]
        if len(column_ids) < 2:
            pytest.skip("列不足2个，无法测试排序")

        # 反转顺序
        reversed_ids = list(reversed(column_ids))
        resp = api.board.sort_columns(reversed_ids)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"排序列: {column_ids} → {reversed_ids}")

    # ==================== 删除列 ====================

    def test_delete_column(self, api_client_admin):
        """删除空的看板列"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        # 创建一个新列用于删除
        resp = api.board.create_column({
            "projectId": project_id,
            "name": "待删除列",
            "color": "#909399"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建测试列")

        column_id = resp.json()["data"]["id"]
        resp = api.board.delete_column(column_id)
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"删除列成功: {column_id}")

    # ==================== 拖动任务 ====================

    def test_move_issue(self, api_client_admin):
        """拖动任务到其他列"""
        api = MimoAPI(api_client_admin)
        project_id = self._ensure_project(api)

        board = api.board.get_board(project_id).json()["data"]
        columns = board["columns"]
        if len(columns) < 2:
            pytest.skip("列不足2个，无法测试拖动")

        # 确保第一个列有任务
        col1 = columns[0]
        if not col1.get("issues"):
            api.issue.create_issue({
                "projectId": project_id,
                "columnId": col1["id"],
                "title": "拖动测试任务",
                "type": "TASK",
                "priority": "LOW"
            })
            board = api.board.get_board(project_id).json()["data"]
            col1 = board["columns"][0]

        if not col1.get("issues"):
            pytest.skip("第一列没有任务可拖动")

        issue_id = col1["issues"][0]["id"]
        target_col = columns[1]

        resp = api.board.move_issue({
            "issueId": issue_id,
            "targetColumnId": target_col["id"],
            "sortOrder": 0
        })
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
        self.logger.info(f"拖动任务 {issue_id} → 列 {target_col['id']}")

    def test_get_board_no_token(self, api_client):
        """未登录获取看板应 401"""
        api = MimoAPI(api_client)
        resp = api.board.get_board(1)
        assert resp.status_code == 401
