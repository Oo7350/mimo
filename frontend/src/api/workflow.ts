import request from './request'

export interface WorkflowNode {
  columnId: number
  name: string
  color: string
  isTerminal: boolean
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
