import request from './request'
import type {
  FieldDiff,
  IssueChangeRequest,
  IssueImpact,
  IssueReview,
  IssueVersion,
} from '@/types/governance'

export function listIssueVersions(issueId: number) {
  return request.get<IssueVersion[]>(`/issues/${issueId}/versions`)
}

export function getIssueVersion(issueId: number, versionNo: number) {
  return request.get<Record<string, unknown>>(`/issues/${issueId}/versions/${versionNo}`)
}

export function diffIssueVersions(issueId: number, from: number, to: number) {
  return request.get<FieldDiff[]>(`/issues/${issueId}/versions/diff`, { params: { from, to } })
}

export function listIssueChangeRequests(issueId: number) {
  return request.get<IssueChangeRequest[]>(`/issues/${issueId}/change-requests`)
}

export function submitIssueChangeRequest(
  issueId: number,
  data: {
    baseVersion: number
    proposedChanges: Record<string, unknown>
    changeReason: string
  },
) {
  return request.post<IssueChangeRequest>(`/issues/${issueId}/change-requests`, data)
}

export function listPendingIssueChangeRequests() {
  return request.get<IssueChangeRequest[]>('/issue-change-requests/pending')
}

export function getIssueChangeRequestImpact(id: number) {
  return request.get<IssueImpact>(`/issue-change-requests/${id}/impact`)
}

export function approveIssueChangeRequest(id: number, note?: string) {
  return request.put<IssueChangeRequest>(`/issue-change-requests/${id}/approve`, { note })
}

export function rejectIssueChangeRequest(id: number, note: string) {
  return request.put<IssueChangeRequest>(`/issue-change-requests/${id}/reject`, { note })
}

export function withdrawIssueChangeRequest(id: number) {
  return request.put(`/issue-change-requests/${id}/withdraw`)
}

export function listIssueReviews(issueId: number) {
  return request.get<IssueReview[]>(`/issues/${issueId}/reviews`)
}

export function submitIssueReview(
  issueId: number,
  data: { completionNote?: string; evidenceUrls?: string[] },
) {
  return request.post<IssueReview>(`/issues/${issueId}/reviews`, data)
}

export function approveIssueReview(id: number, note?: string) {
  return request.put<IssueReview>(`/issue-reviews/${id}/approve`, { note })
}

export function rejectIssueReview(id: number, note: string) {
  return request.put<IssueReview>(`/issue-reviews/${id}/reject`, { note })
}
