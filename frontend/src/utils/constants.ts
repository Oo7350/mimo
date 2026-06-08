import type { IssueType, IssuePriority, BugStatus } from '@/types'

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

export const TYPE_BAR_COLOR: Record<IssueType, string> = {
  STORY: 'var(--color-success)',
  TASK: 'var(--color-primary)',
  BUG: 'var(--color-danger)',
}

export const TYPE_ICON: Record<IssueType, string> = {
  STORY: 'Notebook',
  TASK: 'Check',
  BUG: 'WarningFilled',
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

export const SEVERITY_LABEL: Record<string, string> = {
  BLOCKER: '阻塞',
  CRITICAL: '严重',
  MAJOR: '主要',
  MINOR: '次要',
  TRIVIAL: '轻微',
}

export const SEVERITY_COLOR: Record<string, '' | 'danger' | 'warning' | 'info'> = {
  BLOCKER: 'danger',
  CRITICAL: 'danger',
  MAJOR: 'warning',
  MINOR: 'info',
  TRIVIAL: '',
}

export const BUG_STATUS_LABEL: Record<BugStatus, string> = {
  NEW: '新建',
  CONFIRMED: '已确认',
  IN_PROGRESS: '修复中',
  RESOLVED: '已解决',
  VERIFIED: '已验证',
  CLOSED: '已关闭',
  REOPENED: '重新打开',
}

export const BUG_STATUS_COLOR: Record<BugStatus, '' | 'success' | 'danger' | 'warning' | 'info'> = {
  NEW: 'danger',
  CONFIRMED: 'warning',
  IN_PROGRESS: '',
  RESOLVED: 'info',
  VERIFIED: 'success',
  CLOSED: 'success',
  REOPENED: 'danger',
}

// Allowed transitions for BUG status
export const BUG_TRANSITIONS: Record<BugStatus, BugStatus[]> = {
  NEW: ['CONFIRMED', 'CLOSED'],
  CONFIRMED: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['VERIFIED', 'REOPENED'],
  VERIFIED: ['CLOSED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['IN_PROGRESS'],
}
