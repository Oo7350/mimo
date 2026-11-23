-- e2e 残留清理（开发库专用，可重复执行）
--
-- 背景：e2e 用例会自己造数据。issue 级的用例大多删得干净，但注册出来的账号、
-- 它们建的团队/项目删不掉——删项目要走审批流程，脚本没有合适的"我来删自己刚建的东西"通道。
-- 跑多了开发库就会累积一堆 `probe-*` / `wiki-probe-*` / `chain*` 账号和「链路团队/项目」。
--
-- 用法（本地）：
--   mysql -umimo -pmimo123 mimo < e2e/cleanup-residue.sql
--
-- 只删带上面这些前缀/名字的用例数据，不碰 admin/zhangsan/lisi 和真实项目。
-- 生产库不要执行。

SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. 定位要删的账号 ----------
DROP TABLE IF EXISTS tmp_users;
CREATE TABLE tmp_users (id BIGINT PRIMARY KEY);
INSERT INTO tmp_users
SELECT id FROM users
WHERE username LIKE 'probe-%'
   OR username LIKE 'wiki-probe-%'
   OR username LIKE 'chain%'
   OR username LIKE 'e2e-%';

-- ---------- 2. 它们建的项目 + 名字带「链路项目」的 ----------
DROP TABLE IF EXISTS tmp_projects;
CREATE TABLE tmp_projects (id BIGINT PRIMARY KEY);
INSERT INTO tmp_projects
SELECT id FROM projects
WHERE owner_id IN (SELECT id FROM tmp_users)
   OR name LIKE '链路项目%'
   OR name LIKE 'e2e项目%';

-- ---------- 3. 要删的 issue ----------
DROP TABLE IF EXISTS tmp_issues;
CREATE TABLE tmp_issues (id BIGINT PRIMARY KEY);
INSERT INTO tmp_issues
SELECT id FROM issues
WHERE project_id IN (SELECT id FROM tmp_projects)
   OR reporter_id IN (SELECT id FROM tmp_users)
   OR assignee_id IN (SELECT id FROM tmp_users)
   OR title LIKE 'E2E任务%'
   OR title LIKE '工作流用例%';

DELETE FROM comments WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM work_logs WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM issue_reviews WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM issue_versions WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM issue_labels WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM issue_change_requests WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM attachments WHERE issue_id IN (SELECT id FROM tmp_issues);
DELETE FROM issues WHERE id IN (SELECT id FROM tmp_issues);

-- ---------- 4. 项目级数据 ----------
DELETE FROM activity_logs WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM board_columns WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM sprints WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM reports WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM project_members WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM wiki_pages WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM workflows WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM delivery_daily_snapshots WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM rag_documents WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM calendar_events WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM webhook_configs WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM approval_requests WHERE project_id IN (SELECT id FROM tmp_projects);
DELETE FROM projects WHERE id IN (SELECT id FROM tmp_projects);

-- ---------- 5. 它们建的团队 ----------
DROP TABLE IF EXISTS tmp_teams;
CREATE TABLE tmp_teams (id BIGINT PRIMARY KEY);
INSERT INTO tmp_teams
SELECT id FROM teams
WHERE owner_id IN (SELECT id FROM tmp_users)
   OR name LIKE '链路团队%'
   OR name LIKE 'e2e团队%';

DELETE FROM chat_messages WHERE team_id IN (SELECT id FROM tmp_teams);
DELETE FROM calendar_events WHERE team_id IN (SELECT id FROM tmp_teams);
DELETE FROM team_members WHERE team_id IN (SELECT id FROM tmp_teams);
DELETE FROM webhook_configs WHERE team_id IN (SELECT id FROM tmp_teams);
DELETE FROM approval_requests WHERE team_id IN (SELECT id FROM tmp_teams);
DELETE FROM teams WHERE id IN (SELECT id FROM tmp_teams);

-- ---------- 6. 账号自身 ----------
DELETE FROM project_members WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM team_members WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM comments WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM chat_messages WHERE sender_id IN (SELECT id FROM tmp_users);
DELETE FROM work_logs WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM activity_logs WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM notifications WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM reports WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM calendar_events WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM user_levels WHERE user_id IN (SELECT id FROM tmp_users);
DELETE FROM users WHERE id IN (SELECT id FROM tmp_users);

SET FOREIGN_KEY_CHECKS = 1;

-- ---------- 结果 ----------
SELECT (SELECT COUNT(*) FROM users)    AS users,
       (SELECT COUNT(*) FROM teams)    AS teams,
       (SELECT COUNT(*) FROM projects) AS projects,
       (SELECT COUNT(*) FROM issues WHERE deleted = 0) AS issues_live,
       (SELECT COUNT(*) FROM issues WHERE title LIKE 'E2E任务%') AS leftover_e2e;
