import request from './request'
import type { TeamWorkbenchData, TeamWorkbenchQuery, WorkbenchData } from '@/types/workbench'

export function getMyWorkbench() {
  return request.get<WorkbenchData>('/workbench/my')
}

/**
 * 管理员视角：全员任务流（v2.19.8；v2.20.1 起支持筛选、排序、分页）。
 *
 * 非管理员会收到 403 和一句说明——不是空列表，空列表会被误读成"大家都没任务"。
 *
 * 筛选 / 排序 / 分页都在服务端：前端自己切页再自己筛，会算出"有逾期 3 人"其实只数了
 * 当前页那 8 个人这种骗人的数字（v2.20.1 之前正是如此）。
 */
export function getTeamWorkbench(query: TeamWorkbenchQuery = {}) {
  return request.get<TeamWorkbenchData>('/workbench/team', { params: query })
}
