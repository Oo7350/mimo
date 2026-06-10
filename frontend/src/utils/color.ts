const PALETTE = [
  ['#6366f1', '#818cf8'],
  ['#8b5cf6', '#a78bfa'],
  ['#06b6d4', '#22d3ee'],
  ['#10b981', '#34d399'],
  ['#f59e0b', '#fbbf24'],
  ['#ef4444', '#f87171'],
  ['#ec4899', '#f472b6'],
  ['#3b82f6', '#60a5fa'],
]

export function hashString(str: string): number {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash)
}

export function avatarGradient(name: string): string {
  const [from, to] = PALETTE[hashString(name) % PALETTE.length]
  return `linear-gradient(135deg, ${from}, ${to})`
}

export function avatarColor(name: string): string {
  return PALETTE[hashString(name) % PALETTE.length][0]
}
