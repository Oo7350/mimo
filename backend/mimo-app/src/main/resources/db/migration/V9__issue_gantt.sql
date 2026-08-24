-- V9: 甘特图字段 — 2026-06-18
-- 为 issues 表增加计划起止日期 + 依赖关系（JSON）

ALTER TABLE issues
    ADD COLUMN plan_start_date DATE NULL                        COMMENT '计划开始日期（甘特图用）'           AFTER due_date,
    ADD COLUMN plan_end_date   DATE NULL                        COMMENT '计划结束日期（甘特图用）'           AFTER plan_start_date,
    ADD COLUMN dependencies    VARCHAR(1000) DEFAULT NULL       COMMENT '依赖的 Issue ID 列表 JSON, 如 [1,5,8]' AFTER plan_end_date,
    ADD INDEX idx_issues_plan_range (project_id, plan_start_date, plan_end_date);
