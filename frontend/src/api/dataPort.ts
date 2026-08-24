import request from '@/api/request'

/**
 * 导出任务列表
 * @param projectId 项目 ID
 * @param format  xlsx | csv | json
 */
export async function exportIssues(projectId: number, format: 'xlsx' | 'csv' | 'json' = 'xlsx') {
  // blob 文件下载
  const res = await request.get('/data-port/issues/export', {
    params: { projectId, format },
    responseType: 'blob',
  })
  // res 已是拦截器返回的完整响应体，但 blob 类型直接是 Blob
  const blob: Blob = res as unknown as Blob
  const filename = `issues-${projectId}-${new Date().toISOString().slice(0, 10)}.${format}`
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** 批量导入任务（Excel/CSV） */
export function importIssues(projectId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/data-port/issues/import?projectId=${projectId}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}
