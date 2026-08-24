-- Mimo v2.9.0: 日历模块
-- 字符集: utf8mb4, 引擎: InnoDB

USE `mimo`;

-- ============================================================
-- 日历事件表
-- ============================================================
CREATE TABLE IF NOT EXISTS `calendar_events` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件ID',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户ID',
    `project_id`      BIGINT       DEFAULT NULL COMMENT '关联项目ID',
    `title`           VARCHAR(200) NOT NULL COMMENT '事件标题',
    `description`     TEXT         DEFAULT NULL COMMENT '事件描述',
    `start_time`      DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`        DATETIME     NOT NULL COMMENT '结束时间',
    `all_day`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否全天事件(0=否,1=是)',
    `event_type`      VARCHAR(30)  NOT NULL DEFAULT 'TASK_DEADLINE' COMMENT '事件类型: TASK_DEADLINE/MEETING/REMINDER/SPRINT/CUSTOM',
    `related_id`      BIGINT       DEFAULT NULL COMMENT '关联ID(issue_id/sprint_id)',
    `related_type`    VARCHAR(20)  DEFAULT NULL COMMENT '关联类型: ISSUE / SPRINT',
    `color`           VARCHAR(20)  DEFAULT '#409EFF' COMMENT '事件颜色',
    `location`        VARCHAR(200) DEFAULT NULL COMMENT '地点',
    `participants`    JSON         DEFAULT NULL COMMENT '参与人ID列表',
    `reminder_minutes` INT          DEFAULT 15 COMMENT '提前提醒(分钟),0=不提醒',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人ID',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_time` (`user_id`, `start_time`),
    INDEX `idx_project` (`project_id`),
    INDEX `idx_related` (`related_type`, `related_id`),
    INDEX `idx_date_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日历事件表';
