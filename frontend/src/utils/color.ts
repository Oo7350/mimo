// 飞书同款三色系
const TYPE_COLORS = {
  project: '#3370ff',   // 飞书蓝 — 项目
  team:    '#00b96b',   // 飞书绿 — 团队
  user:    '#f7b955',   // 飞书橙 — 人员
} as const

// 头像颜色调色板
const PALETTE = [
  ['#6366f1', '#a5b4fc'], // 靛蓝
  ['#ec4899', '#f9a8d4'], // 粉红
  ['#14b8a6', '#5eead4'], // 青色
  ['#f97316', '#fdba74'], // 橙色
  ['#8b5cf6', '#c4b5fd'], // 紫色
  ['#06b6d4', '#67e8f9'], // 青蓝
]

export type AvatarType = keyof typeof TYPE_COLORS

export function hashString(str: string): number {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash)
}

export function avatarGradient(name: string, type?: AvatarType): string {
  if (type) return TYPE_COLORS[type]
  return '#475569'
}

export function avatarColor(name: string): string {
  return PALETTE[hashString(name) % PALETTE.length][0]
}
