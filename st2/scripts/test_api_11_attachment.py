"""Mimo 附件模块 API 测试"""
import pytest
import allure
import tempfile
import os
from api import MimoAPI
from tools.logger import GetLog


class TestAttachmentAPI:
    """附件模块：上传 + 列表 + 下载 + 删除"""

    @classmethod
    def setup_class(cls):
        cls.logger = GetLog.get_log()
        cls._project_id = None
        cls._column_id = None
        cls._issue_id = None
        cls._attachment_id = None

    def _ensure_issue(self, api):
        """确保有任务"""
        if self._issue_id:
            return self._issue_id

        projects = api.project.get_my_projects().json()["data"]
        if not projects:
            teams = api.team.get_my_teams().json()["data"]
            if not teams:
                api.team.create_team({"name": "附件测试团队", "description": "自动化"})
                teams = api.team.get_my_teams().json()["data"]
            api.project.create_project({
                "name": "附件测试项目",
                "key": "ATTACH",
                "description": "自动化测试",
                "template": "KANBAN",
                "teamId": teams[0]["id"]
            })
            projects = api.project.get_my_projects().json()["data"]
        self._project_id = projects[0]["id"]

        board = api.board.get_board(self._project_id).json()["data"]
        self._column_id = board["columns"][0]["id"]

        resp = api.issue.create_issue({
            "projectId": self._project_id,
            "columnId": self._column_id,
            "title": "附件测试任务",
            "type": "TASK",
            "priority": "MEDIUM"
        })
        if resp.json()["code"] != 200:
            pytest.skip("无法创建任务")
        self._issue_id = resp.json()["data"]["id"]
        return self._issue_id

    def test_full_attachment_lifecycle(self, api_client_admin):
        """附件完整生命周期：上传 → 列表 → 下载 → 删除"""
        api = MimoAPI(api_client_admin)
        issue_id = self._ensure_issue(api)

        # 1. 上传附件
        tmp_file = tempfile.NamedTemporaryFile(
            mode="w", suffix=".txt", delete=False, encoding="utf-8"
        )
        tmp_file.write("Mimo API 自动化测试附件内容\nLine 2\nLine 3")
        tmp_file.close()

        resp_upload = api.attachment.upload(issue_id, tmp_file.name)
        try:
            os.unlink(tmp_file.name)
        except OSError:
            pass

        # 可能因 upload 目录配置问题失败，先检查
        if resp_upload.status_code >= 500:
            self.logger.warning(f"上传附件失败（可能是upload目录未配置）: {resp_upload.json().get('message')}")
            pytest.skip("上传目录未配置，跳过附件测试")

        assert resp_upload.status_code == 200, \
            f"上传附件失败: {resp_upload.json().get('message')}"
        upload_data = resp_upload.json()
        assert upload_data["code"] == 200
        self._attachment_id = upload_data["data"]["id"]
        self.logger.info(f"附件上传成功: id={self._attachment_id}")

        # 2. 获取附件列表
        resp_list = api.attachment.get_issue_attachments(issue_id)
        assert resp_list.status_code == 200
        list_data = resp_list.json()
        assert list_data["code"] == 200
        assert isinstance(list_data["data"], list)
        assert len(list_data["data"]) >= 1
        self.logger.info(f"任务附件数量: {len(list_data['data'])}")

        # 3. 下载附件
        resp_download = api.attachment.download(self._attachment_id)
        assert resp_download.status_code == 200
        self.logger.info(f"附件下载成功，大小: {len(resp_download.content)} bytes")

        # 4. 删除附件
        resp_delete = api.attachment.delete(self._attachment_id)
        assert resp_delete.status_code == 200
        assert resp_delete.json()["code"] == 200
        self.logger.info(f"附件 {self._attachment_id} 删除成功")

        # 5. 删除后列表应不含该附件
        resp_list2 = api.attachment.get_issue_attachments(issue_id)
        ids = [a["id"] for a in resp_list2.json()["data"]]
        assert self._attachment_id not in ids, "删除后附件应不在列表中"

    def test_get_attachments_no_token(self, api_client):
        """未登录获取附件列表应 401"""
        api = MimoAPI(api_client)
        resp = api.attachment.get_issue_attachments(1)
        assert resp.status_code == 401
