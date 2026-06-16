// 飞书同款三色系
const TYPE_COLORS = {
  project: '#3370ff',   // 飞书蓝 — 项目
  team:    '#00b96b',   // 飞书绿 — 团队
  user:    '#f7b955',   // 飞书橙 — 人员
} as const

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
