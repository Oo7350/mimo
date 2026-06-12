import request from './request'

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
