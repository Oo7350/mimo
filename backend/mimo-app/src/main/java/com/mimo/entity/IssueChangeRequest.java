package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue_change_requests")
public class IssueChangeRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Integer baseVersion;
    private String proposedChanges;
    private String changeReason;
    private String status;
    private Long applicantId;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
