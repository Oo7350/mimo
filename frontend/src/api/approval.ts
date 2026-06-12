import request from '@/utils/request'

// 审批请求相关
export function createApproval(data: any) {
  return request.post('/api/approvals', data)
}

export function getPendingApprovals(teamId: number) {
  return request.get(`/api/approvals/team/${teamId}/pending`)
}

export function getMyApprovals() {
  return request.get('/api/approvals/my')
}

export function approveRequest(id: number) {
  return request.put(`/api/approvals/${id}/approve`)
}

export function rejectRequest(id: number, reason?: string) {
  return request.put(`/api/approvals/${id}/reject`, null, { params: { reason } })
}

// 用户等级相关
export function getMyLevel() {
  return request.get('/api/user-levels/me')
}

export function getUserLevel(userId: number) {
  return request.get(`/api/user-levels/${userId}`)
}

export function getAllUserLevels() {
  return request.get('/api/user-levels')
}

export function setUserLevel(userId: number, level: number) {
  return request.put(`/api/user-levels/${userId}`, null, { params: { level } })
}
