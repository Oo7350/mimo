-- v2.13.3：邮件通知通道
-- 在 user_email_accounts 基础上补充 SMTP 发送配置 + 用户通知偏好
-- notify_types 存逗号分隔的字符串，如 'assignment,mention,approval,comment'
-- 可选值：assignment（任务指派）、mention（@提及）、approval（审批）、comment（评论回复）、issue_status（任务状态变更）

ALTER TABLE `user_email_accounts`
    ADD COLUMN `smtp_host`      VARCHAR(200) NULL COMMENT 'SMTP 服务器主机（NULL 时按 imap_host 推断：imap.xxx → smtp.xxx）' AFTER `imap_password_enc`,
    ADD COLUMN `smtp_port`     INT          NULL COMMENT 'SMTP 端口（NULL 时默认 465 SSL）' AFTER `smtp_host`,
    ADD COLUMN `notify_types`  VARCHAR(200) NULL DEFAULT 'assignment,mention,approval' COMMENT '触发邮件通知的事件类型（逗号分隔）' AFTER `smtp_port`,
    ADD COLUMN `notify_enabled` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否启用邮件通知：0 关闭，1 启用' AFTER `notify_types`;
