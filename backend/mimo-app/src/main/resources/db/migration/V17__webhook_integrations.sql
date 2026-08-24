-- v2.13.5：第三方 Webhook 集成配置表
-- 支持事件触发时回调外部系统（IM 机器人、CI/CD、第三方工作流）
CREATE TABLE IF NOT EXISTS `webhook_configs` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`      BIGINT       NULL     COMMENT '项目ID（NULL=全局）',
    `team_id`         BIGINT       NULL     COMMENT '团队ID（NULL=全局）',
    `name`            VARCHAR(100) NOT NULL COMMENT '配置名称',
    `url`             VARCHAR(500) NOT NULL COMMENT 'Webhook URL（HTTPS）',
    `secret`          VARCHAR(200) NULL     COMMENT '签名密钥（用于 HMAC-SHA256）',
    `events`          VARCHAR(500) NOT NULL DEFAULT '' COMMENT '订阅事件，逗号分隔：ISSUE_CREATED,ISSUE_UPDATED,...',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用 1=是 0=否',
    `created_by`      BIGINT       NULL     COMMENT '创建人',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project` (`project_id`),
    INDEX `idx_team` (`team_id`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook 集成配置';

-- Webhook 投递日志（失败排查 & 重试）
CREATE TABLE IF NOT EXISTS `webhook_delivery_logs` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `webhook_id`      BIGINT       NOT NULL COMMENT 'Webhook ID',
    `event_type`      VARCHAR(50)  NOT NULL COMMENT '事件类型',
    `payload`         TEXT         NULL     COMMENT '请求体 JSON',
    `status_code`     INT          NULL     COMMENT 'HTTP 响应码',
    `response_body`   TEXT         NULL     COMMENT '响应体（截断 2000 字符）',
    `success`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=成功 0=失败',
    `duration_ms`     INT          NULL     COMMENT '耗时（毫秒）',
    `error`           VARCHAR(500) NULL     COMMENT '失败原因',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_webhook` (`webhook_id`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook 投递日志';
