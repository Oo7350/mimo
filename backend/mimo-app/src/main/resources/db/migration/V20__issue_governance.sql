-- Phase 2.1 P0: issue versioning, change requests and task reviews

CREATE TABLE IF NOT EXISTS `issue_versions` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `issue_id`       BIGINT       NOT NULL COMMENT '工作项ID',
    `version_no`     INT          NOT NULL COMMENT '版本号，从1开始',
    `snapshot_json`  JSON         NOT NULL COMMENT '工作项字段快照',
    `change_summary` VARCHAR(500) DEFAULT NULL COMMENT '变更摘要',
    `source`         VARCHAR(30)  NOT NULL COMMENT 'CREATE/DIRECT_EDIT/CHANGE_APPROVED/ACCEPTANCE_CRITERIA',
    `operator_id`    BIGINT       NOT NULL COMMENT '操作人',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_issue_version` (`issue_id`, `version_no`),
    INDEX `idx_issue_created` (`issue_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作项版本快照';

CREATE TABLE IF NOT EXISTS `issue_change_requests` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `issue_id`        BIGINT        NOT NULL COMMENT '工作项ID',
    `base_version`    INT           NOT NULL COMMENT '申请基于的版本号',
    `proposed_changes` JSON         NOT NULL COMMENT '待变更字段',
    `change_reason`   VARCHAR(1000) NOT NULL COMMENT '变更理由',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN',
    `applicant_id`    BIGINT        NOT NULL,
    `reviewer_id`     BIGINT        DEFAULT NULL,
    `review_note`     VARCHAR(1000) DEFAULT NULL,
    `reviewed_at`     DATETIME      DEFAULT NULL,
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_issue_status` (`issue_id`, `status`),
    INDEX `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作项变更申请';

CREATE TABLE IF NOT EXISTS `issue_reviews` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `issue_id`         BIGINT        NOT NULL COMMENT '工作项ID',
    `round_no`         INT           NOT NULL COMMENT '验收轮次',
    `submitter_id`     BIGINT        NOT NULL,
    `completion_note`  VARCHAR(2000) DEFAULT NULL,
    `evidence_urls`    JSON          DEFAULT NULL COMMENT '验收证据地址列表',
    `status`           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    `reviewer_id`      BIGINT        DEFAULT NULL,
    `review_note`      VARCHAR(1000) DEFAULT NULL,
    `submitted_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_at`      DATETIME      DEFAULT NULL,
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_issue_review_round` (`issue_id`, `round_no`),
    INDEX `idx_issue_status` (`issue_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作项验收记录';
