"""项目模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class ProjectAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def create_project(self, data: dict) -> requests.Response:
        return self.client._post("/api/projects", data)

    def get_my_projects(self) -> requests.Response:
        return self.client._get("/api/projects/my")

    def get_project_detail(self, project_id: int) -> requests.Response:
        return self.client._get(f"/api/projects/{project_id}")

    def get_projects_by_team(self, team_id: int) -> requests.Response:
        return self.client._get(f"/api/projects/team/{team_id}")

    def add_project_member(self, project_id: int, user_id: int) -> requests.Response:
        return self.client._post(f"/api/projects/{project_id}/members/{user_id}", {})
