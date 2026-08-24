package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("webhook_configs")
public class WebhookConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long teamId;
    private String name;
    private String url;
    private String secret;
    /** 订阅事件，逗号分隔：ISSUE_CREATED,ISSUE_UPDATED,... */
    private String events;
    private Integer enabled = 1;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
