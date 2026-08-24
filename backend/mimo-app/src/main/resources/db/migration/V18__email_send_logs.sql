-- v2.13.6：邮件发送日志表（记录发送结果，便于排查失败、统计送达率）
CREATE TABLE IF NOT EXISTS `email_send_logs` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NULL     COMMENT '收件人用户 ID',
    `to_email`        VARCHAR(200) NOT NULL COMMENT '收件人邮箱',
    `account_id`     BIGINT       NULL     COMMENT '使用的邮箱账户 ID',
    `from_email`      VARCHAR(200) NULL     COMMENT '发件人邮箱',
    `subject`         VARCHAR(300) NOT NULL COMMENT '邮件主题',
    `notify_type`     VARCHAR(50)  NULL     COMMENT '事件类型：assignment/mention/comment/approval/issue_status',
    `related_id`      BIGINT       NULL     COMMENT '关联业务对象 ID（如通知 ID）',
    `success`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=成功 0=失败',
    `error`           VARCHAR(500) NULL     COMMENT '失败原因',
    `duration_ms`     INT          NULL     COMMENT '耗时（毫秒）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_created` (`created_at`),
    INDEX `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件发送日志';
