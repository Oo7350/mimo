package com.mimo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public class DeliveryAnalyticsDTO {

    @Data
    public static class VelocityVO {
        private Long projectId;
        private Double averageCompletedPoints;
        private Integer averageSprintCount;
        private List<VelocitySprintVO> sprints;
    }

    @Data
    public static class VelocitySprintVO {
        private Long sprintId;
        private String sprintName;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer committedPoints;
        private Integer completedPoints;
        private Double completionRate;
    }

    @Data
    public static class CfdVO {
        private Long projectId;
        private Long sprintId;
        private LocalDate fromDate;
        private LocalDate toDate;
        private List<CfdPoint> points;
    }

    @Data
    public static class CfdPoint {
        private LocalDate date;
        private Integer todoCount;
        private Integer inProgressCount;
        private Integer pendingReviewCount;
        private Integer doneCount;
        private Integer totalCount;
    }
}
