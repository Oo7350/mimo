ALTER TABLE `chat_messages` ADD COLUMN `recalled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已撤回' AFTER `content`;
