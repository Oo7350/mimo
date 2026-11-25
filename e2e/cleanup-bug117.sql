-- BUG-117 现场验证数据清理（项目 43 / 团队 39）
DELETE FROM board_columns WHERE project_id = 43;
DELETE FROM workflows WHERE project_id = 43;
DELETE FROM wiki_pages WHERE project_id = 43;
DELETE FROM activity_logs WHERE project_id = 43;
DELETE FROM issues WHERE project_id = 43;
DELETE FROM projects WHERE id = 43;
DELETE FROM team_members WHERE team_id = 39;
DELETE FROM teams WHERE id = 39;
SELECT (SELECT COUNT(*) FROM projects WHERE id = 43) AS projects_left,
       (SELECT COUNT(*) FROM teams WHERE id = 39) AS teams_left,
       (SELECT COUNT(*) FROM board_columns WHERE project_id = 43) AS columns_left,
       (SELECT COUNT(*) FROM workflows WHERE project_id = 43) AS workflows_left,
       (SELECT COUNT(*) FROM wiki_pages WHERE project_id = 43) AS wiki_left;
