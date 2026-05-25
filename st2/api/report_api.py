"""报告模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class ReportAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def generate_report(self, data: dict) -> requests.Response:
        return self.client._post("/api/reports/generate", data)

    def get_reports(self, params: dict = None) -> requests.Response:
        """查询报告列表，可选 type、projectId 等参数"""
        query = ""
        if params:
            parts = [f"{k}={v}" for k, v in params.items() if v is not None]
            if parts:
                query = "?" + "&".join(parts)
        return self.client._get(f"/api/reports{query}")

    def get_report(self, report_id: int) -> requests.Response:
        return self.client._get(f"/api/reports/{report_id}")

    def update_report(self, data: dict) -> requests.Response:
        return self.client._put("/api/reports", data)

    def submit_report(self, report_id: int) -> requests.Response:
        return self.client._put(f"/api/reports/{report_id}/submit", {})
