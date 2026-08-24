package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件发送日志（v2.13.6）
 * 记录每次邮件发送的结果，用于排查失败和统计送达率。
 */
@Data
@TableName("email_send_logs")
public class EmailSendLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String toEmail;
    private Long accountId;
    private String fromEmail;
    private String subject;
    private String notifyType;
    private Long relatedId;
    private Integer success = 0;
    private String error;
    private Integer durationMs;
    private LocalDateTime createdAt;
}
