package com.mimo.dto;

import lombok.Data;

import java.util.List;

public class StatsDTO {

    @Data
    public static class StatsVO {
        // 原有：依赖已完成任务的数据
        private List<WeeklyItem> weeklyCompleted;
        private List<MemberItem> memberDistribution;
        private List<SprintVelocityItem> sprintVelocity;

        // 新增：通用数据（不依赖已完成状态）
        private List<TypeDistItem> typeDistribution;      // 任务类型分布（STORY/BUG/TASK）
        private StatusOverview statusOverview;             // 状态概览（待办/进行中/已完成数量）
        private List<WeeklyActivityItem> weeklyActivity;   // 每日任务活动（创建+更新）
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

    /** 任务类型分布 */
    @Data
    public static class TypeDistItem {
        private String type;
        private String label;
        private int count;
    }

    /** 状态概览 */
    @Data
    public static class StatusOverview {
        private int todoCount;
        private int inProgressCount;
        private int doneCount;
        private int totalCount;
    }

    /** 每日活动 */
    @Data
    public static class WeeklyActivityItem {
        private String date;
        private int created;
        private int updated;
    }
}
