-- Mimo 敏捷项目管理平台 - 初始化 DDL
-- 字符集: utf8mb4, 引擎: InnoDB

CREATE DATABASE IF NOT EXISTS `mimo` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mimo`;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`      VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    `email`         VARCHAR(100) NOT NULL COMMENT '邮箱',
    `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '角色: ROLE_ADMIN / ROLE_MEMBER',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2. 团队表
-- ============================================================
CREATE TABLE IF NOT EXISTS `teams` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '团队ID',
    `name`          VARCHAR(100) NOT NULL COMMENT '团队名称',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '团队描述',
    `owner_id`      BIGINT       NOT NULL COMMENT '创建者/管理员用户ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_team_name` (`name`),
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='团队表';

-- ============================================================
-- 3. 团队-用户关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS `team_members` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    `team_id`       BIGINT     NOT NULL COMMENT '团队ID',
    `user_id`       BIGINT     NOT NULL COMMENT '用户ID',
    `role`          VARCHAR(20) NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '团队内角色: ROLE_ADMIN / ROLE_MEMBER',
    `joined_at`     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY `uk_team_user` (`team_id`, `user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='团队成员表';

-- ============================================================
-- 4. 项目表
-- ============================================================
CREATE TABLE IF NOT EXISTS `projects` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目ID',
    `name`          VARCHAR(100) NOT NULL COMMENT '项目名称',
    `key`           VARCHAR(10)  NOT NULL COMMENT '项目唯一标识 Key (如 MIMO)',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
    `template`      VARCHAR(20)  NOT NULL DEFAULT 'SCRUM' COMMENT '模板类型: SCRUM / KANBAN',
    `team_id`       BIGINT       NOT NULL COMMENT '所属团队ID',
    `owner_id`      BIGINT       NOT NULL COMMENT '项目管理员ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_project_key` (`key`),
    INDEX `idx_team` (`team_id`),
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- ============================================================
-- 5. 项目成员表
-- ============================================================
CREATE TABLE IF NOT EXISTS `project_members` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    `project_id`    BIGINT   NOT NULL COMMENT '项目ID',
    `user_id`       BIGINT   NOT NULL COMMENT '用户ID',
    `role`          VARCHAR(20) NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '项目内角色',
    `joined_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY `uk_project_user` (`project_id`, `user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

-- ============================================================
-- 6. 看板列表
-- ============================================================
CREATE TABLE IF NOT EXISTS `board_columns` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '列ID',
    `project_id`    BIGINT       NOT NULL COMMENT '项目ID',
    `name`          VARCHAR(50)  NOT NULL COMMENT '列名称 (待办/进行中/已完成)',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `color`         VARCHAR(20)  DEFAULT '#409EFF' COMMENT '列标识颜色',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板列表';

-- ============================================================
-- 7. 任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS `issues` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    `project_id`    BIGINT       NOT NULL COMMENT '所属项目ID',
    `column_id`     BIGINT       DEFAULT NULL COMMENT '所在看板列ID',
    `sprint_id`     BIGINT       DEFAULT NULL COMMENT '所属 Sprint ID',
    `issue_key`     VARCHAR(20)  NOT NULL COMMENT '任务编号 (如 MIMO-1)',
    `title`         VARCHAR(200) NOT NULL COMMENT '任务标题',
    `description`   TEXT         DEFAULT NULL COMMENT '任务描述（富文本）',
    `type`          VARCHAR(20)  NOT NULL DEFAULT 'TASK' COMMENT '类型: STORY / TASK / BUG',
    `priority`      VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级: HIGHEST / HIGH / MEDIUM / LOW / LOWEST',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'TODO' COMMENT '状态: TODO / IN_PROGRESS / DONE',
    `assignee_id`   BIGINT       DEFAULT NULL COMMENT '指派人用户ID',
    `reporter_id`   BIGINT       NOT NULL COMMENT '创建人用户ID',
    `due_date`      DATE         DEFAULT NULL COMMENT '截止日期',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '在列内的排序序号',
    `story_points`  INT          DEFAULT NULL COMMENT '故事点',
    `severity`          VARCHAR(20)  DEFAULT NULL COMMENT '缺陷严重程度: BLOCKER / CRITICAL / MAJOR / MINOR / TRIVIAL',
    `steps_to_repro`    TEXT         DEFAULT NULL COMMENT '复现步骤 (缺陷类型专用)',
    `user_role`         VARCHAR(50)  DEFAULT NULL COMMENT 'STORY专属: 用户角色(作为...)',
    `user_goal`         VARCHAR(500) DEFAULT NULL COMMENT 'STORY专属: 目标功能(我希望...)',
    `business_value`    VARCHAR(500) DEFAULT NULL COMMENT 'STORY专属: 业务价值(以便...)',
    `acceptance_criteria` JSON       DEFAULT NULL COMMENT 'STORY专属: 验收标准列表',
    `environment`       VARCHAR(500) DEFAULT NULL COMMENT 'BUG专属: 环境信息',
    `expected_result`   TEXT         DEFAULT NULL COMMENT 'BUG专属: 期望结果',
    `actual_result`     TEXT         DEFAULT NULL COMMENT 'BUG专属: 实际结果',
    `found_version`     VARCHAR(50)  DEFAULT NULL COMMENT 'BUG专属: 发现版本',
    `fixed_version`     VARCHAR(50)  DEFAULT NULL COMMENT 'BUG专属: 修复版本',
    `parent_id`         BIGINT       DEFAULT NULL COMMENT 'STORY对TASK的父子关系',
    `epic`              VARCHAR(100) DEFAULT NULL COMMENT 'STORY专属: 所属史诗',
    `bug_status`        VARCHAR(20)  DEFAULT NULL COMMENT 'BUG专属状态: NEW/CONFIRMED/IN_PROGRESS/RESOLVED/VERIFIED/CLOSED/REOPENED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_issue_key` (`issue_key`),
    INDEX `idx_project` (`project_id`),
    INDEX `idx_column` (`column_id`),
    INDEX `idx_sprint` (`sprint_id`),
    INDEX `idx_assignee` (`assignee_id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_priority` (`priority`),
    INDEX `idx_parent` (`parent_id`),
    INDEX `idx_epic` (`epic`),
    INDEX `idx_bug_status` (`bug_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- ============================================================
-- 8. 任务标签表
-- ============================================================
CREATE TABLE IF NOT EXISTS `issue_labels` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标签ID',
    `issue_id`      BIGINT      NOT NULL COMMENT '任务ID',
    `label`         VARCHAR(30) NOT NULL COMMENT '标签名',
    `color`         VARCHAR(20) DEFAULT '#409EFF' COMMENT '标签颜色',
    UNIQUE KEY `uk_issue_label` (`issue_id`, `label`),
    INDEX `idx_label` (`label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务标签表';

-- ============================================================
-- 9. 附件表
-- ============================================================
CREATE TABLE IF NOT EXISTS `attachments` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '附件ID',
    `issue_id`      BIGINT       NOT NULL COMMENT '所属任务ID',
    `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path`     VARCHAR(500) NOT NULL COMMENT '存储路径',
    `file_size`     BIGINT       NOT NULL COMMENT '文件大小 (字节)',
    `file_type`     VARCHAR(50)  DEFAULT NULL COMMENT 'MIME类型',
    `uploader_id`   BIGINT       NOT NULL COMMENT '上传人ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    INDEX `idx_issue` (`issue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件表';

-- ============================================================
-- 10. Sprint 表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sprints` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Sprint ID',
    `project_id`    BIGINT       NOT NULL COMMENT '所属项目ID',
    `name`          VARCHAR(100) NOT NULL COMMENT 'Sprint 名称',
    `goal`          VARCHAR(500) DEFAULT NULL COMMENT 'Sprint 目标',
    `start_date`    DATE         NOT NULL COMMENT '开始日期',
    `end_date`      DATE         NOT NULL COMMENT '结束日期',
    `is_active`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否当前活跃 Sprint',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING / ACTIVE / COMPLETED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint表';

-- ============================================================
-- 11. 日报/周报表
-- ============================================================
CREATE TABLE IF NOT EXISTS `reports` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报告ID',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `project_id`    BIGINT       NOT NULL COMMENT '项目ID',
    `type`          VARCHAR(10)  NOT NULL COMMENT '类型: DAILY / WEEKLY',
    `report_date`   DATE         NOT NULL COMMENT '报告日期（日报=当天，周报=周日）',
    `content`       TEXT         DEFAULT NULL COMMENT '报告内容',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT / SUBMITTED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_date` (`user_id`, `report_date`),
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日报/周报表';

-- ============================================================
-- 12. 操作日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS `activity_logs` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    `user_id`       BIGINT       NOT NULL COMMENT '操作用户ID',
    `username`      VARCHAR(50)  NOT NULL COMMENT '操作用户名(冗余)',
    `project_id`    BIGINT       DEFAULT NULL COMMENT '关联项目ID',
    `target_type`   VARCHAR(30)  NOT NULL COMMENT '操作对象类型: ISSUE / SPRINT / PROJECT / TEAM',
    `target_id`     BIGINT       NOT NULL COMMENT '操作对象ID',
    `action`        VARCHAR(30)  NOT NULL COMMENT '动作: CREATE / UPDATE / DELETE / MOVE',
    `detail`        VARCHAR(500) DEFAULT NULL COMMENT '变更详情 (JSON)',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX `idx_user` (`user_id`),
    INDEX `idx_project` (`project_id`),
    INDEX `idx_target` (`target_type`, `target_id`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ============================================================
-- 燃尽图每日快照表 (用于统计理想/实际剩余工作量)
-- ============================================================
CREATE TABLE IF NOT EXISTS `burndown_snapshots` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '快照ID',
    `sprint_id`         BIGINT   NOT NULL COMMENT 'Sprint ID',
    `snapshot_date`     DATE     NOT NULL COMMENT '快照日期',
    `total_points`      INT      NOT NULL DEFAULT 0 COMMENT 'Sprint 总故事点',
    `remaining_points`  INT      NOT NULL DEFAULT 0 COMMENT '剩余故事点',
    `completed_points`  INT      NOT NULL DEFAULT 0 COMMENT '已完成故事点',
    `ideal_remaining`   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '理想剩余 (线性递减)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    UNIQUE KEY `uk_sprint_date` (`sprint_id`, `snapshot_date`),
    INDEX `idx_sprint` (`sprint_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='燃尽图每日快照表';

-- ============================================================
-- 种子数据 (测试账号)
-- ============================================================
-- 密码均为 123456，BCrypt 加密
INSERT INTO `users` (`username`, `password`, `email`, `role`) VALUES
('admin',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@mimo.dev', 'ROLE_ADMIN'),
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'zhangsan@mimo.dev', 'ROLE_MEMBER'),
('lisi',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'lisi@mimo.dev',   'ROLE_MEMBER');

INSERT INTO `teams` (`name`, `description`, `owner_id`) VALUES
('Mimo开发团队', 'Mimo产品研发核心团队', 1);

INSERT INTO `team_members` (`team_id`, `user_id`, `role`) VALUES
(1, 1, 'ROLE_ADMIN'),
(1, 2, 'ROLE_MEMBER'),
(1, 3, 'ROLE_MEMBER');

-- 14. 任务评论表
CREATE TABLE IF NOT EXISTS `comments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `issue_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `username` VARCHAR(50) NOT NULL,
  `content` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_issue` (`issue_id`),
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. 通知表
CREATE TABLE IF NOT EXISTS `notifications` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(30) NOT NULL COMMENT 'ASSIGNED / STATUS_CHANGED',
  `title` VARCHAR(255) NOT NULL,
  `content` VARCHAR(500) NOT NULL,
  `related_id` BIGINT DEFAULT NULL,
  `related_type` VARCHAR(30) DEFAULT NULL COMMENT 'ISSUE',
  `is_read` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_read` (`user_id`, `is_read`),
  INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 16. 团队聊天消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `team_id`     BIGINT       NOT NULL COMMENT '团队ID',
    `sender_id`   BIGINT       NOT NULL COMMENT '发送者用户ID',
    `sender_name` VARCHAR(64)  NOT NULL COMMENT '发送者名称',
    `sender_avatar` VARCHAR(128) DEFAULT '' COMMENT '发送者头像颜色',
    `content`     TEXT         NOT NULL COMMENT '消息内容（最多500字）',
    `recalled`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已撤回',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_team_created` (`team_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='团队聊天消息表';

-- ============================================================
-- 17. 审批请求表
-- ============================================================
CREATE TABLE IF NOT EXISTS `approval_requests` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审批ID',
    `team_id`       BIGINT       NOT NULL COMMENT '所属团队ID',
    `project_id`    BIGINT       DEFAULT NULL COMMENT '关联项目ID(项目操作时)',
    `requester_id`  BIGINT       NOT NULL COMMENT '申请人ID',
    `target_type`   VARCHAR(30)  NOT NULL COMMENT '操作类型: PROJECT_CREATE / PROJECT_UPDATE / PROJECT_DELETE / PROJECT_ADD_MEMBER',
    `title`         VARCHAR(200) NOT NULL COMMENT '审批标题',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '审批描述/原因',
    `data_json`     JSON         DEFAULT NULL COMMENT '操作数据(JSON)',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / APPROVED / REJECTED',
    `approver_id`   BIGINT       DEFAULT NULL COMMENT '审批人ID',
    `approved_at`   DATETIME     DEFAULT NULL COMMENT '审批时间',
    `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_team_status` (`team_id`, `status`),
    INDEX `idx_requester` (`requester_id`, `status`),
    INDEX `idx_approver` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批请求表';

-- ============================================================
-- 18. 用户等级表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_levels` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `user_id`       BIGINT       NOT NULL UNIQUE COMMENT '用户ID',
    `level`         INT          NOT NULL DEFAULT 1 COMMENT '等级: 1-4 (L1-L4)',
    `level_name`    VARCHAR(20)  NOT NULL DEFAULT 'L1' COMMENT '等级名称: L1/L2/L3/L4',
    `badge_color`   VARCHAR(20)  NOT NULL DEFAULT '#909399' COMMENT '铭牌颜色',
    `badge_icon`    VARCHAR(50)  DEFAULT NULL COMMENT '铭牌图标/样式',
    `updated_by`    BIGINT       DEFAULT NULL COMMENT '最后修改人(admin)',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_level` (`level`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户等级表';
