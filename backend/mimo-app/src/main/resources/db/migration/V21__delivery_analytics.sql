-- v2.15.3: Velocity and cumulative flow daily snapshots
CREATE TABLE IF NOT EXISTS `delivery_daily_snapshots` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`      BIGINT       NOT NULL,
    `sprint_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '0 表示未进入 Sprint',
    `snapshot_date`   DATE         NOT NULL,
    `status_category` VARCHAR(30)  NOT NULL COMMENT 'TODO / IN_PROGRESS / PENDING_REVIEW / DONE',
    `issue_count`     INT          NOT NULL DEFAULT 0,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delivery_status_day` (`project_id`, `sprint_id`, `snapshot_date`, `status_category`),
    KEY `idx_delivery_project_date` (`project_id`, `snapshot_date`),
    KEY `idx_delivery_sprint_date` (`sprint_id`, `snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目每日交付状态快照';
