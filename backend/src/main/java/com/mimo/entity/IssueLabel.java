package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("issue_labels")
public class IssueLabel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private String label;
    private String color;
}
