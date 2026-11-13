export type WorkbenchRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface WorkbenchTask {
  id: number
  issueKey: string
  title: string
  type: 'STORY' | 'TASK' | 'BUG'
  priority: 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'LOW' | 'LOWEST'
  status: string
  dueDate?: string
  projectId: number
  projectName?: string
  sprintId?: number
  sprintName?: string
  riskLevel: WorkbenchRiskLevel
  riskReasons: string[]
}

export interface PendingWorkbenchReview {
  id: number
  kind: 'CHANGE' | 'REVIEW'
  issueId: number
  issueKey: string
  issueTitle: string
  projectId: number
  projectName?: string
  submittedById?: number
  submittedByName?: string
  submittedAt?: string
  summary?: string
}

export interface RejectedWorkbenchItem {
  id: number
  kind: 'CHANGE' | 'REVIEW'
  issueId: number
  issueKey: string
  issueTitle: string
  projectId: number
  projectName?: string
  reason?: string
  reviewedAt?: string
}

export interface ActiveSprintEffort {
  id: number
  name: string
  projectId: number
  projectName?: string
  assignedTaskCount: number
  completedTaskCount: number
  storyPoints: number
  doneStoryPoints: number
  weekHours: number
}

export interface WorkbenchWeeklySummary {
  weekStart: string
  weekEnd: string
  workHours: number
  completedTaskCount: number
  activeSprintCount: number
  activeSprintStoryPoints: number
  activeSprintDoneStoryPoints: number
  activeSprintHours: number
  activeSprints: ActiveSprintEffort[]
}

export interface WorkbenchData {
  tasks: WorkbenchTask[]
  dueToday: WorkbenchTask[]
  overdue: WorkbenchTask[]
  pendingChangeReviews: PendingWorkbenchReview[]
  pendingTaskReviews: PendingWorkbenchReview[]
  rejectedItems: RejectedWorkbenchItem[]
  weeklySummary: WorkbenchWeeklySummary
}
