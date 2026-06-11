package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private Boolean recalled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
