package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
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

    /**
     * 批量版 {@link #findByUserAndScope}：这批用户里，谁在 scopeId 作用域下拥有该权限码。
     *
     * <p>作用域叠加规则与单条版本一致：GLOBAL 永远生效，TEAM/PROJECT 仅当 scope_id 命中。
     * 见 v2.22.4 的 N+1 修复说明（{@code AccessControlService#filterTeamAdmins}）。
     */
    @Select("""
        <script>
        SELECT DISTINCT ur.user_id
        FROM user_roles ur
        JOIN role_permissions rp ON rp.role_id = ur.role_id
        JOIN permissions p ON p.id = rp.permission_id
        WHERE p.code = #{code}
          AND ur.user_id IN
          <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
          AND (
                ur.scope_type = 'GLOBAL'
             OR (ur.scope_type = 'TEAM'    AND ur.scope_id = #{scopeId})
             OR (ur.scope_type = 'PROJECT' AND ur.scope_id = #{scopeId})
          )
        </script>
        """)
    List<Long> findUserIdsWithPermissionInScope(@Param("userIds") Collection<Long> userIds,
                                                @Param("code") String code,
                                                @Param("scopeId") Long scopeId);

    /** 批量版，只看 GLOBAL 作用域（系统管理员口径） */
    @Select("""
        <script>
        SELECT DISTINCT ur.user_id
        FROM user_roles ur
        JOIN role_permissions rp ON rp.role_id = ur.role_id
        JOIN permissions p ON p.id = rp.permission_id
        WHERE p.code = #{code}
          AND ur.user_id IN
          <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
          AND ur.scope_type = 'GLOBAL'
        </script>
        """)
    List<Long> findUserIdsWithGlobalPermission(@Param("userIds") Collection<Long> userIds,
                                               @Param("code") String code);
}
