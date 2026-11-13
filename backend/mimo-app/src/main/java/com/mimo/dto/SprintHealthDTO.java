package com.mimo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SprintHealthDTO {

    @Data
    public static class SprintHealthVO {
        private Long sprintId;
        private String sprintName;
        private Long projectId;
        private String projectName;
        private String sprintStatus;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate calculatedDate;
        private Integer totalDays;
        private Integer elapsedDays;
        private Integer remainingDays;
        private Double timeProgress;
        private Double workProgress;
        private Double progressGap;
        private Integer totalIssues;
        private Integer completedIssues;
        private Integer totalStoryPoints;
        private Integer completedStoryPoints;
        private Integer remainingStoryPoints;
        private String trackingBasis;
        private Integer overdueCount;
        private Integer blockedCount;
        private Integer pendingReviewCount;
        private Integer estimatedDelayDays;
        private Integer healthScore;
        private String healthLevel;
        private List<String> riskReasons;
        private List<String> suggestions;
        private LocalDateTime calculatedAt;
    }

    @Data
    public static class AiSummaryVO {
        private String summary;
        private Boolean aiAvailable;
        private String provider;
        private List<String> deterministicSuggestions;
    }
}
