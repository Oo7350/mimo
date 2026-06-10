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

/**
 * 相对时间格式化：将日期字符串转为 "刚刚"、"5分钟前"、"3小时前" 等友好格式
 * @param dateStr 后端返回的日期字符串，格式如 "2024-01-15 10:30" 或 "2024-01-15 10:30:00"
 */
export function formatRelativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr.replace(/-/g, '/'))
  if (isNaN(date.getTime())) return dateStr
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 0) return '刚刚'
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months}个月前`
  const years = Math.floor(months / 12)
  return `${years}年前`
}
