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
    private String status;     // TODO / IN_PROGRESS / DONE
    private Long assigneeId;
    private Long reporterId;
    private LocalDate dueDate;
    private Integer sortOrder;
    private Integer storyPoints;
    private String severity;
    private String stepsToRepro;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
