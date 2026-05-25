"""看板模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class BoardAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def get_board(self, project_id: int) -> requests.Response:
        return self.client._get(f"/api/board/{project_id}")

    def create_column(self, data: dict) -> requests.Response:
        return self.client._post("/api/board/column", data)

    def edit_column(self, data: dict) -> requests.Response:
        return self.client._put("/api/board/column", data)

    def delete_column(self, column_id: int) -> requests.Response:
        return self.client._delete(f"/api/board/column/{column_id}")

    def sort_columns(self, data: list) -> requests.Response:
        return self.client._put("/api/board/column/sort", data)

    def move_issue(self, data: dict) -> requests.Response:
        return self.client._put("/api/board/issue/move", data)
