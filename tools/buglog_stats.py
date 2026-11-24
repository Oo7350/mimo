#!/usr/bin/env python3
"""bug-log.md 统计口径的唯一实现。

背景：这份账本的"总计 = BUG 条目 + KNOWN 未关闭"一直靠手写维护，历史上因此错过两次
（KNOWN-017 明明只"部分解决"却被当成关闭算了，连带总计少算一条）。这个脚本把口径固定下来：

  * BUG 条目       —— 以 `### BUG-NNN` 标题行计数（严重程度取标题里的 [Critical/High/Medium/Low]）
  * KNOWN 已关闭   —— 标题里写了「已解决 / 已修复 / 已失效」的
  * KNOWN 未关闭   —— 标题里只写「部分已解决」，或者根本没写状态的
  * 总计           —— BUG 条目 + KNOWN 未关闭

用法：
    python tools/buglog_stats.py                 # 打印统计
    python tools/buglog_stats.py --check         # 和文件里那张统计表对账，不一致就退出码 1
    python tools/buglog_stats.py --list-open     # 顺便列出未关闭的 KNOWN
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

BLOG = Path(__file__).resolve().parent.parent / "bug-log.md"

BUG_HEADING = re.compile(r"^### (BUG-\d+)\s*(.*)$")
KNOWN_HEADING = re.compile(r"^### (KNOWN-\d+)\s*(.*)$")
SEVERITY = re.compile(r"\[(Critical|High|Medium|Low)\]")
CLOSED_MARKERS = ("已解决", "已修复", "已失效")
SEVERITIES = ("Critical", "High", "Medium", "Low")


def parse(text: str) -> dict:
    bugs: list[tuple[str, str]] = []
    known_open: list[str] = []
    known_closed: list[str] = []

    for line in text.splitlines():
        bug = BUG_HEADING.match(line)
        if bug:
            bugs.append((bug.group(1), bug.group(2)))
            continue
        known = KNOWN_HEADING.match(line)
        if known:
            title = known.group(2)
            if any(marker in title for marker in CLOSED_MARKERS):
                known_closed.append(known.group(1))
            else:
                known_open.append(known.group(1))

    by_severity = {name: 0 for name in SEVERITIES}
    unlabelled: list[str] = []
    for bug_id, title in bugs:
        found = SEVERITY.search(title)
        if found:
            by_severity[found.group(1)] += 1
        else:
            unlabelled.append(bug_id)

    return {
        "bugs": len(bugs),
        "by_severity": by_severity,
        "unlabelled": unlabelled,
        "known_open": known_open,
        "known_closed": len(known_closed),
    }


def stated_table(text: str) -> dict[str, int] | None:
    """抓文件里那张「严重程度 / 数量」统计表，用于对账。"""
    wanted = {
        "Critical": r"\|\s*Critical\s*\|\s*(\d+)\s*\|",
        "High": r"\|\s*High\s*\|\s*(\d+)\s*\|",
        "Medium": r"\|\s*Medium\s*\|\s*(\d+)\s*\|",
        "Low": r"\|\s*Low\s*\|\s*(\d+)\s*\|",
        "bugs": r"\*\*已修复（BUG 条目）\*\*\s*\|\s*\*\*(\d+)\*\*",
        "known_open": r"\*\*已知未修复（KNOWN 条目）\*\*\s*\|\s*\*\*(\d+)\*\*",
        "total": r"\*\*总计\*\*\s*\|\s*\*\*(\d+)\*\*",
    }
    found: dict[str, int] = {}
    for key, pattern in wanted.items():
        match = re.search(pattern, text)
        if not match:
            return None
        found[key] = int(match.group(1))
    return found


def main() -> int:
    # Windows 控制台默认 GBK，中文与 ✓ 会直接抛 UnicodeEncodeError
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    parser = argparse.ArgumentParser(description="统计 bug-log.md 的 BUG / KNOWN / 总计")
    parser.add_argument("--check", action="store_true", help="与文件内统计表对账，不一致返回 1")
    parser.add_argument("--list-open", action="store_true", help="列出未关闭的 KNOWN 编号")
    args = parser.parse_args()

    text = BLOG.read_text(encoding="utf-8")
    stats = parse(text)
    total = stats["bugs"] + len(stats["known_open"])
    severity_sum = sum(stats["by_severity"].values())

    print(f"BUG 条目           : {stats['bugs']}")
    for name in SEVERITIES:
        print(f"  {name:<8}        : {stats['by_severity'][name]}")
    print(f"KNOWN 已关闭       : {stats['known_closed']}")
    print(f"KNOWN 未关闭       : {len(stats['known_open'])}")
    print(f"总计（BUG+未关闭） : {total}")
    if stats["unlabelled"]:
        print(f"[WARN] 没有严重程度标签的 BUG: {', '.join(stats['unlabelled'])}")

    if args.list_open:
        print("未关闭的 KNOWN: " + ", ".join(stats["known_open"]))

    problems: list[str] = []
    if severity_sum != stats["bugs"]:
        problems.append(f"四档合计 {severity_sum} ≠ BUG 条目 {stats['bugs']}")

    if args.check:
        stated = stated_table(text)
        if stated is None:
            problems.append("文件里找不到统计表，没法对账")
        else:
            if stated["bugs"] != stats["bugs"]:
                problems.append(f"统计表 BUG {stated['bugs']} ≠ 实际 {stats['bugs']}")
            if stated["known_open"] != len(stats["known_open"]):
                problems.append(
                    f"统计表 KNOWN 未关闭 {stated['known_open']} ≠ 实际 {len(stats['known_open'])}"
                )
            if stated["total"] != total:
                problems.append(f"统计表总计 {stated['total']} ≠ 实际 {total}")
            for name in SEVERITIES:
                if stated[name] != stats["by_severity"][name]:
                    problems.append(
                        f"统计表 {name} {stated[name]} ≠ 实际 {stats['by_severity'][name]}"
                    )

    if problems:
        print("\n[FAIL] 对不上：")
        for item in problems:
            print("  - " + item)
        return 1

    if args.check:
        print("\n[OK] 与文件内统计表一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
