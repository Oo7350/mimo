-- Mimo 量级测试数据（v2.22.4）
--
-- 用法：  mysql -u mimo -p mimo < tools/seed-load.sql
-- 清理：  mysql -u mimo -p mimo < tools/cleanup-load.sql
-- 规模：  100 用户 / 1 团队 / 5 项目 / 15 看板列 / 5 迭代 / 500 工作项 / 1000 评论 / 5000 活动日志
--
-- 为什么单独一个团队：e2e 与演示数据都锚在「团队 1 / 项目 1 / 演示项目」，量级数据一律挂在
-- `压测团队` 下，压完就整块删掉，不掺进回归。**跑全量 e2e 之前先 cleanup**，否则
-- 「全员任务流」这类跨团队的接口会被 100 个成员撑大，用例耗时与断言都会受影响。
--
-- 幂等：表上都有唯一键的用 INSERT IGNORE（users.uk_username / team_members.uk_team_user /
-- projects.uk_project_key / issues.uk_issue_key），没有唯一键的（看板列、迭代、评论、活动日志）
-- 用 NOT EXISTS 或「已存在就整段跳过」的守卫，重复执行不会翻倍。

SET SESSION cte_max_recursion_depth = 20000;

SET @pw = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi'; -- 明文 123456

DROP TEMPORARY TABLE IF EXISTS tools_seq;
CREATE TEMPORARY TABLE tools_seq (n INT PRIMARY KEY);
INSERT INTO tools_seq (n)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 5000) SELECT n FROM s;

-- 1. 团队
INSERT INTO teams (name, description, owner_id)
SELECT '压测团队', '量级测试专用，tools/cleanup-load.sql 可整块删除', (SELECT id FROM users WHERE username = 'admin')
WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '压测团队');

SET @team = (SELECT id FROM teams WHERE name = '压测团队' AND deleted = 0 LIMIT 1);

-- 2. 100 个用户 load001..load100（口令同演示账号 123456）
INSERT IGNORE INTO users (username, password, email, role)
SELECT CONCAT('load', LPAD(n, 3, '0')), @pw, CONCAT('load', n, '@mimo.test'), 'ROLE_MEMBER'
FROM tools_seq WHERE n <= 100;

-- 3. 入队：load001 是团队管理员，其余普通成员；admin 也加进去
--    （`/teams/my` 只返回"我是成员"的团队，不加这条的话探针会退回到演示项目，
--     压测数据等于白造；顺带也方便在界面上直接点开压测项目看）
INSERT IGNORE INTO team_members (team_id, user_id, role)
SELECT @team, id, IF(username = 'load001', 'ROLE_ADMIN', 'ROLE_MEMBER')
FROM users WHERE username LIKE 'load%';

INSERT IGNORE INTO team_members (team_id, user_id, role)
SELECT @team, id, 'ROLE_ADMIN' FROM users WHERE username = 'admin';

-- 4. 5 个项目 LOAD1..LOAD5
INSERT IGNORE INTO projects (name, `key`, description, template, team_id, owner_id)
SELECT CONCAT('压测项目', n), CONCAT('LOAD', n), '量级测试数据，可整块删除', 'SCRUM', @team,
       (SELECT id FROM users WHERE username = 'load001')
FROM tools_seq WHERE n <= 5;

-- 5. 每个项目 3 列（和看板的端态判定保持一致：列名就是唯一真源）
INSERT INTO board_columns (project_id, name, sort_order, color)
SELECT p.id, c.name, c.sort_order, c.color
FROM projects p
JOIN (
  SELECT '待办' AS name, 0 AS sort_order, '#409EFF' AS color
  UNION ALL SELECT '进行中', 1, '#E6A23C'
  UNION ALL SELECT '已完成', 2, '#67C23A'
) c
WHERE p.`key` LIKE 'LOAD%' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM board_columns bc WHERE bc.project_id = p.id AND bc.name = c.name);

-- 6. 每个项目 2 个迭代（第一个进行中），给「迭代规划/趋势」一点真实上下文
INSERT INTO sprints (project_id, name, goal, start_date, end_date, is_active, status)
SELECT p.id,
       CONCAT(p.name, ' 迭代', s.n),
       CONCAT('量级测试迭代 ', s.n),
       CURDATE() - INTERVAL (3 - s.n) * 7 DAY,
       CURDATE() + INTERVAL (1 + s.n) * 7 DAY,
       IF(s.n = 1, 1, 0),
       IF(s.n = 1, 'ACTIVE', 'PLANNING')
FROM projects p
JOIN (SELECT 1 AS n UNION ALL SELECT 2) s
WHERE p.`key` LIKE 'LOAD%' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sprints sp WHERE sp.project_id = p.id AND sp.name = CONCAT(p.name, ' 迭代', s.n)
  );

