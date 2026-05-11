import request from "./request";

export function getMyProjects() {
  return request.get("/projects/my");
}

export function getProjectById(id: number) {
  return request.get(`/projects/${id}`);
}

export function getProjectsByTeam(teamId: number) {
  return request.get(`/projects/team/${teamId}`);
}

export function createProject(data: { name: string; key: string; description?: string; template?: string; teamId: number }) {
  return request.post("/projects", data);
}

export function addProjectMember(projectId: number, userId: number) {
  return request.post(`/projects/${projectId}/members/${userId}`);
}
