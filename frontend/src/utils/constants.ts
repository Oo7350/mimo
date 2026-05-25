import type { IssueType, IssuePriority } from '@/types'

export const TYPE_LABEL: Record<IssueType, string> = {
  STORY: '故事',
  TASK: '任务',
  BUG: '缺陷',
}

export const TYPE_COLOR: Record<IssueType, '' | 'success' | 'danger'> = {
  STORY: 'success',
  TASK: '',
  BUG: 'danger',
}

export const PRIORITY_LABEL: Record<IssuePriority, string> = {
  HIGHEST: '最高',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  LOWEST: '最低',
}

export const PRIORITY_COLOR: Record<IssuePriority, '' | 'danger' | 'warning' | 'info'> = {
  HIGHEST: 'danger',
  HIGH: 'warning',
  MEDIUM: '',
  LOW: 'info',
  LOWEST: 'info',
}

export const PRIORITY_BAR_COLOR: Record<IssuePriority, string> = {
  HIGHEST: 'var(--priority-highest)',
  HIGH: 'var(--priority-high)',
  MEDIUM: 'var(--priority-medium)',
  LOW: 'var(--priority-low)',
  LOWEST: 'var(--priority-lowest)',
}
