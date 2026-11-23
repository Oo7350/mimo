-- V12: 修复 V11 迁移条件 — User.role 实际存储为 'ROLE_ADMIN' / 'ROLE_MEMBER'（带前缀）
-- V11 用 u.role = 'ADMIN' / 'MEMBER' 匹配失败，导致 user_roles 表为空
-- 本脚本重新迁移，使用正确的前缀

-- ------------------------------------------------------------------
-- 历史重复授权清理（BUG-098）
-- GLOBAL 行的 scope_id 为 NULL，MySQL 唯一键 uk_user_role_scope 对 NULL 不生效，
-- 而 MigrationRunner 每次启动都会重跑迁移脚本 → 每个用户堆了十几份完全相同的授权。
-- 这里按 (user_id, role_id, scope_type, scope_id) 只保留 id 最小的一条。语句本身可重复执行。
-- ------------------------------------------------------------------
DELETE dup FROM user_roles dup
JOIN user_roles keep
  ON dup.user_id   = keep.user_id
 AND dup.role_id   = keep.role_id
 AND dup.scope_type = keep.scope_type
 AND (dup.scope_id = keep.scope_id OR (dup.scope_id IS NULL AND keep.scope_id IS NULL))
 AND dup.id > keep.id;

-- 幂等插入：不用 INSERT IGNORE（NULL 唯一键不生效），显式 NOT EXISTS 判重
INSERT INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'GLOBAL', NULL
FROM users u
JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.role IN ('ADMIN', 'ROLE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT * FROM user_roles) ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
        AND ur.scope_type = 'GLOBAL' AND ur.scope_id IS NULL
  );

INSERT INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'GLOBAL', NULL
FROM users u
JOIN roles r ON r.code = 'DEVELOPER'
WHERE u.role IN ('MEMBER', 'ROLE_MEMBER')
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT * FROM user_roles) ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
        AND ur.scope_type = 'GLOBAL' AND ur.scope_id IS NULL
  );
