package com.mimo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ChatMessageDTO {

    @Data
    public static class SendRequest {
        @NotNull(message = "团队ID不能为空")
        private Long teamId;

        @NotBlank(message = "消息内容不能为空")
        private String content;
    }

    @Data
    public static class ChatMessageVO {
        private Long id;
        private Long teamId;
        private Long senderId;
        private String senderName;
        private String senderAvatar;
        private String content;
        private Boolean recalled;
        private String createdAt;
        /** 是否为当前用户自己发的（前端用） */
        private boolean isMine;
    }

    /**
     * WebSocket 广播事件
     */
    @Data
    public static class ChatEvent {
        private String type; // "CHAT_MESSAGE"
        private Long teamId;
        private ChatMessageVO message;
    }
}
