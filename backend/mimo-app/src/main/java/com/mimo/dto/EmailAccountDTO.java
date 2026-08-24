package com.mimo.dto;

import lombok.Data;

public class EmailAccountDTO {

    @Data
    public static class BindRequest {
        private String emailAddress;
        private String imapHost;
        private Integer imapPort;
        private String imapUsername;
        private String imapPassword; // 明文，仅请求体用，服务端加密存储
        private Integer isDefault;
    }

    @Data
    public static class AccountVO {
        private Long id;
        private String emailAddress;
        private String imapHost;
        private Integer imapPort;
        private String imapUsername;
        private Integer isDefault;
        private String lastSyncedAt;
        private String createdAt;

        // v2.13.3：邮件通知偏好
        private String smtpHost;
        private Integer smtpPort;
        private String notifyTypes;
        private Integer notifyEnabled;
    }

    /** v2.13.3：更新通知偏好的请求体 */
    @Data
    public static class NotifySettingsRequest {
        private Integer enabled;       // 0 关闭，1 启用
        private String types;          // 逗号分隔，如 "assignment,mention,approval"
        private String smtpHost;       // 可选，覆盖默认推断
        private Integer smtpPort;       // 可选，默认 465
    }

    @Data
    public static class PresetVO {
        public String provider;
        public String imapHost;
        public Integer imapPort;
        public String hint;
    }
}
