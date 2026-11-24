-- 数据完整性体检（KNOWN-007 的补偿手段）
--
-- 背景：本项目**故意不建数据库外键**——MyBatis-Plus 的逻辑删除（deleted=1）和外键约束
-- 相处不好，硬加外键会让"删除项目"变成一堆级联失败。代价是孤儿数据要靠应用层保证。
-- 既然不建外键，就得有能随时跑一遍的检查，而不是"相信代码没错"。
--
-- 用法（开发库示例）：
--   mysql -umimo -pmimo123 mimo < tools/integrity-check.sql
-- 输出里每一行 cnt 都是 0 才算干净；不为 0 就去查对应表。
--
-- 注意两点：
--   1. 只有 announcements / issues / projects / teams / users / user_email_accounts
--      这几张表有 deleted 列（逻辑删除），其它表是物理删除，别乱加 deleted=0。
--   2. 逻辑删除的行不算孤儿——它们本来就该被忽略，所以有 deleted 列的都带 deleted=0。

USE mimo;

SELECT '项目没有归属团队' AS issue, COUNT(*) AS cnt
FROM projects p
WHERE p.deleted = 0 AND p.team_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM teams t WHERE t.id = p.team_id AND t.deleted = 0)

UNION ALL
SELECT '工作项没有归属项目', COUNT(*)
FROM issues i
WHERE i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = i.project_id AND p.deleted = 0)

UNION ALL
SELECT '工作项指向不存在的看板列', COUNT(*)
FROM issues i
WHERE i.deleted = 0 AND i.column_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM board_columns c WHERE c.id = i.column_id)

UNION ALL
SELECT '工作项负责人不是该团队成员', COUNT(*)
FROM issues i
JOIN projects p ON p.id = i.project_id AND p.deleted = 0
WHERE i.deleted = 0 AND i.assignee_id IS NOT NULL AND p.team_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM team_members tm
                  WHERE tm.team_id = p.team_id AND tm.user_id = i.assignee_id)

UNION ALL
SELECT '评论没有归属工作项', COUNT(*)
FROM comments c
WHERE NOT EXISTS (SELECT 1 FROM issues i WHERE i.id = c.issue_id AND i.deleted = 0)

UNION ALL
SELECT '工时记录没有归属工作项', COUNT(*)
FROM work_logs w
WHERE NOT EXISTS (SELECT 1 FROM issues i WHERE i.id = w.issue_id AND i.deleted = 0)

UNION ALL
SELECT '验收记录没有归属工作项', COUNT(*)
FROM issue_reviews r
WHERE NOT EXISTS (SELECT 1 FROM issues i WHERE i.id = r.issue_id AND i.deleted = 0)

UNION ALL
SELECT '附件没有归属工作项', COUNT(*)
FROM attachments a
WHERE a.issue_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM issues i WHERE i.id = a.issue_id AND i.deleted = 0)

UNION ALL
SELECT '团队成员指向不存在的用户', COUNT(*)
FROM team_members tm
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = tm.user_id AND u.deleted = 0)

UNION ALL
SELECT '团队成员指向不存在的团队', COUNT(*)
FROM team_members tm
WHERE NOT EXISTS (SELECT 1 FROM teams t WHERE t.id = tm.team_id AND t.deleted = 0)

UNION ALL
SELECT '项目成员指向不存在的项目', COUNT(*)
FROM project_members pm
WHERE NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = pm.project_id AND p.deleted = 0)

UNION ALL
SELECT '迭代不属于任何项目', COUNT(*)
FROM sprints s
WHERE NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = s.project_id AND p.deleted = 0)

UNION ALL
SELECT '燃尽快照指向不存在的迭代', COUNT(*)
FROM burndown_snapshots b
WHERE NOT EXISTS (SELECT 1 FROM sprints s WHERE s.id = b.sprint_id)

UNION ALL
SELECT '通知指向不存在的接收人', COUNT(*)
FROM notifications n
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = n.user_id AND u.deleted = 0)

UNION ALL
SELECT '工作项标签挂在不存在的对象上', COUNT(*)
FROM issue_labels il
WHERE NOT EXISTS (SELECT 1 FROM issues i WHERE i.id = il.issue_id AND i.deleted = 0)

UNION ALL
SELECT 'Wiki 页面不属于任何项目', COUNT(*)
FROM wiki_pages w
WHERE NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = w.project_id AND p.deleted = 0);
