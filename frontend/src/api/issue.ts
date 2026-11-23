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

// ---- 认领：管理员直接认领并推进，普通成员提交审批 ----

export function claimIssue(id: number) {
  return request.post(`/issues/${id}/claim`);
}

// ---- BUG status management ----

export function updateBugStatus(id: number, data: { bugStatus: string }) {
  return request.put(`/issues/${id}/bug-status`, data);
}

// ---- Acceptance Criteria management (STORY only) ----

export function addAcceptanceCriteria(id: number, data: { text: string }) {
  return request.post(`/issues/${id}/acceptance-criteria`, data);
}

export function updateAcceptanceCriteria(id: number, criteriaId: string, data: { text?: string; done?: boolean }) {
  return request.put(`/issues/${id}/acceptance-criteria/${criteriaId}`, data);
}

export function deleteAcceptanceCriteria(id: number, criteriaId: string) {
  return request.delete(`/issues/${id}/acceptance-criteria/${criteriaId}`);
}
