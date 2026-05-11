package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("burndown_snapshots")
public class BurndownSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sprintId;
    private LocalDate snapshotDate;
    private Integer totalPoints;
    private Integer remainingPoints;
    private Integer completedPoints;
    private BigDecimal idealRemaining;
    private LocalDateTime createdAt;
}
