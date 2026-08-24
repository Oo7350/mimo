package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("webhook_delivery_logs")
public class WebhookDeliveryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long webhookId;
    private String eventType;
    private String payload;
    private Integer statusCode;
    private String responseBody;
    private Integer success = 0;
    private Integer durationMs;
    private String error;
    private LocalDateTime createdAt;
}
