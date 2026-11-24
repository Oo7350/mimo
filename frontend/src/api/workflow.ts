import request from './request'

export interface WorkflowNode {
  columnId: number
  name: string
  color: string
  isTerminal: boolean
  /** 这一节点的责任人（谁负责把它交给下一步）；不填 = 不限制 */
  ownerUserId?: number | null
  /** 后端回填的责任人显示名 */
  ownerName?: string | null
}

export interface WorkflowTransition {
  fromColumnId: number
  toColumnId: number
  conditions: string[]
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
  /** 出发节点的责任人；null = 不限制 */
  fromOwnerId?: number | null
  fromOwnerName?: string | null
  /** 当前用户能不能走这一步 */
  allowed?: boolean
  /** allowed=false 时的原因，例如「这一步由「张三」负责」 */
  reason?: string | null
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

export function getAvailableTransitions(projectId: number, issueType: string, columnId: number) {
  return request.get<AvailableTransition[]>('/workflow/transitions', {
    params: { projectId, issueType, columnId },
  })
}
