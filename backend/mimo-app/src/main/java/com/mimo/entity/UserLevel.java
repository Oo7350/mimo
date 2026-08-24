package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_levels")
public class UserLevel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer level;        // 1-4
    private String levelName;     // L1/L2/L3/L4
    private String badgeColor;    // 铭牌颜色
    private String badgeIcon;     // 铭牌图标/样式
    private Long updatedBy;       // 最后修改人(admin)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
