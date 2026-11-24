// 飞书同款三色系
// 注意：这三个底色上压的都是**白字**（头像首字母），所以必须够深。
// 原来用的 #3370ff / #00b96b / #f7b955，白字对比度只有 4.28 / 2.58 / 1.75，
// 最后那个橙底白字基本看不清（暗色模式下更糟）。下面这组都 ≥5:1。
const TYPE_COLORS = {
  project: '#2563eb',   // 蓝 — 项目（白字 5.2:1）
  team:    '#047857',   // 绿 — 团队（白字 5.5:1）
  user:    '#b45309',   // 琥珀 — 人员（白字 5.0:1）
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

/** 把 #rgb / #rrggbb / rgb() 解析成 0-255 的三元组，解析不了返回 null */
function parseHex(input: string): [number, number, number] | null {
  if (!input) return null
  const s = input.trim()
  const m = s.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i)
  if (m) {
    const h = m[1]
    if (h.length === 3) {
      return [
        parseInt(h[0] + h[0], 16),
        parseInt(h[1] + h[1], 16),
        parseInt(h[2] + h[2], 16),
      ]
    }
    return [
      parseInt(h.slice(0, 2), 16),
      parseInt(h.slice(2, 4), 16),
      parseInt(h.slice(4, 6), 16),
    ]
  }
  const rgb = s.match(/^rgba?\(([^)]+)\)$/i)
  if (rgb) {
    const parts = rgb[1].split(/[,\s/]+/).filter(Boolean).map(Number)
    if (parts.length >= 3) return [parts[0], parts[1], parts[2]]
  }
  return null
}

/** 相对亮度（WCAG），0=黑 1=白 */
export function relativeLuminance(color: string): number {
  const rgb = parseHex(color)
  if (!rgb) return 0
  const f = (v: number) => {
    const x = v / 255
    return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * f(rgb[0]) + 0.7152 * f(rgb[1]) + 0.0722 * f(rgb[2])
}

/**
 * 给一块**任意颜色**的底配一个读得清的字色。
 *
 * 用在"颜色是数据带出来的"地方——日历事件色、头像底色这类值来自用户或后端，
 * 代码里写死 color:#fff 就一定有一批数据会翻车（白字压在浅黄/浅绿上只有 2:1）。
 */
export function readableInk(background: string, light = '#ffffff', dark = '#0f172a'): string {
  const lum = relativeLuminance(background)
  // 白字对比度 = 1.05 / (lum+0.05)，深字 = (lum+0.05) / 0.05；取更划算的那个
  const onWhite = 1.05 / (lum + 0.05)
  const onDark = (lum + 0.05) / 0.05
  return onWhite >= onDark ? light : dark
}
