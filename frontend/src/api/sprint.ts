import request from "./request";

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
