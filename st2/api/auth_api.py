"""认证模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class AuthAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def register(self, data: dict) -> requests.Response:
        return self.client._post("/api/auth/register", data)

    def login(self, data: dict) -> requests.Response:
        return self.client._post("/api/auth/login", data)
