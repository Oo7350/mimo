import requests
from api_config import API_BASE_URL


class MimoAPI:
    """Mimo API接口类"""

    def __init__(self):
        self.base_url = API_BASE_URL
        self.session = requests.Session()
        self.token = None

    def _get_headers(self):
        """获取请求头，包含Token"""
        headers = {
            "Content-Type": "application/json"
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    # ==================== 认证模块 ====================

    def register(self, data):
        """用户注册"""
        url = f"{self.base_url}/api/auth/register"
        return requests.post(url, json=data)

    def login(self, data):
        """用户登录"""
        url = f"{self.base_url}/api/auth/login"
        response = requests.post(url, json=data)
        # 如果登录成功，保存token
        if response.status_code == 200 and response.json().get("code") == 200:
            self.token = response.json()["data"]["token"]
        return response

    # ==================== 团队模块 ====================

    def create_team(self, data):
        """创建团队"""
        url = f"{self.base_url}/api/teams"
        return requests.post(url, json=data, headers=self._get_headers())

    def get_my_teams(self):
        """获取我的团队列表"""
        url = f"{self.base_url}/api/teams/my"
        return requests.get(url, headers=self._get_headers())

    def get_team_detail(self, team_id):
        """获取团队详情"""
        url = f"{self.base_url}/api/teams/{team_id}"
        return requests.get(url, headers=self._get_headers())

    def get_team_members(self, team_id):
        """获取团队成员列表"""
        url = f"{self.base_url}/api/teams/{team_id}/members"
        return requests.get(url, headers=self._get_headers())

    def invite_member(self, data):
        """邀请成员"""
        url = f"{self.base_url}/api/teams/invite"
        return requests.post(url, json=data, headers=self._get_headers())

    def remove_member(self, team_id, user_id):
        """移除成员"""
        url = f"{self.base_url}/api/teams/{team_id}/members/{user_id}"
        return requests.delete(url, headers=self._get_headers())
