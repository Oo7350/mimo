package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_logs")
public class ActivityLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private Long projectId;

    private String targetType;

    private Long targetId;

    private String action;

    private String detail;

    private LocalDateTime createdAt;
}
