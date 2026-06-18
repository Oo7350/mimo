import request from "./request";

export interface SprintVO {
  id: number
  name: string
  goal?: string
  startDate: string
  endDate: string
  isActive: boolean
  status: string
  totalIssues?: number
  completedIssues?: number
  createdAt: string
}

export function getSprints(projectId: number) {
  return request.get(`/sprints/project/${projectId}`);
}

export function getSprintById(id: number) {
  return request.get(`/sprints/${id}`);
}

export function getBurndown(sprintId: number) {
  return request.get(`/sprints/${sprintId}/burndown`);
}

export function getSprintStats(sprintId: number) {
  return request.get(`/sprints/${sprintId}/stats`);
}

export function createSprint(data: { projectId: number; name: string; startDate: string; endDate: string; goal?: string }) {
  return request.post("/sprints", data);
}

export function startSprint(id: number) {
  return request.put(`/sprints/${id}/start`);
}

export function completeSprint(id: number) {
  return request.put(`/sprints/${id}/complete`);
}

export function quickStartSprint(projectId: number) {
  return request.post(`/sprints/quick/${projectId}`);
}

export function takeSnapshot(sprintId: number, data?: { doneIssues?: number; remainingIssues?: number }) {
  return request.post(`/sprints/${sprintId}/snapshot`, data || {});
}

export function completeMigration(sprintId: number) {
  return request.put(`/sprints/${sprintId}/complete-migration`);
}

export function addToSprint(issueId: number, sprintId: number) {
  return request.put(`/sprints/${issueId}/add-to-sprint/${sprintId}`);
}
