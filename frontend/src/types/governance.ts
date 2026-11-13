export type GovernanceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export interface FieldDiff {
  field: string
  before: unknown
  after: unknown
}

export interface IssueVersion {
  id: number
  issueId: number
  versionNo: number
  snapshot: Record<string, unknown>
  changeSummary?: string
  source: string
  operatorId: number
  operatorName?: string
  createdAt: string
}

export interface IssueChangeRequest {
  id: number
  issueId: number
  issueKey: string
  issueTitle: string
  baseVersion: number
  currentVersion: number
  proposedChanges: Record<string, unknown>
  diffs: FieldDiff[]
  changeReason: string
  status: GovernanceStatus
  applicantId: number
  applicantName?: string
  reviewerId?: number
  reviewerName?: string
  reviewNote?: string
  reviewedAt?: string
  createdAt: string
  versionConflict: boolean
}

export interface IssueReview {
  id: number
  issueId: number
  roundNo: number
  submitterId: number
  submitterName?: string
  completionNote?: string
  evidenceUrls: string[]
  status: GovernanceStatus
  reviewerId?: number
  reviewerName?: string
  reviewNote?: string
  submittedAt: string
  reviewedAt?: string
}

export type ImpactRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface AffectedIssue {
  id: number
  issueKey: string
  title: string
  type: string
  status?: string
  sprintId?: number
  sprintName?: string
  completed: boolean
  relation: string
}

export interface AffectedSprint {
  id: number
  name: string
  status?: string
  active: boolean
}

export interface IssueImpact {
  requestId: number
  issueId: number
  issueKey: string
  issueTitle: string
  affectedIssueCount: number
  affectedTaskCount: number
  completedTaskCount: number
  affectedIssues: AffectedIssue[]
  affectedSprintId?: number
  affectedSprintName?: string
  affectedSprints: AffectedSprint[]
  currentSprintId?: number
  currentSprintName?: string
  proposedSprintId?: number
  proposedSprintName?: string
  sprintScopeChanged: boolean
  activeSprintAffected: boolean
  currentStoryPoints: number
  proposedStoryPoints: number
  storyPointDelta: number
  workloadDelta: number
  currentDueDate?: string
  proposedDueDate?: string
  dueDateDeltaDays?: number
  riskLevel: ImpactRiskLevel
  riskReasons: string[]
}
