package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("delivery_daily_snapshots")
public class DeliveryDailySnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sprintId;
    private LocalDate snapshotDate;
    private String statusCategory;
    private Integer issueCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
