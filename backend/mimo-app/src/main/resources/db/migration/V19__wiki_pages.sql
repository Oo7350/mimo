-- v2.13.7：Wiki 文档系统（项目级知识沉淀，含树形目录/版本历史/全文检索）
CREATE TABLE IF NOT EXISTS `wiki_pages` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`   BIGINT       NOT NULL COMMENT '所属项目 ID',
    `parent_id`    BIGINT       NULL     COMMENT '父页面 ID（NULL=根节点）',
    `title`        VARCHAR(200) NOT NULL COMMENT '页面标题',
    `slug`         VARCHAR(200) NULL     COMMENT 'URL slug（保留字段）',
    `content`      LONGTEXT     NULL     COMMENT 'Markdown 正文',
    `content_html` LONGTEXT     NULL     COMMENT '渲染后 HTML 缓存（可选，前端渲染则留空）',
    `author_id`    BIGINT       NOT NULL COMMENT '作者用户 ID',
    `editor_id`    BIGINT       NULL     COMMENT '最后编辑人 ID',
    `version`      INT          NOT NULL DEFAULT 1 COMMENT '当前版本号（每次保存 +1）',
    `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '同级排序',
    `is_pinned`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=置顶',
    `view_count`   INT          NOT NULL DEFAULT 0 COMMENT '查看次数',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project_parent` (`project_id`, `parent_id`),
    INDEX `idx_project` (`project_id`),
    INDEX `idx_author` (`author_id`),
    INDEX `idx_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 页面';

CREATE TABLE IF NOT EXISTS `wiki_versions` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `page_id`        BIGINT       NOT NULL COMMENT '页面 ID',
    `version`        INT          NOT NULL COMMENT '版本号',
    `title`          VARCHAR(200) NOT NULL COMMENT '该版本标题快照',
    `content`        LONGTEXT     NOT NULL COMMENT '该版本正文快照',
    `editor_id`      BIGINT       NOT NULL COMMENT '编辑人 ID',
    `change_summary` VARCHAR(500) NULL     COMMENT '变更摘要',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_page_version` (`page_id`, `version`),
    INDEX `idx_page` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 页面版本历史';

CREATE TABLE IF NOT EXISTS `wiki_attachments` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `page_id`     BIGINT       NOT NULL COMMENT '所属页面 ID',
    `file_name`   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path`   VARCHAR(500) NOT NULL COMMENT '存储相对路径',
    `file_size`   BIGINT       NOT NULL COMMENT '字节数',
    `mime_type`   VARCHAR(100) NULL     COMMENT 'MIME 类型',
    `uploader_id` BIGINT       NOT NULL COMMENT '上传人 ID',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_page` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 附件';
