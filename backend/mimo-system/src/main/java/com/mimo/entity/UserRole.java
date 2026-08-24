package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_roles")
public class UserRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer roleId;
    /** GLOBAL / TEAM / PROJECT */
    private String scopeType;
    /** team_id 或 project_id，GLOBAL 时为 null */
    private Long scopeId;
    private LocalDateTime createdAt;
}
