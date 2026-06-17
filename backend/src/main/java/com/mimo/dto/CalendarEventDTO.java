package com.mimo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class CalendarEventDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private Long projectId;
        private String title;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Boolean allDay;
        private String eventType;       // TASK_DEADLINE / MEETING / REMINDER / SPRINT / CUSTOM
        private Long relatedId;
        private String relatedType;     // ISSUE / SPRINT
        private String color;
        private String location;
        private List<Long> participants;
        private Integer reminderMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Long projectId;
        private String title;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Boolean allDay;
        private String eventType;
        private Long relatedId;
        private String relatedType;
        private String color;
        private String location;
        private List<Long> participants;
        private Integer reminderMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventVO {
        private Long id;
        private Long userId;
        private Long projectId;
        private String projectName;
        private String title;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Boolean allDay;
        private String eventType;
        private Long relatedId;
        private String relatedType;
        private String relatedTitle;     // 关联对象标题(任务名/Sprint名)
        private String color;
        private String location;
        private List<Long> participants;
        private List<String> participantNames;
        private Integer reminderMinutes;
        private LocalDateTime createdAt;
        private Boolean readonly;        // 是否为自动同步的截止日期事件(不可编辑)
    }
}