-- 7. 500 工作项：5 项目 × 100（状态/列/负责人/类型/优先级都打散，避免只测到一条分支）
INSERT IGNORE INTO issues (
  project_id, column_id, sprint_id, issue_key, title, type, priority, status,
  assignee_id, reporter_id, due_date, story_points, created_at, updated_at
)
SELECT
  p.id,
  (SELECT bc.id FROM board_columns bc
    WHERE bc.project_id = p.id
      AND bc.name = CASE s.n % 3 WHEN 0 THEN '已完成' WHEN 1 THEN '进行中' ELSE '待办' END
    LIMIT 1),
  (SELECT sp.id FROM sprints sp WHERE sp.project_id = p.id ORDER BY sp.id LIMIT 1),
  CONCAT(p.`key`, '-', s.n),
  CONCAT('压测工作项 ', p.`key`, '-', s.n),
  CASE s.n % 5 WHEN 0 THEN 'BUG' WHEN 1 THEN 'STORY' ELSE 'TASK' END,
  CASE s.n % 4 WHEN 0 THEN 'HIGHEST' WHEN 1 THEN 'HIGH' WHEN 2 THEN 'MEDIUM' ELSE 'LOW' END,
  CASE s.n % 3 WHEN 0 THEN 'DONE' WHEN 1 THEN 'IN_PROGRESS' ELSE 'TODO' END,
  (SELECT id FROM users WHERE username = CONCAT('load', LPAD(1 + (s.n % 100), 3, '0'))),
  (SELECT id FROM users WHERE username = 'load001'),
  CURDATE() + INTERVAL ((s.n % 30) - 10) DAY,
  (s.n % 8) + 1,
  NOW() - INTERVAL (s.n % 60) DAY,
  NOW() - INTERVAL (s.n % 30) DAY
FROM projects p
CROSS JOIN tools_seq s
WHERE p.`key` LIKE 'LOAD%' AND p.deleted = 0 AND s.n <= 100;

-- 8. 1000 条评论：每个工作项 2 条（评论数/最近评论是详情与列表的常见 N+1 触发点）
INSERT INTO comments (issue_id, user_id, username, content, created_at)
SELECT i.id, u.id, u.username, CONCAT('压测评论：', i.issue_key), NOW() - INTERVAL (i.id % 20) DAY
FROM issues i
JOIN (SELECT 1 AS n UNION ALL SELECT 2) k
JOIN users u ON u.username = CONCAT('load', LPAD(1 + ((i.id + k.n) % 100), 3, '0'))
WHERE i.issue_key LIKE 'LOAD%'
  AND NOT EXISTS (SELECT 1 FROM comments c WHERE c.issue_id = i.id AND c.content = CONCAT('压测评论：', i.issue_key));

-- 9. 5000 条活动日志（跨 30 天：近 7 天趋势、分页、时间范围查询都有真实量）
INSERT INTO activity_logs (user_id, username, project_id, target_type, target_id, action, detail, created_at)
SELECT u.id, u.username, i.project_id, 'ISSUE', i.id,
       CASE WHEN t.n % 3 = 0 THEN 'CREATE' ELSE 'MOVE' END,
       CONCAT('压测活动 ', i.issue_key, ' #', t.n),
       NOW() - INTERVAL (t.n % 30) DAY - INTERVAL (t.n % 24) HOUR
FROM tools_seq t
JOIN (
  SELECT id, project_id, issue_key, ROW_NUMBER() OVER (ORDER BY id) AS rn
  FROM issues WHERE issue_key LIKE 'LOAD%'
) i ON i.rn = (t.n % 500) + 1
JOIN users u ON u.username = CONCAT('load', LPAD(1 + (t.n % 100), 3, '0'))
WHERE t.n <= 5000
  AND NOT EXISTS (SELECT 1 FROM activity_logs WHERE detail LIKE '压测活动 %' LIMIT 1);

SELECT '压测团队' AS scope,
       (SELECT COUNT(*) FROM users WHERE username LIKE 'load%') AS users,
       (SELECT COUNT(*) FROM team_members tm JOIN teams t2 ON t2.id = tm.team_id WHERE t2.name = '压测团队') AS members,
       (SELECT COUNT(*) FROM projects WHERE `key` LIKE 'LOAD%' AND deleted = 0) AS projects,
       (SELECT COUNT(*) FROM issues WHERE issue_key LIKE 'LOAD%') AS issues,
       (SELECT COUNT(*) FROM comments WHERE content LIKE '压测评论：%') AS comments,
       (SELECT COUNT(*) FROM activity_logs WHERE detail LIKE '压测活动 %') AS activity_logs;
