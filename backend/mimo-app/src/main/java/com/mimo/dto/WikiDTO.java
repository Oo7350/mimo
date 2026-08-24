package com.mimo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiki 文档系统 DTO
 */
public class WikiDTO {

    @Data
    public static class CreateRequest {
        private Long projectId;
        private Long parentId;
        private String title;
        private String content;
        private String changeSummary;
    }

    @Data
    public static class UpdateRequest {
        private Long parentId;
        private String title;
        private String content;
        private String changeSummary;
    }

    @Data
    public static class PageVO {
        private Long id;
        private Long projectId;
        private Long parentId;
        private String title;
        private String content;
        private Integer version;
        private Long authorId;
        private String authorName;
        private Long editorId;
        private String editorName;
        private Integer isPinned;
        private Integer viewCount;
        private Integer sortOrder;
        private Integer childCount;
        private String createdAt;
        private String updatedAt;
    }

    /** 目录树节点（不含 content 大字段） */
    @Data
    public static class TreeVO {
        private Long id;
        private Long projectId;
        private Long parentId;
        private String title;
        private Integer version;
        private String authorName;
        private String editorName;
        private Integer isPinned;
        private Integer viewCount;
        private Integer sortOrder;
        private Integer childCount;
        private String updatedAt;
        private List<TreeVO> children = new ArrayList<>();
    }

    @Data
    public static class VersionVO {
        private Long id;
        private Long pageId;
        private Integer version;
        private String title;
        private String content;
        private String changeSummary;
        private String editorName;
        private String createdAt;
    }

    @Data
    public static class AttachmentVO {
        private Long id;
        private Long pageId;
        private String fileName;
        private Long fileSize;
        private String mimeType;
        private String uploaderName;
        private String createdAt;
    }
}
