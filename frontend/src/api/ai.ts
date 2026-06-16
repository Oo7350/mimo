import request from './request'

// ==================== 现有接口 (兼容) ====================

/** 润色任务描述 */
export async function polishDescription(text: string): Promise<string> {
  const res = await request.post('/ai/polish', { text })
  return res.data?.data || res.data || ''
}

/** AI 推荐优先级 */
export async function analyzePriority(title: string, description: string): Promise<{ priority: string; reason: string }> {
  const res = await request.post('/ai/priority', { title, description })
  return res.data?.data || { priority: 'MEDIUM', reason: '无法解析AI响应，使用默认值' }
}

// ==================== 新增AI能力接口 ====================

/** 智能任务创建 - 自然语言解析为结构化任务 */
export interface ParsedIssue {
  title: string
  type: 'STORY' | 'TASK' | 'BUG'
  priority: 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'LOW' | 'LOWEST'
  assigneeSuggestion?: string
  storyPoints?: number
  labels?: string[]
  description?: string
  dueDateSuggestion?: string
  subTaskSuggestions?: Array<{ title: string; type: string; estimate: number }>
  confidence: number
  disclaimer?: string
}

export async function parseIssue(input: string, context?: Record<string, any>): Promise<ParsedIssue> {
  const res = await request.post('/ai/parse-issue', { input, context })
  return res.data?.data
}

/** Story 拆分 - 将Story拆分为子任务建议 */
export interface SuggestedTask {
  title: string
  type: 'TASK' | 'BUG'
  estimate: number
  dependency?: string
  description?: string
}

export async function suggestSubTasks(params: {
  title: string
  description?: string
  acceptanceCriteria?: string
}): Promise<SuggestedTask[]> {
  const res = await request.post('/ai/suggest-tasks', params)
  return res.data?.data || []
}

/** AI 故事点估算 */
export interface StoryPointEstimate {
  points: number
  confidence: number
  reason: string
}

export async function estimateStoryPoints(params: {
  title: string
  description?: string
  type?: string
}): Promise<StoryPointEstimate> {
  const res = await request.post('/ai/estimate-story-points', params)
  return res.data?.data || { points: 3, confidence: 0.5, reason: '默认值' }
}

// ==================== 流式对话 ====================

/** SSE 流式对话 */
export function chatStream(
  message: string,
  onChunk: (text: string) => void,
  systemPrompt?: string,
  onDone?: () => void,
  onError?: (err: Error) => void
): AbortController {
  const controller = new AbortController()
  const params = new URLSearchParams({ message, systemPrompt: systemPrompt || '' })
  fetch(`/api/ai/chat/stream?${params.toString()}`, {
    headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        // 读取错误响应体获取更多信息
        let errorText = ''
        try { errorText = await response.text() } catch {}
        throw new Error(`HTTP ${response.status}${errorText ? ': ' + errorText.slice(0, 200) : ''}`)
      }
      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // 解析SSE: data: ...\n\n
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6).trim()
            if (data) onChunk(data)
          }
        }
      }
      onDone?.()
    })
    .catch((err) => {
      if ((err as Error).name !== 'AbortError') onError?.(err as Error)
    })
  return controller
}

// ==================== 用量查询 ====================

/** 获取AI用量信息 */
export interface AiUsageInfo {
  provider: string
  used: number
  remaining: number
  dailyLimit: number
}

export async function getAiUsage(): Promise<AiUsageInfo> {
  const res = await request.get('/ai/usage')
  return res.data?.data || { provider: 'Unknown', used: 0, remaining: 100, dailyLimit: 100 }
}
