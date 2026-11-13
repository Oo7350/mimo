import request from './request'

export interface VelocitySprintVO {
  sprintId: number
  sprintName: string
  status: 'ACTIVE' | 'COMPLETED'
  startDate: string
  endDate: string
  committedPoints: number
  completedPoints: number
  completionRate: number
}

export interface VelocityVO {
  projectId: number
  averageCompletedPoints: number
  averageSprintCount: number
  sprints: VelocitySprintVO[]
}

export interface CfdPoint {
  date: string
  todoCount: number
  inProgressCount: number
  pendingReviewCount: number
  doneCount: number
  totalCount: number
}

export interface CfdVO {
  projectId: number
  sprintId?: number
  fromDate: string
  toDate: string
  points: CfdPoint[]
}

export function getProjectVelocity(projectId: number, limit = 6) {
  return request.get<VelocityVO>(`/analytics/project/${projectId}/velocity`, {
    params: { limit },
  })
}

export function getProjectCfd(
  projectId: number,
  params: { sprintId?: number; from?: string; to?: string } = {},
) {
  return request.get<CfdVO>(`/analytics/project/${projectId}/cfd`, { params })
}
