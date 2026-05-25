"""仪表盘模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class DashboardAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def get_dashboard(self) -> requests.Response:
        return self.client._get("/api/dashboard")
