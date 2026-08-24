CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `team_id`     BIGINT       NOT NULL COMMENT '团队ID',
    `sender_id`   BIGINT       NOT NULL COMMENT '发送者用户ID',
    `sender_name` VARCHAR(64)  NOT NULL COMMENT '发送者名称',
    `sender_avatar` VARCHAR(128) DEFAULT '' COMMENT '发送者头像颜色',
    `content`     TEXT         NOT NULL COMMENT '消息内容（最多500字）',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_team_created` (`team_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='团队聊天消息表';
