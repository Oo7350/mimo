import request from "./request";

export interface GanttItem {
  id: number
  issueKey?: string
  title: string
  type?: string
  priority?: string
  status?: string
  assigneeId?: number
  storyPoints?: number
  planStartDate: string
  planEndDate: string
  dependencies?: string   // JSON 字符串, e.g. "[1,5,8]"
}

export function getGanttByProject(projectId: number) {
  return request.get<GanttItem[]>(`/gantt/project/${projectId}`)
}
