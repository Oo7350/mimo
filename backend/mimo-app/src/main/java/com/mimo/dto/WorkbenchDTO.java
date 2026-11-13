package com.mimo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class WorkbenchDTO {

    @Data
    public static class WorkbenchVO {
        private List<WorkbenchTaskVO> tasks;
        private List<WorkbenchTaskVO> dueToday;
        private List<WorkbenchTaskVO> overdue;
        private List<PendingReviewVO> pendingChangeReviews;
        private List<PendingReviewVO> pendingTaskReviews;
        private List<RejectedItemVO> rejectedItems;
        private WeeklySummaryVO weeklySummary;
    }

    @Data
    public static class WorkbenchTaskVO {
        private Long id;
        private String issueKey;
        private String title;
        private String type;
        private String priority;
        private String status;
        private LocalDate dueDate;
        private Long projectId;
        private String projectName;
        private Long sprintId;
        private String sprintName;
        private String riskLevel;
        private List<String> riskReasons;
    }

    @Data
    public static class PendingReviewVO {
        private Long id;
        private String kind;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Long projectId;
        private String projectName;
        private Long submittedById;
        private String submittedByName;
        private LocalDateTime submittedAt;
        private String summary;
    }

    @Data
    public static class RejectedItemVO {
        private Long id;
        private String kind;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Long projectId;
        private String projectName;
        private String reason;
        private LocalDateTime reviewedAt;
    }

    @Data
    public static class WeeklySummaryVO {
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private BigDecimal workHours;
        private Integer completedTaskCount;
        private Integer activeSprintCount;
        private Integer activeSprintStoryPoints;
        private Integer activeSprintDoneStoryPoints;
        private BigDecimal activeSprintHours;
        private List<ActiveSprintEffortVO> activeSprints;
    }

    @Data
    public static class ActiveSprintEffortVO {
        private Long id;
        private String name;
        private Long projectId;
        private String projectName;
        private Integer assignedTaskCount;
        private Integer completedTaskCount;
        private Integer storyPoints;
        private Integer doneStoryPoints;
        private BigDecimal weekHours;
    }
}
