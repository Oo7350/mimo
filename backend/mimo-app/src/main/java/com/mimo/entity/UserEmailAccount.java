package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_email_accounts")
public class UserEmailAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String emailAddress;
    private String imapHost;
    private Integer imapPort;
    private String imapUsername;
    /** 加密后的 IMAP 密码（base64），数据库列 imap_password_enc */
    @TableField("imap_password_enc")
    private String imapPasswordEnc;

    // SMTP 发送配置（NULL 时按 imapHost 推断）
    private String smtpHost;
    private Integer smtpPort;

    // 邮件通知偏好（v2.13.3）
    /** 触发邮件通知的事件类型（逗号分隔）：assignment,mention,approval,comment,issue_status */
    private String notifyTypes;
    /** 是否启用邮件通知：0 关闭，1 启用 */
    @TableField("notify_enabled")
    private Integer notifyEnabled;

    private Integer isDefault;
    private LocalDateTime lastSyncedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
