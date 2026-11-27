-- V25: 热点查询索引（v2.22.4 量级验证的产出）— 2026-11-27
--
-- 由来：造了量级数据（100 用户 / 500 工作项 / 5000 活动日志）之后，把 activity_logs
-- 放大到 4 万行（其中近 7 天窗口内 3.6 万行），用 EXPLAIN ANALYZE 看真实执行计划，
-- 逐条量了"该不该建索引"，而不是"看着像该建就建"。结论如下。
--
-- ── 建的（量出了收益）───────────────────────────────────────────────
-- 1. activity_logs (project_id, action, created_at)
--    工作台趋势的两条聚合查询（ActivityLogMapper.countCreatedByDay / countMovedToTerminalByDay）
--    形状都是 `WHERE project_id IN (...) AND action = ? AND created_at >= ?`，
--    但表上只有单列索引，优化器只能二选一：全表扫 4 万行再筛，或按项目把 7000+ 行全捞出来再筛。
--    实测（EXPLAIN ANALYZE，4 万行）：新建趋势 31.4ms → 16.4ms；移动趋势 63.7ms → 47.2ms。
--    顺带把 `project_id IN (SELECT … LIKE 'LOAD%')` 那段变成了覆盖扫描（不是索引的功劳，是统计信息准了）。
--
-- 2. issues (sprint_id, status)
--    迭代速率统计是 `WHERE sprint_id IN (...) AND deleted = 0`，只取 (id, sprint_id, status)。
--    复合后变成覆盖索引，连回表都省了。实测在 1.7 万行的 issues 副本上：
--    选中的迭代只占 0.9% 时 6.47ms → 0.33ms（约 20 倍）；占到 87% 时优化器正确地改用全表扫。
--    也就是说它是"选择性好的时候赚、选择性差的时候不亏"。
--
-- ── 删的（纯浪费）─────────────────────────────────────────────────
-- 3. DROP INDEX idx_created：它与 V16 建的 idx_created_at **完全重复**（都只含 created_at）。
--    两个同构索引只带来写放大和 buffer 占用。
--
-- ── 量了之后决定不建的（留个证据，省得下次再来一轮）──────────────────
-- 4. issues (project_id, deleted)：看着最像该建的一个，实测**没有收益**。
--    1.7 万行副本上：`project_id = ?`（选中 17%）时全表扫 6.45ms，走索引反而 7.65ms；
--    `project_id IN (5 个)`（选中 87%）时两者都在 7~8ms，优化器直接忽略索引。
--    原因很实在：压测数据里同一个项目的行是连续插入的，聚簇索引本来就把它摆在相邻页上了，
--    顺序扫比"3000 次索引查找 + 回表"更便宜。既然不赚，就不加——单列 idx_project 继续留着。
-- 5. 同理没动 idx_project / idx_sprint 这两个"新复合索引的前缀"：它们对新索引不是必需的，
--    但优化器在某些纯等值查询里仍会挑窄索引；没有测出坏处就不做无谓的折腾。

CREATE INDEX `idx_activity_project_action_time` ON `activity_logs` (`project_id`, `action`, `created_at`);

CREATE INDEX `idx_issues_sprint_status` ON `issues` (`sprint_id`, `status`);

-- 与 idx_created_at 完全重复，删一个。
-- MigrationRunner 逐条执行并跳过失败，所以这两句在"索引已存在 / 已删除"的库上会报错被跳过，天然幂等。
DROP INDEX `idx_created` ON `activity_logs`;
