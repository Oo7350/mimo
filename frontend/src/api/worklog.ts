import request from "./request";

export interface WorkLogVO {
  id: number
  issueId: number
  issueKey?: string
  issueTitle?: string
  userId: number
  username?: string
  avatar?: string
  workDate: string
  hours: number
  description?: string
  createdAt: string
}

export interface MemberWorkloadVO {
  userId: number
  username?: string
  avatar?: string
  totalHours: number
  logCount: number
  issueCount: number
}

export interface SprintWorkloadVO {
  sprintId: number
  sprintName: string
  totalHours: number
  memberCount: number
  members: MemberWorkloadVO[]
}

export function createWorkLog(data: { issueId: number; workDate: string; hours: number; description?: string }) {
  return request.post("/worklogs", data)
}

export function listWorkLogsByIssue(issueId: number) {
  return request.get(`/worklogs/issue/${issueId}`)
}

export function deleteWorkLog(id: number) {
  return request.delete(`/worklogs/${id}`)
}

export function sumWorkLogByIssue(issueId: number) {
  return request.get(`/worklogs/issue/${issueId}/sum`)
}

export function getSprintWorkloadSummary(sprintId: number) {
  return request.get(`/worklogs/sprint/${sprintId}/summary`)
}
