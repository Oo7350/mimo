-- V12: 修复 V11 迁移条件 — User.role 实际存储为 'ROLE_ADMIN' / 'ROLE_MEMBER'（带前缀）
-- V11 用 u.role = 'ADMIN' / 'MEMBER' 匹配失败，导致 user_roles 表为空
-- 本脚本重新迁移，使用正确的前缀

INSERT IGNORE INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'GLOBAL', NULL
FROM users u
JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.role IN ('ADMIN', 'ROLE_ADMIN');

INSERT IGNORE INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'GLOBAL', NULL
FROM users u
JOIN roles r ON r.code = 'DEVELOPER'
WHERE u.role IN ('MEMBER', 'ROLE_MEMBER');
