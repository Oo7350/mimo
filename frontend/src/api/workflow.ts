import request from './request'

export interface WorkflowNode {
  /**
   * 节点的稳定身份（v2.22.0）。
   *
   * 以前节点就用 columnId 当主键，等于"节点只存在于看板上"；现在节点先有身份，
   * 看板列是它的落脚点：没绑列的自由节点在保存时由后端补一列并回填 columnId。
   */
  nodeKey?: string
  columnId?: number | null
  name: string
  color: string
  isTerminal: boolean
  /** 这一节点的责任人（谁负责把它交给下一步）；不填 = 不限制 */
  ownerUserId?: number | null
  /** 后端回填的责任人显示名 */
  ownerName?: string | null
  /** 画布坐标（拖动后保存；后端会对空值按顺序排一行） */
  x?: number | null
  y?: number | null
}

export interface WorkflowTransition {
  /** 画布连线两端（保存时后端与 columnId 互相回填） */
  fromNodeKey?: string | null
  toNodeKey?: string | null
  fromColumnId?: number | null
  toColumnId?: number | null
  /** 谁可以做这一步（命中任一即可）；空 = 所有人 */
  conditions: string[]
  /** 前提条件（必须全部满足）；目前只有 ACCEPTANCE_DONE */
  prerequisites?: string[]
}

export interface WorkflowConfig {
  nodes: WorkflowNode[]
  transitions: WorkflowTransition[]
}

export interface WorkflowVO {
  id: number
  projectId: number
  issueType: string
  name: string
  config: WorkflowConfig
  isActive: boolean
  isDefault: boolean
  /** 本次保存时为自由节点自动创建的看板列名（前端要明确告诉用户） */
  createdColumns?: string[]
}

export interface SaveWorkflowRequest {
  projectId: number
  issueType: string
  name: string
  config: WorkflowConfig
}

export interface AvailableTransition {
  toColumnId: number
  toColumnName: string
  conditions: string[]
  /** 前提条件（全部满足才允许） */
  prerequisites?: string[]
  /** 出发节点的责任人；null = 不限制 */
  fromOwnerId?: number | null
  fromOwnerName?: string | null
  /** 当前用户能不能走这一步 */
  allowed?: boolean
  /** allowed=false 时的原因，例如「这一步由「张三」负责」 */
  reason?: string | null
}

/** 「谁可以做」条件的人话文案 */
export const CONDITION_LABEL: Record<string, string> = {
  ROLE_ADMIN: '仅管理员',
  ASSIGNEE: '仅指派人',
  REPORTER: '仅报告人',
}

/** 「什么情况下才能做」前提条件的人话文案（全部满足才允许） */
export const PREREQUISITE_LABEL: Record<string, string> = {
  ACCEPTANCE_DONE: '验收标准已全部勾完',
}

export function listWorkflows(projectId: number) {
  return request.get<WorkflowVO[]>(`/workflow/project/${projectId}`)
}

export function getActiveWorkflow(projectId: number, issueType: string) {
  return request.get<WorkflowVO>(`/workflow/project/${projectId}/type/${issueType}`)
}

export function saveWorkflow(data: SaveWorkflowRequest) {
  return request.post<WorkflowVO>('/workflow', data)
}

export function deleteWorkflow(workflowId: number) {
  return request.delete(`/workflow/${workflowId}`)
}

export function applyTemplate(projectId: number, issueType: string, templateName: string) {
  return request.post<WorkflowVO>('/workflow/template', null, {
    params: { projectId, issueType, templateName },
  })
}

/**
 * 可用转换（带上"能不能走 / 卡在哪"）。
 *
 * issueId 有值时后端才能判断"验收标准还有几项没勾完"这类前提；
 * 不传就只能对"谁可以做"下结论。
 */
export function getAvailableTransitions(
  projectId: number,
  issueType: string,
  columnId: number,
  issueId?: number,
) {
  return request.get<AvailableTransition[]>('/workflow/transitions', {
    params: { projectId, issueType, columnId, ...(issueId ? { issueId } : {}) },
  })
}
