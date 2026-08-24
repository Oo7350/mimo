package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("roles")
public class Role {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String code;
    private Integer isSystem;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    /** 非持久字段：关联的权限编码列表 */
    @TableField(exist = false)
    private List<String> permissions;
}
