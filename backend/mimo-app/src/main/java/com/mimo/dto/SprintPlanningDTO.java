package com.mimo.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SprintPlanningDTO {

    @Data
    public static class SuggestPlanRequest {
        @NotNull
        private Long projectId;
        private Long sprintId;
        private Integer capacityPoints;
    }

    @Data
    public static class ApplyPlanRequest {
        @NotEmpty
        private List<Long> issueIds;
    }

    @Data
    public static class CandidateIssueVO {
        private Long id;
        private String issueKey;
        private String title;
        private String type;
        private String priority;
        private String status;
        private Integer storyPoints;
        private LocalDate dueDate;
        private Boolean blocked;
        private List<String> dependencyIssueKeys;
    }

    @Data
    public static class RecommendedTaskVO {
        private Long id;
        private String issueKey;
        private String title;
        private String type;
        private String priority;
        private Integer storyPoints;
        private LocalDate dueDate;
        private Boolean blocked;
        private String reason;
    }

    @Data
    public static class SprintPlanVO {
        private Long projectId;
        private Long sprintId;
        private String sprintName;
        private Integer capacityPoints;
        private Double historicalVelocity;
        private Integer availablePoints;
        private Integer totalRecommendedPoints;
        private Boolean aiAvailable;
        private String summary;
        private List<String> risks;
        private List<CandidateIssueVO> candidates;
        private List<RecommendedTaskVO> recommendedTasks;
        private LocalDateTime generatedAt;
    }

    @Data
    public static class ApplyPlanVO {
        private Long sprintId;
        private Integer appliedCount;
        private Integer totalStoryPoints;
        private List<Long> appliedIssueIds;
    }
}
