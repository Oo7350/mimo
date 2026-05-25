"""燃尽图模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class BurndownAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def get_burndown(self, sprint_id: int) -> requests.Response:
        return self.client._get(f"/api/sprints/{sprint_id}/burndown")

    def create_snapshot(self, sprint_id: int) -> requests.Response:
        return self.client._post(f"/api/sprints/{sprint_id}/snapshot", {})
