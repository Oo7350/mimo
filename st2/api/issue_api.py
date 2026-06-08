"""任务模块 API —— 支持 STORY / TASK / BUG 三种类型"""
import requests
from api.mimo_api_client import MimoAPIClient


class IssueAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def create_issue(self, data: dict) -> requests.Response:
        return self.client._post("/api/issues", data)

    def get_issue(self, issue_id: int) -> requests.Response:
        return self.client._get(f"/api/issues/{issue_id}")

    def update_issue(self, data: dict) -> requests.Response:
        return self.client._put("/api/issues", data)

    def delete_issue(self, issue_id: int) -> requests.Response:
        return self.client._delete(f"/api/issues/{issue_id}")

    def query_issues(self, data: dict) -> requests.Response:
        return self.client._post("/api/issues/query", data)

    # ---- BUG 状态管理 ----

    def update_bug_status(self, issue_id: int, bug_status: str) -> requests.Response:
        return self.client._put(f"/api/issues/{issue_id}/bug-status",
                                {"issueId": issue_id, "bugStatus": bug_status})

    # ---- 验收标准管理 (STORY 专属) ----

    def add_acceptance_criteria(self, issue_id: int, text: str) -> requests.Response:
        return self.client._post(f"/api/issues/{issue_id}/acceptance-criteria", {"text": text})

    def update_acceptance_criteria(self, issue_id: int, criteria_id: str,
                                   data: dict) -> requests.Response:
        return self.client._put(
            f"/api/issues/{issue_id}/acceptance-criteria/{criteria_id}", data)

    def delete_acceptance_criteria(self, issue_id: int, criteria_id: str) -> requests.Response:
        return self.client._delete(f"/api/issues/{issue_id}/acceptance-criteria/{criteria_id}")
