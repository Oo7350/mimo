package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki 页面版本历史快照
 */
@Data
@TableName("wiki_versions")
public class WikiVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pageId;

    private Integer version;

    private String title;

    private String content;

    private Long editorId;

    private String changeSummary;

    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String editorName;
}
