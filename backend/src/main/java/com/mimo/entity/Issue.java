package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("issues")
public class Issue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long columnId;
    private Long sprintId;
    private String issueKey;
    private String title;
    private String description;
    private String type;       // STORY / TASK / BUG
    private String priority;   // HIGHEST / HIGH / MEDIUM / LOW / LOWEST
    private String status;     // TODO / IN_PROGRESS / DONE (STORY/TASK); BUG uses bugStatus
    private Long assigneeId;
    private Long reporterId;
    private LocalDate dueDate;
    private Integer sortOrder;
    private Integer storyPoints;
    private String severity;
    private String stepsToRepro;
    // 甘特图字段 — v2.9.3
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private String dependencies;   // JSON 数组字符串, e.g. "[1,5,8]"
    // STORY 专属
    private String userRole;
    private String userGoal;
    private String businessValue;
    private String acceptanceCriteria;  // JSON string
    private String epic;
    private Long parentId;
    // BUG 专属
    private String bugStatus;   // NEW/CONFIRMED/IN_PROGRESS/RESOLVED/VERIFIED/CLOSED/REOPENED
    private String environment;
    private String expectedResult;
    private String actualResult;
    private String foundVersion;
    private String fixedVersion;
    // Timestamps
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
