-- V8: 工时记录 (WorkLog) — 2026-06-18
-- 用于追踪每个 Issue 上花费的实际工时（小时），支持 Sprint 工时汇总与团队工时报表

CREATE TABLE IF NOT EXISTS work_logs (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    issue_id        BIGINT          NOT NULL                                COMMENT '关联 Issue ID',
    user_id         BIGINT          NOT NULL                                COMMENT '工时所属用户',
    work_date       DATE            NOT NULL                                COMMENT '工时日期',
    hours           DECIMAL(6, 2)   NOT NULL                                COMMENT '工时（小时，最大 9999.99）',
    description     VARCHAR(500)    DEFAULT NULL                            COMMENT '工时说明 / 完成了什么',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    KEY idx_work_logs_issue (issue_id),
    KEY idx_work_logs_user_date (user_id, work_date),
    KEY idx_work_logs_date (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工时记录表';
