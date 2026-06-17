package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("calendar_events")
public class CalendarEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer allDay;       // 0 = no, 1 = yes
    private String eventType;     // TASK_DEADLINE / MEETING / REMINDER / SPRINT / CUSTOM
    private Long relatedId;       // issue_id or sprint_id
    private String relatedType;   // ISSUE / SPRINT
    private String color;
    private String location;
    private String participants;  // JSON string of user IDs
    private Integer reminderMinutes;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
