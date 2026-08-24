package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("board_columns")
public class BoardColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private Integer sortOrder;
    private String color;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
