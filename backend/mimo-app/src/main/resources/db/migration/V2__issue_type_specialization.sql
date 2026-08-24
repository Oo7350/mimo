-- ============================================================
-- V2: Issue Type Specialization
-- 为 STORY/TASK/BUG 三种类型增加专属字段
-- ============================================================

ALTER TABLE issues
  ADD COLUMN IF NOT EXISTS user_role VARCHAR(50) DEFAULT NULL COMMENT 'STORY专属: 用户角色(作为...)',
  ADD COLUMN IF NOT EXISTS user_goal VARCHAR(500) DEFAULT NULL COMMENT 'STORY专属: 目标功能(我希望...)',
  ADD COLUMN IF NOT EXISTS business_value VARCHAR(500) DEFAULT NULL COMMENT 'STORY专属: 业务价值(以便...)',
  ADD COLUMN IF NOT EXISTS acceptance_criteria JSON DEFAULT NULL COMMENT 'STORY专属: 验收标准列表',
  ADD COLUMN IF NOT EXISTS environment VARCHAR(500) DEFAULT NULL COMMENT 'BUG专属: 环境信息',
  ADD COLUMN IF NOT EXISTS expected_result TEXT DEFAULT NULL COMMENT 'BUG专属: 期望结果',
  ADD COLUMN IF NOT EXISTS actual_result TEXT DEFAULT NULL COMMENT 'BUG专属: 实际结果',
  ADD COLUMN IF NOT EXISTS found_version VARCHAR(50) DEFAULT NULL COMMENT 'BUG专属: 发现版本',
  ADD COLUMN IF NOT EXISTS fixed_version VARCHAR(50) DEFAULT NULL COMMENT 'BUG专属: 修复版本',
  ADD COLUMN IF NOT EXISTS parent_id BIGINT DEFAULT NULL COMMENT 'STORY对TASK的父子关系',
  ADD COLUMN IF NOT EXISTS epic VARCHAR(100) DEFAULT NULL COMMENT 'STORY专属: 所属史诗',
  ADD COLUMN IF NOT EXISTS bug_status VARCHAR(20) DEFAULT NULL COMMENT 'BUG专属状态: NEW/CONFIRMED/IN_PROGRESS/RESOLVED/VERIFIED/CLOSED/REOPENED';

ALTER TABLE issues
  ADD INDEX IF NOT EXISTS idx_parent (parent_id),
  ADD INDEX IF NOT EXISTS idx_epic (epic),
  ADD INDEX IF NOT EXISTS idx_bug_status (bug_status);
