import request from "./request";

export function getIssueAttachments(issueId: number) {
  return request.get(`/attachments/issue/${issueId}`);
}

export function uploadAttachment(issueId: number, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("issueId", String(issueId));
  return request.post("/attachments/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export function downloadAttachment(id: number) {
  return request.get(`/attachments/${id}/download`, {
    responseType: "blob",
  });
}

export function deleteAttachment(id: number) {
  return request.delete(`/attachments/${id}`);
}
