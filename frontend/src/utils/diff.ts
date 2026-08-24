export interface DiffLine {
  type: "added" | "removed" | "unchanged"
  text: string
}

/**
 * 行级 diff（LCS 最长公共子序列算法）
 * 返回按原顺序排列的行列表：新增行标 added，删除行标 removed，未变行标 unchanged。
 */
export function diffLines(oldText: string, newText: string): DiffLine[] {
  const a = (oldText || "").split("\n")
  const b = (newText || "").split("\n")
  const n = a.length
  const m = b.length

  // dp[i][j] = a[i..] 与 b[j..] 的 LCS 长度（自底向上）
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  // 回溯生成 diff 行
  const result: DiffLine[] = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      result.push({ type: "unchanged", text: a[i] })
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      result.push({ type: "removed", text: a[i] })
      i++
    } else {
      result.push({ type: "added", text: b[j] })
      j++
    }
  }
  while (i < n) result.push({ type: "removed", text: a[i++] })
  while (j < m) result.push({ type: "added", text: b[j++] })

  return result
}
