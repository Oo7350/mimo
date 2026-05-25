"""团队模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class TeamAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def create_team(self, data: dict) -> requests.Response:
        return self.client._post("/api/teams", data)

    def get_my_teams(self) -> requests.Response:
        return self.client._get("/api/teams/my")

    def get_team_detail(self, team_id: int) -> requests.Response:
        return self.client._get(f"/api/teams/{team_id}")

    def get_team_members(self, team_id: int) -> requests.Response:
        return self.client._get(f"/api/teams/{team_id}/members")

    def invite_member(self, data: dict) -> requests.Response:
        return self.client._post("/api/teams/invite", data)

    def remove_member(self, team_id: int, user_id: int) -> requests.Response:
        return self.client._delete(f"/api/teams/{team_id}/members/{user_id}")
