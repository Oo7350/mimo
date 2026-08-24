package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SprintDTO {

    @Data
    public static class CreateRequest {
        @NotNull
        private Long projectId;
        @NotBlank
        private String name;
        private String goal;
        @NotNull
        private LocalDate startDate;
        @NotNull
        private LocalDate endDate;
    }

    @Data
    public static class SprintVO {
        private Long id;
        private String name;
        private String goal;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean isActive;
        private String status;
        private Integer totalIssues;
        private Integer completedIssues;
        private LocalDateTime createdAt;
    }

    @Data
    public static class BurndownPoint {
        private String date;
        private Double idealRemaining;
        private Double actualRemaining;
    }

    @Data
    public static class BurndownVO {
        private Long sprintId;
        private String sprintName;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer totalPoints;
        private List<BurndownPoint> points;
    }

    @Data
    public static class MemberStat {
        private String username;
        private Long assigneeId;
        private Integer totalAssigned;
        private Integer completed;
        private Integer overdue;
        private Double completionRate;
        private Double avgDaysInColumn;
    }

    @Data
    public static class SprintStatsVO {
        private Long sprintId;
        private String sprintName;
        private Integer totalIssues;
        private Integer completedIssues;
        private Double overallCompletionRate;
        private Double overallOverdueRate;
        private List<MemberStat> memberStats;
    }
}
