package com.mimo.dto;

import lombok.Data;

public class AnnouncementDTO {

    @Data
    public static class CreateRequest {
        private String title;
        private String content;
        private Integer pinned;
    }

    @Data
    public static class UpdateRequest {
        private String title;
        private String content;
        private Integer pinned;
    }

    @Data
    public static class VO {
        private Long id;
        private String title;
        private String content;
        private Integer pinned;
        private Long createdBy;
        private String createdByName;
        private String createdAt;
        private String updatedAt;
    }
}
