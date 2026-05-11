import request from "./request";

export function createIssue(data: any) {
  return request.post("/issues", data);
}

export function getIssueById(id: number) {
  return request.get(`/issues/${id}`);
}

export function updateIssue(data: any) {
  return request.put("/issues", data);
}

export function deleteIssue(id: number) {
  return request.delete(`/issues/${id}`);
}

export function queryIssues(data: any) {
  return request.post("/issues/query", data);
}
