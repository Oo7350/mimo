import request from './request'

/** 润色任务描述 */
export async function polishDescription(text: string): Promise<string> {
  const res = await request.post('/api/ai/polish', { text })
  return res.data?.data || res.data || ''
}

/** AI 推荐优先级 */
export async function analyzePriority(title: string, description: string): Promise<{ priority: string; reason: string }> {
  const res = await request.post('/api/ai/priority', { title, description })
  return res.data?.data || { priority: 'MEDIUM', reason: '无法解析AI响应，使用默认值' }
}
