-- Mimo 测试/演示账号与团队（**只在开发与 CI 环境执行**）
--
-- 为什么单独一个文件：建表（V0__init.sql）与"给个默认口令的账号"是两件事。
-- 前者任何环境都该自动跑；后者在真实环境里是安全隐患，必须有人显式敲这条命令。
-- 以前这两半混在 init.sql 里，于是"要么全自动带上 admin/123456，要么全都不建表"
-- 二选一（BUG-123）。
--
-- 执行：
--   mysql -u mimo -p mimo < backend/mimo-app/src/main/resources/db/seed-demo.sql
--
-- 幂等：全部 INSERT IGNORE，配合 uk_username / uk_team_name / uk_team_user 三个唯一键，
-- 重复执行不会报错也不会造重复行。
--
-- 密码均为 123456（BCrypt）。

INSERT IGNORE INTO `users` (`id`, `username`, `password`, `email`, `role`) VALUES
(1, 'admin',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@mimo.dev',    'ROLE_ADMIN'),
(2, 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'zhangsan@mimo.dev', 'ROLE_MEMBER'),
(3, 'lisi',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'lisi@mimo.dev',     'ROLE_MEMBER');

INSERT IGNORE INTO `teams` (`id`, `name`, `description`, `owner_id`) VALUES
(1, 'Mimo开发团队', 'Mimo产品研发核心团队', 1);

INSERT IGNORE INTO `team_members` (`team_id`, `user_id`, `role`) VALUES
(1, 1, 'ROLE_ADMIN'),
(1, 2, 'ROLE_ADMIN'),
(1, 3, 'ROLE_MEMBER');

-- zhangsan 的团队角色是「旧字段兜底的团队管理员」：users.role 仍是 ROLE_MEMBER、
-- RBAC 里没有角色，但 team_members.role = ROLE_ADMIN。后端的 AccessControlService
-- 认这条旧字段（scope=TEAM），admin-flow 用例正是盯这个落差。
-- 这一行是"改正"而不是"插入"：INSERT IGNORE 只会补缺，补不了以前灌错的值
-- ——空库第一次演练时他就是被灌成普通成员，于是"全员"入口整个不出现（v2.22.2）。
UPDATE `team_members` SET `role` = 'ROLE_ADMIN'
WHERE `team_id` = 1 AND `user_id` = 2;
UPDATE `team_members` SET `role` = 'ROLE_MEMBER'
WHERE `team_id` = 1 AND `user_id` = 3;

-- RBAC 授权：迁移 V11 只在**启动时**按 role 字段补授权，这里显式补一遍，
-- 保证"刚灌完种子数据就能用管理员功能"，不用等下次重启。
INSERT INTO `user_roles` (`user_id`, `role_id`, `scope_type`, `scope_id`)
SELECT u.`id`, r.`id`, 'GLOBAL', NULL
FROM `users` u
JOIN `roles` r ON r.`code` = 'SUPER_ADMIN'
WHERE u.`role` = 'ROLE_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT * FROM `user_roles`) ur
      WHERE ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`
        AND ur.`scope_type` = 'GLOBAL' AND ur.`scope_id` IS NULL
  );

-- 每位演示用户的 L1 铭牌（注册接口会给新用户发一块，种子账号也得有，否则个人页缺一块）
INSERT INTO `user_levels` (`user_id`, `level`, `level_name`, `badge_color`)
SELECT u.`id`, 1, 'L1', '#909399'
FROM `users` u
WHERE u.`username` IN ('admin', 'zhangsan', 'lisi')
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `user_levels`) ul WHERE ul.`user_id` = u.`id`);
