package com.mimo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class RoleDTO {

    @Data
    public static class RoleVO {
        private Integer id;
        private String name;
        private String code;
        private Integer isSystem;
        private String description;
        private Integer sortOrder;
        private LocalDateTime createdAt;
        /** 关联的权限编码列表 */
        private List<String> permissions;
    }

    @Data
    public static class PermissionVO {
        private Integer id;
        private String code;
        private String name;
        private String module;
        private String description;
        private Integer sortOrder;
    }

    @Data
    public static class SaveRoleRequest {
        private Integer id;
        private String name;
        private String code;
        private String description;
        private Integer sortOrder;
        /** 权限编码列表 */
        private List<String> permissions;
    }

    @Data
    public static class AssignRoleRequest {
        private Long userId;
        private Integer roleId;
        /** 'GLOBAL' / 'TEAM' / 'PROJECT' */
        private String scopeType;
        private Long scopeId;
    }

    /**
     * 用户角色分配记录（包含 role + scope 信息）
     */
    @Data
    public static class UserRoleAssignmentVO {
        private Integer userRoleId;
        private Long userId;
        // Role 信息
        private Integer id;
        private String name;
        private String code;
        private Integer isSystem;
        private String description;
        // Scope 信息
        private String scopeType;
        private Long scopeId;
        private LocalDateTime assignedAt;
    }
}
