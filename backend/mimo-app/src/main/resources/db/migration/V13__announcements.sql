-- 公告表
CREATE TABLE IF NOT EXISTS `announcements` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告ID',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题',
    `content`      TEXT         NOT NULL COMMENT '正文（Markdown）',
    `pinned`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `created_by`   BIGINT       NOT NULL COMMENT '发布人 userId',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_pinned` (`pinned`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告';
