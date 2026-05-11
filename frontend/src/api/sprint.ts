import request from "./request";

export function createSprint(data: { projectId: number; name: string; goal?: string; startDate: string; endDate: string }) {
  return request.post("/sprints", data);
}

export function getSprintById(id: number) {
  return request.get(`/sprints/${id}`);
}

export function getSprintsByProject(projectId: number) {
  return request.get(`/sprints/project/${projectId}`);
}

export function startSprint(id: number) {
  return request.put(`/sprints/${id}/start`);
}

export function completeSprint(id: number) {
  return request.put(`/sprints/${id}/complete`);
}

export function getBurndown(sprintId: number) {
  return request.get(`/sprints/${sprintId}/burndown`);
}

export function takeSnapshot(sprintId: number) {
  return request.post(`/sprints/${sprintId}/snapshot`);
}
