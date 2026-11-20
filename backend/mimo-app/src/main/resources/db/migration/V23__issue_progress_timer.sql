-- v2.16.0：看板进行中自动计时
-- 设计：不需要定时任务，也不需要写 work_logs。
-- 任务进入「进行中」时把当前时间写进 in_progress_started_at；
-- 离开进行中时把 (now - in_progress_started_at) 累加进 in_progress_seconds 并清空 started_at。
-- 前端用 started_at + seconds 实时渲染计时，天然支持刷新/多人同时查看。

ALTER TABLE `issues`
    ADD COLUMN `in_progress_started_at` DATETIME DEFAULT NULL COMMENT '本轮进入进行中的开始时间（NULL=当前未计时）';

ALTER TABLE `issues`
    ADD COLUMN `in_progress_seconds` BIGINT NOT NULL DEFAULT 0 COMMENT '已累计的进行中时长（秒）';

CREATE INDEX `idx_issues_in_progress` ON `issues` (`in_progress_started_at`);

-- 存量数据修正：已经处于进行中但没有开始时间的任务，用 updated_at 兜底开始计时，
-- 避免上线后大批在办任务显示 0 秒。
UPDATE `issues`
SET `in_progress_started_at` = `updated_at`
WHERE `status` = 'IN_PROGRESS' AND `in_progress_started_at` IS NULL;
