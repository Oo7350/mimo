"""任务模块 API"""
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
