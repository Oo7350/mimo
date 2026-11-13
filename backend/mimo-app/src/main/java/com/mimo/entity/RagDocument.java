package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_documents")
public class RagDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String sourceType;
    private Long sourceId;
    private Long parentId;
    private String title;
    private Integer chunkIndex;
    private String content;
    private String sourceUrl;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime indexedAt;
}
