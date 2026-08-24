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

    // v2.13.4：审计增强字段
    /** 操作 IP（IPv4/IPv6） */
    private String ipAddress;
    /** 浏览器 UA（截断到 500 字符） */
    private String userAgent;
    /** 字段变更 Diff（JSON 字符串，before/after 结构） */
    private String diffJson;
    /** 请求追踪 ID（串联同一请求的多条日志） */
    private String requestId;

    private LocalDateTime createdAt;
}
