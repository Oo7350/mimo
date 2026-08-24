-- 用户邮箱账户表（IMAP 凭据，密码 AES-GCM 加密存储）
CREATE TABLE IF NOT EXISTS `user_email_accounts` (
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户ID',
    `user_id`             BIGINT       NOT NULL COMMENT '所属 Mimo 用户 ID',
    `email_address`       VARCHAR(200) NOT NULL COMMENT '邮箱地址',
    `imap_host`           VARCHAR(200) NOT NULL COMMENT 'IMAP 服务器主机',
    `imap_port`           INT          NOT NULL DEFAULT 993 COMMENT 'IMAP 端口',
    `imap_username`       VARCHAR(200) NOT NULL COMMENT 'IMAP 登录用户名（通常等于邮箱）',
    `imap_password_enc`   VARCHAR(500) NOT NULL COMMENT 'IMAP 密码（AES-GCM 加密 + base64）',
    `is_default`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否默认账户',
    `last_synced_at`      DATETIME     DEFAULT NULL COMMENT '最近一次拉取时间',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_user_email` (`user_id`, `email_address`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户邮箱账户（IMAP）';
