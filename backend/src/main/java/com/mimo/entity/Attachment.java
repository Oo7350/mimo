package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("attachments")
public class Attachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Long uploaderId;
    private LocalDateTime createdAt;
}
