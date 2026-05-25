export interface UserInfo {
  id: number
  username: string
  email: string
  avatar?: string
  role: 'ROLE_ADMIN' | 'ROLE_MEMBER'
  createdAt?: string
}

export interface TeamVO {
  id: number
  name: string
  description?: string
  ownerId: number
  ownerName: string
  memberCount: number
  createdAt: string
}

export interface ProjectVO {
  id: number
  name: string
  key: string
  description?: string
  template: 'SCRUM' | 'KANBAN'
  teamName: string
  teamId?: number
}

export interface BoardColumn {
  id: number
  name: string
  color: string
  sortOrder: number
  issues: IssueCard[]
}

export interface IssueCard {
  id: number
  issueKey: string
  title: string
  type: IssueType
  priority: IssuePriority
  status: IssueStatus
  assigneeId?: number
  assigneeName?: string
  assigneeAvatar?: string
  storyPoints?: number
  labels: IssueLabel[]
  severity?: string
  columnId: number
  columnName: string
  dueDate?: string
  reporterId?: number
  reporterName?: string
  sprintId?: number
  projectId?: number
  description?: string
  stepsToRepro?: string
}

export interface IssueLabel {
  id: number
  label: string
  color: string
}

export interface SprintVO {
  id: number
  name: string
  goal?: string
  startDate: string
  endDate: string
  status: 'PLANNING' | 'ACTIVE' | 'COMPLETED'
  isActive: boolean
  totalIssues: number
  completedIssues: number
  projectId: number
  projectName: string
}

export interface DashboardData {
  username?: string
  totalIssues: number
  inProgressIssues: number
  doneIssues: number
  myProjects: ProjectVO[]
  activeSprints: SprintVO[]
  recentActivities: ActivityVO[]
}

export interface ActivityVO {
  id: number
  username: string
  action: string
  detail: string
  createdAt: string
  targetType?: string
}

export interface BurndownData {
  sprintName: string
  startDate: string
  endDate: string
  totalPoints: number
  points: BurndownPoint[]
}

export interface BurndownPoint {
  date: string
  idealRemaining: number
  actualRemaining: number
}

export interface IssueFormData {
  id?: number
  projectId: number
  title: string
  type: IssueType
  priority: IssuePriority
  status: IssueStatus
  columnId?: number
  assigneeId?: number
  storyPoints?: number
  dueDate?: string
  description?: string
  severity?: string
  stepsToRepro?: string
  labels: string[]
  sprintId?: number
}

export type IssueType = 'STORY' | 'TASK' | 'BUG'
export type IssuePriority = 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'LOW' | 'LOWEST'
export type IssueStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
