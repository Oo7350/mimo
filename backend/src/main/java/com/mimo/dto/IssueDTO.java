package com.mimo.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class IssueDTO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        // 甘特图 — v2.9.3
        private LocalDate planStartDate;
        private LocalDate planEndDate;
        private String dependencies;
        private List<String> labels;
        // STORY 专属
        private String userRole;
        private String userGoal;
        private String businessValue;
        private String epic;
        private Long parentId;
        // BUG 专属
        private String environment;
        private String expectedResult;
        private String actualResult;
        private String foundVersion;
        private String fixedVersion;
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
        // 甘特图 — v2.9.3
        private LocalDate planStartDate;
        private LocalDate planEndDate;
        private String dependencies;
        private String status;
        // STORY 专属
        private String userRole;
        private String userGoal;
        private String businessValue;
        private String epic;
        private Long parentId;
        // BUG 专属
        private String bugStatus;
        private String environment;
        private String expectedResult;
        private String actualResult;
        private String foundVersion;
        private String fixedVersion;
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
        // 甘特图 — v2.9.3
        private LocalDate planStartDate;
        private LocalDate planEndDate;
        private String dependencies;
        private List<IssueLabelVO> labels;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        // STORY 专属
        private String userRole;
        private String userGoal;
        private String businessValue;
        private List<AcceptanceCriterion> acceptanceCriteria;
        private String epic;
        private Long parentId;
        private String parentIssueKey;
        private String parentTitle;
        private List<IssueVO> subTasks;
        // BUG 专属
        private String bugStatus;
        private String environment;
        private String expectedResult;
        private String actualResult;
        private String foundVersion;
        private String fixedVersion;
        private Long projectId;
    }

    @Data
    public static class AcceptanceCriterion {
        private String id;
        private String text;
        private boolean done;
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
        private String bugStatus;
        private String epic;
        private Long parentId;
        private Integer page = 1;
        private Integer size = 20;
    }

    @Data
    public static class BugStatusRequest {
        @NotNull
        private Long issueId;
        @NotBlank
        private String bugStatus;
    }

    @Data
    public static class AcceptanceCriteriaRequest {
        @NotBlank
        private String text;
    }

    // ---- Serialization helpers ----

    public static List<AcceptanceCriterion> parseAcceptanceCriteria(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<List<AcceptanceCriterion>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    public static String toJson(List<AcceptanceCriterion> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
