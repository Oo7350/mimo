"""Sprint 模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class SprintAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def create_sprint(self, data: dict) -> requests.Response:
        return self.client._post("/api/sprints", data)

    def get_sprint(self, sprint_id: int) -> requests.Response:
        return self.client._get(f"/api/sprints/{sprint_id}")

    def get_sprints_by_project(self, project_id: int) -> requests.Response:
        return self.client._get(f"/api/sprints/project/{project_id}")

    def start_sprint(self, sprint_id: int) -> requests.Response:
        return self.client._put(f"/api/sprints/{sprint_id}/start", {})

    def complete_sprint(self, sprint_id: int) -> requests.Response:
        return self.client._put(f"/api/sprints/{sprint_id}/complete", {})
