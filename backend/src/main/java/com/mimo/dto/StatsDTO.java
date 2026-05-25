package com.mimo.dto;

import lombok.Data;

import java.util.List;

public class StatsDTO {

    @Data
    public static class StatsVO {
        private List<WeeklyItem> weeklyCompleted;
        private List<MemberItem> memberDistribution;
        private List<SprintVelocityItem> sprintVelocity;
    }

    @Data
    public static class WeeklyItem {
        private String date;
        private int count;
    }

    @Data
    public static class MemberItem {
        private String username;
        private int count;
    }

    @Data
    public static class SprintVelocityItem {
        private String sprintName;
        private int totalPoints;
        private int completedPoints;
    }
}
