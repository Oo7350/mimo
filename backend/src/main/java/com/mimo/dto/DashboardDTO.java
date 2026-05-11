package com.mimo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

public class DashboardDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardVO {
        private int totalIssues;
        private int inProgressIssues;
        private int doneIssues;
        private List<SprintInfo> activeSprints;
        private List<ProjectInfo> myProjects;
        private List<ActivityInfo> recentActivities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SprintInfo {
        private Long id;
        private String name;
        private Long projectId;
        private String projectName;
        private String startDate;
        private String endDate;
        private int totalIssues;
        private int completedIssues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectInfo {
        private Long id;
        private String name;
        private String key;
        private String template;
        private String teamName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityInfo {
        private Long id;
        private String username;
        private String targetType;
        private String action;
        private String detail;
        private String createdAt;
    }
}
