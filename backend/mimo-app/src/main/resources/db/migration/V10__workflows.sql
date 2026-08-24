-- V10: 可配置工作流引擎 — 2026-06-24
-- 每个项目可为不同 Issue 类型(STORY/TASK/BUG)定义自定义工作流
-- config JSON 结构:
-- {
--   "nodes": [
--     { "columnId": 1, "name": "待办", "color": "#909399", "isTerminal": false },
--     { "columnId": 2, "name": "进行中", "color": "#409EFF", "isTerminal": false },
--     { "columnId": 3, "name": "已完成", "color": "#67C23A", "isTerminal": true }
--   ],
--   "transitions": [
--     { "fromColumnId": 1, "toColumnId": 2, "conditions": [] },
--     { "fromColumnId": 2, "toColumnId": 3, "conditions": [] },
--     { "fromColumnId": 2, "toColumnId": 1, "conditions": [] }
--   ]
-- }
-- conditions 可选值: "ROLE_ADMIN", "ASSIGNEE", "REPORTER" (空数组 = 所有人可执行)

CREATE TABLE workflows (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL             COMMENT '所属项目',
    issue_type  VARCHAR(20)  NOT NULL             COMMENT 'STORY / TASK / BUG',
    name        VARCHAR(100) NOT NULL             COMMENT '工作流名称',
    config      JSON         NOT NULL             COMMENT '节点+转换规则',
    is_active   TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    is_default  TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '是否默认模板',
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_type (project_id, issue_type),
    INDEX idx_project (project_id)
);
