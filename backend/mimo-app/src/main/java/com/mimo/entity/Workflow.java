package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 可配置工作流 — 每个项目可为 STORY/TASK/BUG 定义独立状态机
 */
@Data
@TableName("workflows")
public class Workflow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String issueType;   // STORY / TASK / BUG
    private String name;
    private String config;      // JSON: nodes[] + transitions[]
    private Integer isActive;
    private Integer isDefault;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
