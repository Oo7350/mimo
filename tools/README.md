# tools/ — 自检脚本

这里放的是**能跑出数字**的自检工具，不是一次性调试脚本。
每个都在文件头写了"为什么存在"，改之前先读那段。

| 脚本 | 干什么 | 怎么跑 | 当前基线 |
|---|---|---|---|
| `buglog_stats.py` | 统计 `bug-log.md` 的 BUG / KNOWN / 总计，并与文件里那张统计表对账 | `python tools/buglog_stats.py`<br>`python tools/buglog_stats.py --check --list-open` | BUG 109（6/45/40/18）、KNOWN 未关闭 2、总计 111 |
| `theme_literals.py` | 找出前端样式块里的**中性色字面值**（深字 `#0f172a`、白底 `#fff`、边框 `#e2e8f0`…），可一键映射成语义变量 | `python tools/theme_literals.py --check`（只统计）<br>`python tools/theme_literals.py --write`（落盘） | `--check` = 0 处 |
| `integrity-check.sql` | 数据库引用关系体检：16 类孤儿（项目/工作项/评论/工时/验收/附件/成员/迭代/快照/通知/标签/Wiki） | `mysql -umimo -pmimo123 mimo < tools/integrity-check.sql` | 16 项全 0 |

## 为什么需要它们

- **账**：`bug-log.md` 的统计长期靠手算，历史上错过两次（KNOWN-017 部分解决被当成关闭）。
  现在口径固定在脚本里，表格抄错就 `--check` 报红。
- **色**：暗色模式的问题不是"审美"，是"用了不该用的变量"。人眼看不出来，
  脚本能列清哪一行还是字面值。它**故意不管**品牌色/状态色/渐变/图表配色——那些是刻意的。
- **数据**：库上没有外键（见 `AGENTS.md` Decision Log），所以必须有能随时跑的体检。
  `integrity-check.sql` 上线第一天就扫出 BUG-104（6 行 `project_members` 挂在逻辑删除的项目上）。

## 配套的浏览器侧工具（在 `e2e/`）

| 脚本 | 干什么 |
|---|---|
| `e2e/theme-audit.mjs` | 真开浏览器量实际底色与文字对比度，暗色浅色块/低对比应为 0 |
| `e2e/probe-colors.mjs` | 打印某个元素实际生效的 CSS 变量与匹配规则（查"为什么这个颜色没生效"） |
| `e2e/probe-layout.mjs` | 逐页查横向溢出与文字裁切 |
| `e2e/presence.mjs` | 验证团队在线人数是真的（连上算在线、关页面会掉线） |

跑法见 `e2e/README.md`。
