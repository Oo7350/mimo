-- v2.15.5: Project knowledge index for RAG
CREATE TABLE IF NOT EXISTS `rag_documents` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`        BIGINT       NOT NULL,
    `source_type`       VARCHAR(20)  NOT NULL COMMENT 'WIKI / ISSUE / COMMENT',
    `source_id`         BIGINT       NOT NULL,
    `parent_id`         BIGINT       DEFAULT NULL COMMENT '评论所属 Issue ID',
    `title`             VARCHAR(300) NOT NULL,
    `chunk_index`       INT          NOT NULL DEFAULT 0,
    `content`           LONGTEXT     NOT NULL,
    `source_url`        VARCHAR(500) DEFAULT NULL,
    `source_updated_at` DATETIME     DEFAULT NULL,
    `indexed_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_source_chunk` (`project_id`, `source_type`, `source_id`, `chunk_index`),
    KEY `idx_rag_project` (`project_id`),
    KEY `idx_rag_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目 RAG 知识片段索引';
