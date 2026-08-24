package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki 页面实体。Markdown 正文存 content，content_html 留作缓存位（默认 null 由前端渲染）。
 */
@Data
@TableName("wiki_pages")
public class WikiPage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long parentId;

    private String title;

    private String slug;

    private String content;

    private String contentHtml;

    private Long authorId;

    private Long editorId;

    private Integer version = 1;

    private Integer sortOrder = 0;

    private Integer isPinned = 0;

    private Integer viewCount = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 非持久字段：用于树形结构 + 列表展示 */
    @TableField(exist = false)
    private String authorName;

    @TableField(exist = false)
    private String editorName;

    @TableField(exist = false)
    private Integer childCount;
}
