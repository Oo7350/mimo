import request from './request'
import type { TeamWorkbenchData, WorkbenchData } from '@/types/workbench'

export function getMyWorkbench() {
  return request.get<WorkbenchData>('/workbench/my')
}

/**
 * 管理员视角：全员任务流（v2.19.8）。
 * 非管理员会收到 403 和一句说明——不是空列表，空列表会被误读成"大家都没任务"。
 */
export function getTeamWorkbench() {
  return request.get<TeamWorkbenchData>('/workbench/team')
}
