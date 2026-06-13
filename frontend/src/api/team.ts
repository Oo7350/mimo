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

export function deleteTeam(teamId: number) {
  return request.delete(`/teams/${teamId}`);
}

export function getTeamRole(teamId: number) {
  return request.get(`/teams/${teamId}/role`)
}

export function leaveTeam(teamId: number) {
  return request.post(`/teams/${teamId}/leave`)
}

export function transferOwner(teamId: number, newOwnerId: number) {
  return request.put(`/teams/${teamId}/transfer-owner`, null, { params: { newOwnerId } })
}

export function setMemberAdmin(teamId: number, userId: number) {
  return request.put(`/teams/${teamId}/members/${userId}/set-admin`)
}

export function unsetMemberAdmin(teamId: number, userId: number) {
  return request.put(`/teams/${teamId}/members/${userId}/unset-admin`)
}
