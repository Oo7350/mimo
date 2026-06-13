import request from './request'

export function createApproval(data: any) {
  return request.post('/approvals', data)
}

export function getPendingApprovals(teamId: number) {
  return request.get(`/approvals/team/${teamId}/pending`)
}

export function getMyApprovals() {
  return request.get('/approvals/my')
}

export function approveRequest(id: number) {
  return request.put(`/approvals/${id}/approve`)
}

export function rejectRequest(id: number, reason?: string) {
  return request.put(`/api/approvals/${id}/reject`, null, { params: { reason } })
}

export function withdrawApproval(id: number) {
  return request.put(`/api/approvals/${id}/withdraw`)
}

export function cleanupExpiredApprovals() {
  return request.post('/api/approvals/cleanup-expired')
}
