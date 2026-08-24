-- ============================================================
-- V11: 细粒度 RBAC 权限系统
-- 参考 项目功能完善计划.md §4.2
-- ============================================================

-- 权限点表
CREATE TABLE IF NOT EXISTS permissions (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE COMMENT '权限编码 如 issue.delete',
    name        VARCHAR(50)  NOT NULL             COMMENT '权限名称',
    module      VARCHAR(30)  NOT NULL             COMMENT '所属模块 project/issue/sprint/board/report/team/system/wiki',
    description VARCHAR(200)                      COMMENT '描述',
    sort_order  INT          NOT NULL DEFAULT 0,
    INDEX idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL             COMMENT '角色名称',
    code        VARCHAR(50)  NOT NULL UNIQUE       COMMENT '角色编码 如 PROJECT_MANAGER',
    is_system   TINYINT(1)   NOT NULL DEFAULT 0   COMMENT '系统内置不可删',
    description VARCHAR(200),
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-角色关联表（多对多，允许用户在不同团队/项目有不同角色）
CREATE TABLE IF NOT EXISTS user_roles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    role_id     INT    NOT NULL,
    scope_type  VARCHAR(20) NOT NULL COMMENT 'GLOBAL / TEAM / PROJECT',
    scope_id    BIGINT       DEFAULT NULL COMMENT 'team_id 或 project_id，GLOBAL 时为 NULL',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role_scope (user_id, role_id, scope_type, scope_id),
    INDEX idx_user (user_id),
    INDEX idx_scope (scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 预置权限点
-- ============================================================
INSERT INTO permissions (code, name, module, description, sort_order) VALUES
-- 项目模块
('project.create',            '创建项目',      'project', '创建新项目',                   10),
('project.view',              '查看项目',      'project', '查看项目详情',                 11),
('project.edit',              '编辑项目',      'project', '修改项目信息',                 12),
('project.delete',            '删除项目',      'project', '删除整个项目',                 13),
('project.member.invite',     '邀请成员',      'project', '邀请用户加入项目',           14),
('project.member.remove',     '移除成员',      'project', '从项目移除成员',             15),
('project.member.manage',     '管理成员',      'project', '修改项目成员角色',           16),
('project.settings',          '项目设置',      'project', '修改项目配置、看板列等',     17),
-- 任务模块
('issue.create',              '创建任务',      'issue',   '新建 Issue',                   20),
('issue.view',                '查看任务',      'issue',   '查看 Issue 详情',             21),
('issue.edit',                '编辑任务',      'issue',   '修改 Issue 信息',              22),
('issue.delete',              '删除任务',      'issue',   '删除 Issue',                   23),
('issue.assign',             '分配任务',      'issue',   '指派/改派 Issue',              24),
('issue.move',               '移动任务',      'issue',   '拖拽看板卡片',                 25),
('issue.change-status',      '变更状态',      'issue',   '修改 Issue 状态',             26),
('issue.comment',            '发表评论',      'issue',   '在 Issue 下评论',             27),
('issue.attach',             '上传附件',      'issue',   '为 Issue 上传附件',           28),
('issue.manage-labels',      '管理标签',      'issue',   '增删改 Issue 标签',           29),
-- Sprint 模块
('sprint.create',            '创建 Sprint',   'sprint',  '新建 Sprint',                  30),
('sprint.start',             '启动 Sprint',   'sprint',  '启动 Sprint',                  31),
('sprint.complete',          '完成 Sprint',   'sprint',  '归档 Sprint',                  32),
('sprint.manage',            '管理 Sprint',   'sprint',  'Sprint 全部增删改查',         33),
-- 看板模块
('board.view',               '查看看板',      'board',   '查看看板',                    40),
('board.configure',          '配置看板',      'board',   '增删看板列、泳道',            41),
('board.export',             '导出看板',      'board',   '导出看板为 CSV/PDF',          42),
-- 报告模块
('report.create',            '创建报告',      'report',  '提交日报/周报',                 50),
('report.view',              '查看报告',      'report',  '查看自己的报告',               51),
('report.export',            '导出报告',      'report',  '导出报告为 PDF',               52),
('report.manage-all',        '管理所有报告',  'report',  '查看团队所有成员报告',         53),
-- 团队模块
('team.create',             '创建团队',      'team',    '创建新团队',                    60),
('team.manage',             '管理团队',      'team',    '修改团队信息',                  61),
('team.member.manage',      '管理团队成员',  'team',    '邀移除团队成员、修改角色',     62),
('team.settings',           '团队设置',      'team',    '修改团队配置',                  63),
-- 系统管理
('user.manage',             '用户管理',      'system',  '增删改用户账号',                70),
('level.manage',            '等级管理',      'system',  '用户等级配置',                 71),
('approval.manage',         '审批管理',      'system',  '审批申请',                     72),
('audit.log.view',          '审计日志',      'system',  '查看操作审计日志',             73),
('settings.manage',         '系统设置',      'system',  '修改全局配置',                 74),
('ai.manage',               'AI 管理',       'system',  '管理 AI 配额与策略',           75),
('role.manage',             '角色管理',      'system',  '管理 RBAC 角色与权限分配',     76),
-- 工作流模块
('workflow.design',         '设计工作流',    'system',  '配置项目工作流',               80),
-- Wiki 模块（预留）
('wiki.create',             '创建 Wiki',     'wiki',    '新建 Wiki 页面',                90),
('wiki.edit',               '编辑 Wiki',     'wiki',    '修改 Wiki 内容',               91),
('wiki.delete',             '删除 Wiki',     'wiki',    '删除 Wiki 页面',               92),
('wiki.admin',              'Wiki 管理',     'wiki',    '管理 Wiki 权限与设置',         93)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================
-- 预置角色
-- ============================================================
INSERT INTO roles (name, code, is_system, description, sort_order) VALUES
('超级管理员', 'SUPER_ADMIN',     1, '拥有全部权限，不可删除',           1),
('项目经理',   'PROJECT_MANAGER', 1, '项目全权限 + 团队成员管理',        2),
('产品负责人', 'PRODUCT_OWNER',   1, 'Story 管理 + 优先级 + Sprint 规划', 3),
('开发者',     'DEVELOPER',        1, '创建/编辑/移动任务 + 评论 + 工时', 4),
('测试人员',   'TESTER',           1, '创建 Bug + 验证关闭 + 评论',       5),
('访客',       'VIEWER',           1, '只读查看',                         6),
('报表管理员', 'REPORTER',         1, 'Dashboard + 报表导出',            7)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================
-- 角色-权限映射
-- ============================================================

-- SUPER_ADMIN：所有权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE role_id = r.id;

-- PROJECT_MANAGER：项目、任务、Sprint、看板、团队、报告
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PROJECT_MANAGER'
  AND p.code IN (
    'project.create','project.view','project.edit','project.member.invite','project.member.remove','project.member.manage','project.settings',
    'issue.create','issue.view','issue.edit','issue.delete','issue.assign','issue.move','issue.change-status','issue.comment','issue.attach','issue.manage-labels',
    'sprint.create','sprint.start','sprint.complete','sprint.manage',
    'board.view','board.configure','board.export',
    'report.create','report.view','report.export','report.manage-all',
    'team.manage','team.member.manage','team.settings',
    'wiki.create','wiki.edit','wiki.delete','wiki.admin',
    'workflow.design'
  )
ON DUPLICATE KEY UPDATE role_id = r.id;

-- PRODUCT_OWNER：Story/Sprint/优先级
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PRODUCT_OWNER'
  AND p.code IN (
    'project.view',
    'issue.create','issue.view','issue.edit','issue.assign','issue.move','issue.change-status','issue.comment','issue.attach','issue.manage-labels',
    'sprint.create','sprint.start','sprint.complete','sprint.manage',
    'board.view','board.export',
    'report.view',
    'wiki.create','wiki.edit'
  )
ON DUPLICATE KEY UPDATE role_id = r.id;

-- DEVELOPER：创建/编辑/移动任务 + 评论 + 工时
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'DEVELOPER'
  AND p.code IN (
    'project.view',
    'issue.create','issue.view','issue.edit','issue.assign','issue.move','issue.change-status','issue.comment','issue.attach',
    'sprint.view' IS NULL, -- Sprint 没有 view，沿用 sprint.manage 之外的隐式读权限
    'board.view','board.export',
    'report.create','report.view',
    'wiki.edit'
  ) IS NULL
ON DUPLICATE KEY UPDATE role_id = r.id;

-- 重新插入 DEVELOPER（避免上面的 IS NULL 技巧混淆）
DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE code = 'DEVELOPER');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'DEVELOPER'
  AND p.code IN (
    'project.view',
    'issue.create','issue.view','issue.edit','issue.assign','issue.move','issue.change-status','issue.comment','issue.attach',
    'board.view','board.export',
    'report.create','report.view',
    'wiki.edit'
  )
ON DUPLICATE KEY UPDATE role_id = r.id;

-- TESTER：创建 Bug + 验证关闭 + 评论
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'TESTER'
  AND p.code IN (
    'project.view',
    'issue.create','issue.view','issue.edit','issue.change-status','issue.comment','issue.attach',
    'board.view',
    'report.view',
    'wiki.edit'
  )
ON DUPLICATE KEY UPDATE role_id = r.id;

-- VIEWER：只读
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'VIEWER'
  AND p.code IN (
    'project.view','issue.view','board.view','report.view'
  )
ON DUPLICATE KEY UPDATE role_id = r.id;

-- REPORTER：Dashboard + 报表导出
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'REPORTER'
  AND p.code IN (
    'project.view','board.view',
    'report.create','report.view','report.export','report.manage-all'
  )
ON DUPLICATE KEY UPDATE role_id = r.id;

-- ============================================================
-- 迁移现有 User.role 字符串 → user_roles
-- 仅迁移存在 role 字符串的用户到 GLOBAL SUPER_ADMIN / 其他
-- ============================================================
INSERT IGNORE INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'GLOBAL', NULL
FROM users u
JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.role = 'ADMIN';

INSERT IGNORE INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'GLOBAL', NULL
FROM users u
JOIN roles r ON r.code = 'DEVELOPER'
WHERE u.role = 'MEMBER';
