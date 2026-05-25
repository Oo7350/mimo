package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class IssueDTO {

    @Data
    public static class CreateRequest {
        @NotNull
        private Long projectId;
        private Long columnId;
        private Long sprintId;
        @NotBlank(message = "标题不能为空")
        private String title;
        private String description;
        @NotBlank
        private String type = "TASK";
        private String priority = "MEDIUM";
        private Long assigneeId;
        private LocalDate dueDate;
        private Integer storyPoints;
        private String severity;
        private String stepsToRepro;
        private List<String> labels;
    }

    @Data
    public static class UpdateRequest {
        @NotNull
        private Long id;
        private String title;
        private String description;
        private String type;
        private String priority;
        private Long assigneeId;
        private Long sprintId;
        private Long columnId;
        private LocalDate dueDate;
        private Integer storyPoints;
        private String severity;
        private String stepsToRepro;
        private String status;
    }

    @Data
    public static class MoveRequest {
        @NotNull
        private Long issueId;
        @NotNull
        private Long targetColumnId;
        @NotNull
        private Integer sortOrder;
    }

    @Data
    public static class IssueVO {
        private Long id;
        private String issueKey;
        private String title;
        private String description;
        private String type;
        private String priority;
        private String status;
        private Long columnId;
        private String columnName;
        private Long sprintId;
        private String sprintName;
        private Long assigneeId;
        private String assigneeName;
        private String assigneeAvatar;
        private Long reporterId;
        private String reporterName;
        private LocalDate dueDate;
        private Integer sortOrder;
        private Integer storyPoints;
        private String severity;
        private String stepsToRepro;
        private List<IssueLabelVO> labels;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class IssueLabelVO {
        private Long id;
        private String label;
        private String color;
    }

    @Data
    public static class QueryRequest {
        private Long projectId;
        private Long sprintId;
        private Long assigneeId;
        private String type;
        private String priority;
        private String status;
        private String keyword;
        private Integer page = 1;
        private Integer size = 20;
    }
}
