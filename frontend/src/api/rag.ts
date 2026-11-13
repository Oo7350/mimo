import request from './request'

export interface RagCitation {
  citationNo: number
  sourceType: 'WIKI' | 'ISSUE' | 'COMMENT'
  sourceId: number
  parentId?: number
  title: string
  snippet: string
  sourceUrl?: string
  score: number
}

export interface RagAnswer {
  projectId: number
  question: string
  answer: string
  aiAvailable: boolean
  evidenceSufficient: boolean
  citations: RagCitation[]
  retrievedCount: number
  generatedAt: string
}

export interface RagIndexResult {
  projectId: number
  wikiChunks: number
  issueChunks: number
  commentChunks: number
  totalChunks: number
  indexedAt: string
}

export interface RagEvaluationResult {
  caseCount: number
  topK: number
  recallAtK: number
  citationCorrectness: number
  unsupportedAnswerRate: number
  details: Array<{
    question: string
    expectedSources: string[]
    retrievedSources: string[]
    recall: number
    citationCorrectness: number
    unsupported: boolean
  }>
}

export function askProjectKnowledge(data: {
  projectId: number
  question: string
  topK?: number
}) {
  return request.post<RagAnswer>('/ai/rag/ask', data)
}

export function rebuildProjectKnowledgeIndex(projectId: number) {
  return request.post<RagIndexResult>(`/ai/rag/index/${projectId}`)
}

export function evaluateProjectKnowledge(data: {
  projectId: number
  topK?: number
  cases: Array<{ question: string; expectedSources: string[] }>
}) {
  return request.post<RagEvaluationResult>('/ai/rag/evaluate', data)
}
