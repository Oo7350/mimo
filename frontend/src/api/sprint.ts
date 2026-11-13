import request from "./request";

export interface SprintVO {
  id: number
  name: string
  goal?: string
  startDate: string
  endDate: string
  isActive: boolean
  status: string
  totalIssues?: number
  completedIssues?: number
  createdAt: string
}

export type SprintHealthLevel = 'HEALTHY' | 'WATCH' | 'AT_RISK'

export interface SprintHealthVO {
  sprintId: number
  sprintName: string
  projectId: number
  projectName?: string
  sprintStatus: string
  startDate: string
  endDate: string
  calculatedDate: string
  totalDays: number
  elapsedDays: number
  remainingDays: number
  timeProgress: number
  workProgress: number
  progressGap: number
  totalIssues: number
  completedIssues: number
  totalStoryPoints: number
  completedStoryPoints: number
  remainingStoryPoints: number
  trackingBasis: 'STORY_POINTS' | 'ISSUE_COUNT'
  overdueCount: number
  blockedCount: number
  pendingReviewCount: number
  estimatedDelayDays: number
  healthScore: number
  healthLevel: SprintHealthLevel
  riskReasons: string[]
  suggestions: string[]
  calculatedAt: string
}

export interface SprintHealthAiSummaryVO {
  summary: string
  aiAvailable: boolean
  provider?: string
  deterministicSuggestions: string[]
}

export interface SprintPlanCandidate {
  id: number
  issueKey: string
  title: string
  type: string
  priority: string
  status: string
  storyPoints?: number
  dueDate?: string
  blocked: boolean
  dependencyIssueKeys: string[]
}

export interface SprintPlanTask {
  id: number
  issueKey: string
  title: string
  type: string
  priority: string
  storyPoints?: number
  dueDate?: string
  blocked: boolean
  reason: string
}

export interface SprintPlanVO {
  projectId: number
  sprintId?: number
  sprintName?: string
  capacityPoints: number
  historicalVelocity: number
  availablePoints: number
  totalRecommendedPoints: number
  aiAvailable: boolean
  summary: string
  risks: string[]
  candidates: SprintPlanCandidate[]
  recommendedTasks: SprintPlanTask[]
  generatedAt: string
}

export interface SprintPlanApplyResult {
  sprintId: number
  appliedCount: number
  totalStoryPoints: number
  appliedIssueIds: number[]
}

export function getSprints(projectId: number) {
  return request.get(`/sprints/project/${projectId}`);
}

export function getSprintById(id: number) {
  return request.get(`/sprints/${id}`);
}

export function getBurndown(sprintId: number) {
  return request.get(`/sprints/${sprintId}/burndown`);
}

export function getSprintStats(sprintId: number) {
  return request.get(`/sprints/${sprintId}/stats`);
}

export function getSprintHealth(sprintId: number) {
  return request.get<SprintHealthVO>(`/sprints/${sprintId}/health`)
}

export function getSprintHealthAiSummary(sprintId: number) {
  return request.post<SprintHealthAiSummaryVO>(`/sprints/${sprintId}/health/ai-summary`)
}

export function suggestSprintPlan(data: {
  projectId: number
  sprintId: number
  capacityPoints?: number
}) {
  return request.post<SprintPlanVO>('/ai/suggest-sprint-plan', data)
}

export function applySprintPlan(sprintId: number, issueIds: number[]) {
  return request.post<SprintPlanApplyResult>(`/sprints/${sprintId}/apply-plan`, { issueIds })
}

export function createSprint(data: { projectId: number; name: string; startDate: string; endDate: string; goal?: string }) {
  return request.post("/sprints", data);
}

export function startSprint(id: number) {
  return request.put(`/sprints/${id}/start`);
}

export function completeSprint(id: number) {
  return request.put(`/sprints/${id}/complete`);
}

export function quickStartSprint(projectId: number) {
  return request.post(`/sprints/quick/${projectId}`);
}

export function takeSnapshot(sprintId: number, data?: { doneIssues?: number; remainingIssues?: number }) {
  return request.post(`/sprints/${sprintId}/snapshot`, data || {});
}

export function completeMigration(sprintId: number) {
  return request.put(`/sprints/${sprintId}/complete-migration`);
}

export function addToSprint(issueId: number, sprintId: number) {
  return request.put(`/sprints/${issueId}/add-to-sprint/${sprintId}`);
}
