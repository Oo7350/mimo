package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReportDTO {

    @Data
    public static class CreateRequest {
        @NotNull
        private Long projectId;
        private String type = "DAILY";
        private LocalDate reportDate;
    }

    @Data
    public static class UpdateRequest {
        @NotNull
        private Long id;
        private String content;
    }

    @Data
    public static class SubmitRequest {
        @NotNull
        private Long id;
    }

    @Data
    public static class ReportVO {
        private Long id;
        private Long userId;
        private String username;
        private Long projectId;
        private String projectName;
        private String type;
        private LocalDate reportDate;
        private String content;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
