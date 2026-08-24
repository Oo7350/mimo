package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki 附件元数据
 */
@Data
@TableName("wiki_attachments")
public class WikiAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pageId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String mimeType;

    private Long uploaderId;

    private LocalDateTime createdAt;
}
