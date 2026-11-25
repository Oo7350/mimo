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
  completedTaskCount: number
  activeSprintCount: number
  activeSprintStoryPoints: number
  activeSprintDoneStoryPoints: number
  activeSprintHours: number
  activeSprints: ActiveSprintEffort[]
  workHours: number
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

// ==================== 管理员视角：全员任务流（v2.19.8） ====================

export type MemberLoadLevel = 'OVERLOADED' | 'BUSY' | 'NORMAL' | 'IDLE'

/** 一个人的任务流 */
export interface MemberStream {
  userId: number
  username: string
  email?: string
  avatar?: string
  /** 旧字段角色，仅展示用 */
  role?: string
  /** 是否管理员（系统管理员或某团队管理员） */
  admin: boolean
  /** 是否在线（本实例 WebSocket 连接，口径同团队页在线人数） */
  online: boolean
  openCount: number
  inProgressCount: number
  overdueTaskCount: number
  doneCount: number
  tasks: WorkbenchTask[]
  /** 这个人一共有几条未完成任务（tasks 可能被 taskLimit 截断） */
  taskTotal: number
  loadLevel: MemberLoadLevel
  loadReason: string
}

export interface UnassignedTask {
  id: number
  issueKey: string
  title: string
  type: 'STORY' | 'TASK' | 'BUG'
  priority: WorkbenchTask['priority']
  projectId: number
  projectName?: string
  dueDate?: string
}

export interface TeamWorkbenchSummary {
  memberCount: number
  busyMemberCount: number
  openTaskCount: number
  overdueTaskCount: number
  doneTaskCount: number
  inProgressTaskCount: number
  unassignedTaskCount: number
  onlineMemberCount: number
  unassignedTasks: UnassignedTask[]
}

export interface TeamWorkbenchData {
  /** SYSTEM = 全部团队；TEAM = 我管理的团队 */
  scope: 'SYSTEM' | 'TEAM'
  teamIds: number[]
  teamNames: string[]
  summary: TeamWorkbenchSummary
  /** 当前页的成员流（筛选、排序、分页都在后端做） */
  members: MemberStream[]
  /** 筛完之后的成员总数 */
  memberTotal: number
  /** 不筛选时的成员总数（用来说明"筛出 3 / 共 12"） */
  memberTotalAll: number
  memberPage: number
  memberPageSize: number
  memberPageCount: number
  /** 每个成员最多带回几条任务 */
  taskLimit: number
  trend: TrendPoint[]
  /** 趋势口径说明，直接显示给用户 */
  trendNote: string
}

/** 近 7 天的新建 / 完成 */
export interface TrendPoint {
  date: string
  created: number
  done: number
}

/** 全员任务流的查询条件（筛选/排序/分页都走服务端，否则"筛选"只筛当前页会骗人） */
export interface TeamWorkbenchQuery {
  page?: number
  pageSize?: number
  keyword?: string
  only?: 'all' | 'overdue' | 'doing' | 'idle'
  sort?: 'load' | 'name' | 'overdue'
  taskLimit?: number
}
