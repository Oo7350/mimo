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
  // 改用 POST + body 传参，避免 URL 长度限制（GET URL 超过 ~8KB 会被浏览器或 Tomcat 拒绝）
  // 走相对路径 + Vite/Nginx 反代，浏览器无需关心后端 IP/端口
  // (开发环境 Vite 代理，生产环境 nginx 代理)
  fetch(`/api/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('token')}`,
      // text/event-stream 后保留通配符, 避免服务端 content negotiation 失败
      Accept: 'text/event-stream, */*',
    },
    body: JSON.stringify({
      message,
      systemPrompt: systemPrompt || '',
    }),
    signal: controller.signal,
    // 明确告诉浏览器这是流式请求
    cache: 'no-store',
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
      // 处理 [DONE] 标记
      const handleData = (raw: string) => {
        const data = raw.trim()
        if (!data) return
        if (data === '[DONE]') return
        onChunk(data)
      }
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          // 处理缓冲区残留
          if (buffer.trim()) {
            const tail = buffer.split('\n')
            for (const line of tail) {
              if (line.startsWith('data: ')) handleData(line.slice(6))
            }
          }
          break
        }
        buffer += decoder.decode(value, { stream: true })
        // SSE 事件以 \n\n 分隔，先按 \n\n 拆分
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''
        for (const evt of events) {
          for (const line of evt.split('\n')) {
            if (line.startsWith('data: ')) handleData(line.slice(6))
          }
        }
      }
      onDone?.()
    })
    .catch((err) => {
      if ((err as Error).name !== 'AbortError') {
        console.error('[AI Stream] error:', err)
        onError?.(err as Error)
      }
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
