package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue_versions")
public class IssueVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Integer versionNo;
    private String snapshotJson;
    private String changeSummary;
    private String source;
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
