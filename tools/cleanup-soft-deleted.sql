-- 物理清理"逻辑删除"的项目（v2.22.4）
--
-- 用法：mysql -u mimo -p mimo < tools/cleanup-soft-deleted.sql
--
-- 为什么会有这个脚本：项目的删除是逻辑删除（projects.deleted = 1），行还留在表里，
-- 它下挂的工作项、评论、附件、活动日志也都还在。界面上看不到它们（所有查询都带 deleted = 0），
-- 但表里占着位置、统计口径要到处记得排除、开发库做完演示/回归之后还会剩一堆。
-- 这就是计划书里那条「示例项目被逻辑删除后行仍在表里」的收尾。
--
-- 口径：只处理 deleted = 1 的项目，并且**连带清掉它的全部子行**，不留半截数据。
-- 不碰 deleted = 0 的项目，也不碰团队/用户/角色这些公共数据。
-- 建议先看第一段输出（将要删多少行），确认是自己要删的量再执行——脚本本身不会问你。

SELECT '将要清理的逻辑删除项目' AS step,
       (SELECT COUNT(*) FROM projects WHERE deleted = 1) AS projects,
       (SELECT COUNT(*) FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1)) AS issues,
       (SELECT COUNT(*) FROM activity_logs WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1)) AS activity_logs,
       (SELECT COUNT(*) FROM board_columns WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1)) AS board_columns;

-- 1. 工作项的子行（评论 / 附件 / 工时 / 变更与评审 / 版本 / 标签 / 缺陷关联）
DELETE FROM comments WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));
DELETE FROM attachments WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));
DELETE FROM work_logs WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));
DELETE FROM issue_change_requests WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));
DELETE FROM issue_reviews WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));
DELETE FROM issue_versions WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));
DELETE FROM issue_labels WHERE issue_id IN (
  SELECT id FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1));

-- 2. 工作项本体
DELETE FROM issues WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);

-- 3. 项目级的行（看板列 / 迭代 / 成员 / 工作流 / 报告 / 活动日志 / 审批 / 日历 / 文档 / webhook …）
DELETE FROM board_columns WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM sprints WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM burndown_snapshots WHERE sprint_id NOT IN (SELECT id FROM sprints);
DELETE FROM project_members WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM workflows WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM reports WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM activity_logs WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM approval_requests WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM calendar_events WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM rag_documents WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM delivery_daily_snapshots WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM webhook_configs WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);
DELETE FROM wiki_pages WHERE project_id IN (SELECT id FROM projects WHERE deleted = 1);

-- 4. 项目本体
DELETE FROM projects WHERE deleted = 1;

SELECT '清理后' AS step,
       (SELECT COUNT(*) FROM projects WHERE deleted = 1) AS soft_deleted_projects,
       (SELECT COUNT(*) FROM issues WHERE project_id NOT IN (SELECT id FROM projects)) AS orphan_issues,
       (SELECT COUNT(*) FROM projects) AS projects_total,
       (SELECT COUNT(*) FROM issues) AS issues_total;
