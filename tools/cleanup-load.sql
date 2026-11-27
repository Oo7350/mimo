-- 删除 tools/seed-load.sql 造出来的全部量级测试数据（v2.22.4）
--
-- 用法：mysql -u mimo -p mimo < tools/cleanup-load.sql
--
-- 口径：只删「压测团队」及其下 5 个 LOAD 项目、load 用户、它们的评论与活动日志。
-- 演示团队（团队 1）、演示项目、e2e 残留一律不碰。跑完再看一眼你关心的数字。

SET @team = (SELECT id FROM teams WHERE name = '压测团队' LIMIT 1);
SET @load_owner = (SELECT id FROM users WHERE username = 'load001' LIMIT 1);

-- 1. 活动日志：按项目 id 删（压测项目的活动日志 project_id 一定在 LOAD 项目里）
DELETE FROM activity_logs
WHERE project_id IN (SELECT id FROM projects WHERE `key` LIKE 'LOAD%')
   OR detail LIKE '压测活动 %';

-- 2. 评论：挂在 LOAD 工作项上的
DELETE FROM comments WHERE issue_id IN (SELECT id FROM issues WHERE issue_key LIKE 'LOAD%');

-- 3. 工作项：连带子任务/父子关系一起删（按 key 前缀，物理删除）
DELETE FROM issues WHERE issue_key LIKE 'LOAD%';

-- 4. 看板列 / 迭代 / 项目
DELETE FROM board_columns WHERE project_id IN (SELECT id FROM projects WHERE `key` LIKE 'LOAD%');
DELETE FROM sprints WHERE project_id IN (SELECT id FROM projects WHERE `key` LIKE 'LOAD%');
DELETE FROM projects WHERE `key` LIKE 'LOAD%';

-- 5. 成员关系 / 团队 / 用户（只删 load 前缀的账号，别碰真实账号）
DELETE FROM team_members WHERE team_id = @team;
DELETE FROM teams WHERE name = '压测团队';
DELETE FROM user_roles WHERE user_id IN (
  SELECT id FROM (SELECT id FROM users WHERE username LIKE 'load%') u
);
DELETE FROM user_levels WHERE user_id IN (
  SELECT id FROM (SELECT id FROM users WHERE username LIKE 'load%') u
);
DELETE FROM users WHERE username LIKE 'load%';

SELECT '清理后' AS scope,
       (SELECT COUNT(*) FROM users WHERE username LIKE 'load%') AS left_load_users,
       (SELECT COUNT(*) FROM teams WHERE name = '压测团队') AS left_load_teams,
       (SELECT COUNT(*) FROM projects WHERE `key` LIKE 'LOAD%') AS left_load_projects,
       (SELECT COUNT(*) FROM issues WHERE issue_key LIKE 'LOAD%') AS left_load_issues,
       (SELECT COUNT(*) FROM activity_logs WHERE detail LIKE '压测活动 %') AS left_load_logs,
       (SELECT COUNT(*) FROM users) AS users_total,
       (SELECT COUNT(*) FROM issues WHERE deleted = 0) AS issues_total;
