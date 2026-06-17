"""Mimo API 门面 —— 统一访问所有模块"""
from api.mimo_api_client import MimoAPIClient
from api.auth_api import AuthAPI
from api.user_api import UserAPI
from api.dashboard_api import DashboardAPI
from api.team_api import TeamAPI
from api.project_api import ProjectAPI
from api.board_api import BoardAPI
from api.issue_api import IssueAPI
from api.sprint_api import SprintAPI
from api.burndown_api import BurndownAPI
from api.report_api import ReportAPI
from api.attachment_api import AttachmentAPI


class MimoAPI:
    """Mimo 全量 API 门面
    MimoAPI 是 API 的统一入口，封装了所有模块的 API
    """
    def __init__(self, client: MimoAPIClient):
        self.client = client
        self.auth = AuthAPI(client)
        self.user = UserAPI(client)
        self.dashboard = DashboardAPI(client)
        self.team = TeamAPI(client)
        self.project = ProjectAPI(client)
        self.board = BoardAPI(client)
        self.issue = IssueAPI(client)
        self.sprint = SprintAPI(client)
        self.burndown = BurndownAPI(client)
        self.report = ReportAPI(client)
        self.attachment = AttachmentAPI(client)
