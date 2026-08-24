package com.mimo.dto;

import lombok.Data;

public class NotificationDTO {

    @Data
    public static class NotificationVO {
        private Long id;
        private String type;
        private String title;
        private String content;
        private Long relatedId;
        private String relatedType;
        private Integer isRead;
        private String createdAt;
    }

    @Data
    public static class UnreadCountVO {
        private int count;
    }
}
