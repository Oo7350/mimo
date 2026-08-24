package com.mimo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserLevelVO {
    private Long userId;
    private String username;
    private Integer level;        // 1-4
    private String levelName;     // L1/L2/L3/L4
    private String badgeColor;    // 铭牌颜色
    private String badgeIcon;     // 铭牌图标/样式
    private LocalDateTime updatedAt;
}
