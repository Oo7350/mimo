-- v2.13.4 操作审计增强
-- 给 activity_logs 表补充 IP/UA/Diff/RequestID 等审计字段，支持更完整的追溯能力
-- 注意：拆成独立 ALTER 语句，便于重跑时单条失败不影响其他列的修改
-- （MigrationRunner 会把 Duplicate column 类错误识别为"已执行过"跳过）

ALTER TABLE `activity_logs`
    ADD COLUMN `ip_address`  VARCHAR(45)  NULL COMMENT '操作 IP（IPv4/IPv6 兼容）' AFTER `detail`;

ALTER TABLE `activity_logs`
    ADD COLUMN `user_agent` VARCHAR(500) NULL COMMENT '浏览器 UA（截断到 500 字符）' AFTER `ip_address`;

ALTER TABLE `activity_logs`
    ADD COLUMN `diff_json`  JSON         NULL COMMENT '字段变更 Diff（before/after 结构）' AFTER `user_agent`;

ALTER TABLE `activity_logs`
    ADD COLUMN `request_id` VARCHAR(36)  NULL COMMENT '请求追踪 ID（用于串联同一请求的多条日志）' AFTER `diff_json`;

-- target_id 原为 NOT NULL，但部分对象（如 TEAM 创建）没有明确 ID，允许为空
ALTER TABLE `activity_logs` MODIFY COLUMN `target_id` BIGINT NULL COMMENT '操作对象ID';

-- 索引：CREATE INDEX 若已存在会报 Duplicate key name，MigrationRunner 会跳过
CREATE INDEX `idx_request_id` ON `activity_logs` (`request_id`);
CREATE INDEX `idx_created_at` ON `activity_logs` (`created_at`);
