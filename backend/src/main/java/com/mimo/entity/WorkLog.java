package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("work_logs")
public class WorkLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Long userId;
    private LocalDate workDate;
    private BigDecimal hours;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
