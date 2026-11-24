# 把前端样式里"写死的中性色"映射成语义变量（KNOWN-021 收口工具）
#
# 只处理三类最容易在暗色模式下出事、且**不含品牌含义**的字面值：
#   1. 深色文字（#0f172a / #111827 / ...）—— 暗色主题下会变成"深字压深底"
#   2. 近白/浅灰底（#fff / #f8fafc / #f1f5f9 / ...）—— 暗色主题下会变成"亮块"
#   3. 中性边框（#e2e8f0 / #ebeef5 / ...）
#
# 不动：品牌色、状态色（红/绿/蓝的实心块）、渐变、阴影、图表配色（那些是刻意的）。
# 用法：
#   python tools/theme_literals.py --check   # 只统计，不改文件
#   python tools/theme_literals.py --write   # 执行替换
import argparse
import pathlib
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = pathlib.Path(__file__).resolve().parent.parent / "frontend" / "src"

# (字面值正则, 目标变量, 说明)
RULES = [
    # —— 深色文字 ——
    (r"color:\s*#0f172a\b", "color: var(--text-primary)", "深字→主文字"),
    (r"color:\s*#111827\b", "color: var(--text-primary)", "深字→主文字"),
    (r"color:\s*#1e293b\b", "color: var(--text-primary)", "深字→主文字"),
    (r"color:\s*#1f2937\b", "color: var(--text-primary)", "深字→主文字"),
    (r"color:\s*#334155\b", "color: var(--text-regular)", "深字→正文"),
    (r"color:\s*#475569\b", "color: var(--text-regular)", "深字→正文"),
    (r"color:\s*#64748b\b", "color: var(--text-secondary)", "灰字→次要文字"),
    (r"color:\s*#6b7280\b", "color: var(--text-secondary)", "灰字→次要文字"),
    (r"color:\s*#94a3b8\b", "color: var(--text-muted)", "浅灰字→弱化文字"),
    (r"color:\s*#9ca3af\b", "color: var(--text-muted)", "浅灰字→弱化文字"),
    # —— 近白/浅灰底 ——
    (r"background:\s*#ffffff\b", "background: var(--bg-card)", "白底→卡片底"),
    (r"background:\s*#fff\b", "background: var(--bg-card)", "白底→卡片底"),
    (r"background:\s*#f8fafc\b", "background: var(--bg-subtle)", "浅底→弱底色"),
    (r"background:\s*#f9fafb\b", "background: var(--bg-subtle)", "浅底→弱底色"),
    (r"background:\s*#f1f5f9\b", "background: var(--bg-subtle)", "浅底→弱底色"),
    (r"background:\s*#f3f4f6\b", "background: var(--bg-subtle)", "浅底→弱底色"),
    (r"background:\s*#f5f7fa\b", "background: var(--bg-subtle)", "浅底→弱底色"),
    (r"background:\s*#e8ecf4\b", "background: var(--bg-subtle-strong)", "浅底→强弱底色"),
    (r"background:\s*#ecfdf5\b", "background: var(--bg-success-soft)", "浅绿底→语义浅底"),
    (r"background:\s*#fef2f2\b", "background: var(--bg-danger-soft)", "浅红底→语义浅底"),
    (r"background:\s*#eff6ff\b", "background: var(--bg-info-soft)", "浅蓝底→语义浅底"),
    (r"background:\s*#fffbeb\b", "background: var(--bg-warning-soft)", "浅黄底→语义浅底"),
    # —— 中性边框 ——
    (r"border:\s*1px solid #e2e8f0\b", "border: 1px solid var(--border-color)", "边框→边框变量"),
    (r"border:\s*1px solid #ebeef5\b", "border: 1px solid var(--border-color)", "边框→边框变量"),
    (r"border-bottom:\s*1px solid #f1f5f9\b", "border-bottom: 1px solid var(--border-color-light)", "分隔线→浅边框"),
]

SKIP_DIRS = {"node_modules", "dist"}
# 设计令牌本身就是这些字面值的定义处，不能被自己改写
SKIP_FILES = {"variables.css"}


def iter_files():
    for path in ROOT.rglob("*"):
        if path.suffix not in {".vue", ".css", ".scss"}:
            continue
        if path.name in SKIP_FILES:
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        yield path


def style_span(text: str):
    """只替换 <style> 块里的内容，避免动到 JS 里的颜色常量"""
    if "<style" not in text:
        return [(0, len(text))]
    spans = []
    for m in re.finditer(r"<style[^>]*>(.*?)</style>", text, re.S):
        spans.append((m.start(1), m.end(1)))
    return spans


def process(path: pathlib.Path, write: bool):
    text = path.read_text(encoding="utf-8", errors="replace")
    spans = style_span(text)
    total = 0
    per_rule = {}
    new_text = text
    # 从后往前替换，保持偏移有效
    for start, end in sorted(spans, reverse=True):
        chunk = new_text[start:end]
        for pattern, replacement, label in RULES:
            new_chunk, n = re.subn(pattern, replacement, chunk)
            if n:
                per_rule[label] = per_rule.get(label, 0) + n
                total += n
                chunk = new_chunk
        new_text = new_text[:start] + chunk + new_text[end:]

    if total and write:
        path.write_text(new_text, encoding="utf-8", newline="")
    return total, per_rule


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="真的写入文件")
    parser.add_argument("--check", action="store_true", help="只统计（默认行为）")
    parser.add_argument("--top", type=int, default=15, help="列出命中最多的文件数")
    args = parser.parse_args()

    write = args.write and not args.check
    total = 0
    rows = []
    for path in iter_files():
        n, _ = process(path, write=False)
        if n:
            rows.append((n, path.relative_to(ROOT.parent.parent)))
            total += n

    rows.sort(reverse=True)
    for n, rel in rows[: args.top]:
        print(f"{n:>4}  {rel}")
    print(f"\n命中合计：{total} 处（{len(rows)} 个文件）")

    if write:
        done = 0
        for path in iter_files():
            n, _ = process(path, write=True)
            done += n
        print(f"已替换：{done} 处")
    else:
        print("未改动任何文件（加 --write 才落盘）")


if __name__ == "__main__":
    main()
