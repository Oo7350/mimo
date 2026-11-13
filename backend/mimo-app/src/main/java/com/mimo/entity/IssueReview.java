package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue_reviews")
public class IssueReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Integer roundNo;
    private Long submitterId;
    private String completionNote;
    private String evidenceUrls;
    private String status;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
