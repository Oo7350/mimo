-- Mimo v2.7.0: 团队管理员权限 + 审批流程 + 用户等级系统
-- 字符集: utf8mb4, 引擎: InnoDB

USE `mimo`;

-- ============================================================
-- 1. 审批请求表
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
-- 2. 用户等级表
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

-- ============================================================
-- 3. 初始化用户等级数据
-- ============================================================
INSERT INTO `user_levels` (`user_id`, `level`, `level_name`, `badge_color`, `badge_icon`) VALUES
(1, 4, 'L4', '#E6A23C', 'crown'),      -- admin: L4 金色皇冠
(2, 1, 'L1', '#909399', null),           -- zhangsan: L1 灰色
(3, 1, 'L1', '#909399', null);           -- lisi: L1 灰色

-- ============================================================
-- 4. 为现有团队成员添加到项目成员表(修复历史数据)
-- ============================================================
INSERT IGNORE INTO project_members (project_id, user_id, role, joined_at)
SELECT p.id, tm.user_id,
       CASE WHEN tm.role = 'ROLE_ADMIN' THEN 'ROLE_ADMIN' ELSE 'ROLE_MEMBER' END,
       NOW()
FROM projects p
JOIN team_members tm ON p.team_id = tm.team_id
WHERE NOT EXISTS (
    SELECT 1 FROM project_members pm WHERE pm.project_id = p.id AND pm.user_id = tm.user_id
);
