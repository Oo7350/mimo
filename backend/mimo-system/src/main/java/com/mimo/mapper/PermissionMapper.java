package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 查询用户在某 scope 下拥有的全部权限（含 GLOBAL）。
     * 适用 scopeType = 'GLOBAL' 或 'TEAM'：
     *   - 'GLOBAL' 传 scopeId=null，仅查 GLOBAL 角色
     *   - 'TEAM'   传 scopeId=teamId，叠加 GLOBAL + TEAM=teamId
     */
    @Select("""
        SELECT DISTINCT p.*
        FROM permissions p
        JOIN role_permissions rp ON rp.permission_id = p.id
        JOIN user_roles ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = #{userId}
          AND (
                (ur.scope_type = 'GLOBAL')
             OR (ur.scope_type = 'TEAM'   AND ur.scope_id = #{scopeId})
             OR (ur.scope_type = 'PROJECT' AND ur.scope_id = #{scopeId})
          )
        """)
    List<Permission> findByUserAndScope(@Param("userId") Long userId,
                                        @Param("scopeId") Long scopeId);

    /**
     * 查询用户在某项目下拥有的全部权限（三层叠加）：
     *   - GLOBAL scope 角色权限（全局）
     *   - TEAM scope 角色权限（仅当 scope_id = 项目所属 team_id 时生效，即"仅本组项目生效"）
     *   - PROJECT scope 角色权限（仅当 scope_id = projectId 时生效）
     *
     * 通过 JOIN projects 表找到项目所属 team_id，避免用户被分配到其他组时也获得本组权限。
     */
    @Select("""
        SELECT DISTINCT p.*
        FROM permissions p
        JOIN role_permissions rp ON rp.permission_id = p.id
        JOIN user_roles ur ON ur.role_id = rp.role_id
        LEFT JOIN projects proj ON proj.id = #{projectId} AND proj.deleted = 0
        WHERE ur.user_id = #{userId}
          AND (
                ur.scope_type = 'GLOBAL'
             OR (ur.scope_type = 'TEAM'    AND proj.team_id IS NOT NULL AND ur.scope_id = proj.team_id)
             OR (ur.scope_type = 'PROJECT' AND ur.scope_id = #{projectId})
          )
        """)
    List<Permission> findByUserForProject(@Param("userId") Long userId,
                                          @Param("projectId") Long projectId);

    /** 查询某角色的权限 */
    @Select("SELECT p.* FROM permissions p " +
            "JOIN role_permissions rp ON rp.permission_id = p.id " +
            "WHERE rp.role_id = #{roleId} ORDER BY p.sort_order")
    List<Permission> findByRoleId(@Param("roleId") Integer roleId);
}
