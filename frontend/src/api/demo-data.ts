import request from "./request"

/**
 * 演示数据（KNOWN-020）。
 *
 * 空库只有 3 个用户 1 个团队：工作台、迭代健康度、交付分析、审计日志全是 0，
 * 既没法试用也没法演示。这里是给管理员用的"一键铺一套数据"的接口。
 */
export function getDemoDataStatus() {
  return request.get<{ exists: boolean; projectId?: number; issueCount?: number }>("/admin/demo-data")
}

export function generateDemoData() {
  return request.post<{
    created: boolean
    message: string
    projectId?: number
    teamId?: number
    issueCount?: number
    sprintCount?: number
    commentCount?: number
    workLogCount?: number
    reviewCount?: number
    reportCount?: number
  }>("/admin/demo-data")
}
