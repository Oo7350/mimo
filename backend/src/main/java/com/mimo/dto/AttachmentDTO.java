package com.mimo.dto;

import lombok.Data;

import java.time.LocalDateTime;

public class AttachmentDTO {

    @Data
    public static class AttachmentVO {
        private Long id;
        private Long issueId;
        private String fileName;
        private Long fileSize;
        private String fileType;
        private String uploaderName;
        private LocalDateTime createdAt;
    }
}
