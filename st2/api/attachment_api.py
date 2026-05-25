"""附件模块 API"""
import requests
from api.mimo_api_client import MimoAPIClient


class AttachmentAPI:
    def __init__(self, client: MimoAPIClient):
        self.client = client

    def upload(self, issue_id: int, file_path: str) -> requests.Response:
        """上传附件（multipart/form-data）"""
        with open(file_path, "rb") as f:
            files = {"file": (file_path.split("/")[-1].split("\\")[-1], f)}
            data = {"issueId": str(issue_id)}
            return self.client._upload("/api/attachments/upload", files=files, data=data)

    def get_issue_attachments(self, issue_id: int) -> requests.Response:
        return self.client._get(f"/api/attachments/issue/{issue_id}")

    def download(self, attachment_id: int) -> requests.Response:
        return self.client._get(f"/api/attachments/{attachment_id}/download")

    def delete(self, attachment_id: int) -> requests.Response:
        return self.client._delete(f"/api/attachments/{attachment_id}")
