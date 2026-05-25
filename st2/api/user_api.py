"""用户模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class UserAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def get_profile(self) -> requests.Response:
        return self.client._get("/api/user/profile")

    def update_profile(self, data: dict) -> requests.Response:
        return self.client._put("/api/user/profile", data)

    def update_password(self, data: dict) -> requests.Response:
        return self.client._put("/api/user/password", data)
