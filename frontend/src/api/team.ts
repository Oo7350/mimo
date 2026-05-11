import request from "./request";

export function getMyTeams() {
  return request.get("/teams/my");
}

export function getTeamById(id: number) {
  return request.get(`/teams/${id}`);
}

export function createTeam(data: { name: string; description?: string }) {
  return request.post("/teams", data);
}

export function inviteMember(data: { teamId: number; userId: number; role?: string }) {
  return request.post("/teams/invite", data);
}

export function getMembers(teamId: number) {
  return request.get(`/teams/${teamId}/members`);
}

export function removeMember(teamId: number, userId: number) {
  return request.delete(`/teams/${teamId}/members/${userId}`);
}
