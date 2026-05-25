"""Mimo API 核心客户端 —— 封装 requests.Session + JWT Token 管理"""
import requests
from config import API_BASE_URL


class MimoAPIClient:
    """Mimo API HTTP 客户端"""

    def __init__(self, base_url: str = None):
        self.base_url = (base_url or API_BASE_URL).rstrip("/")
        self.session = requests.Session()
        self.token = None

    def login(self, username: str, password: str) -> dict:
        """登录并保存 token，返回解析后的响应 data"""
        url = f"{self.base_url}/api/auth/login"
        resp = self.session.post(url, json={
            "username": username,
            "password": password
        })
        resp_json = resp.json()
        if resp.status_code == 200 and resp_json.get("code") == 200:
            self.token = resp_json["data"]["token"]
        return resp_json

    def _get_headers(self, extra: dict = None) -> dict:
        """构建请求头"""
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if extra:
            headers.update(extra)
        return headers

    def _get(self, path: str, **kwargs) -> requests.Response:
        return self.session.get(
            f"{self.base_url}{path}",
            headers=self._get_headers(),
            **kwargs
        )

    def _post(self, path: str, data: dict) -> requests.Response:
        return self.session.post(
            f"{self.base_url}{path}",
            json=data,
            headers=self._get_headers()
        )

    def _put(self, path: str, data: dict) -> requests.Response:
        return self.session.put(
            f"{self.base_url}{path}",
            json=data,
            headers=self._get_headers()
        )

    def _delete(self, path: str) -> requests.Response:
        return self.session.delete(
            f"{self.base_url}{path}",
            headers=self._get_headers()
        )

    def _upload(self, path: str, files: dict, data: dict = None) -> requests.Response:
        """multipart/form-data 上传（附件等）"""
        headers = {}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return self.session.post(
            f"{self.base_url}{path}",
            files=files,
            data=data or {},
            headers=headers
        )
