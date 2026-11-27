package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /** 查询用户在某 scope 下的全部角色 */
    @Select("""
        SELECT * FROM user_roles
        WHERE user_id = #{userId}
          AND (
                scope_type = 'GLOBAL'
             OR (scope_type = #{scopeType} AND scope_id = #{scopeId})
          )
        """)
    List<UserRole> findByUserAndScope(@Param("userId") Long userId,
                                     @Param("scopeType") String scopeType,
                                     @Param("scopeId") Long scopeId);

    /** 查询用户全部角色（任意 scope） */
    @Select("SELECT * FROM user_roles WHERE user_id = #{userId}")
    List<UserRole> findByUser(@Param("userId") Long userId);

    /**
     * 批量：这批用户里，哪些被分配了指定编码的角色（任意 scope）。
     *
     * <p>为什么要批量（v2.22.4）：`/workbench/team` 要逐个成员判定"是不是管理员"，
     * 单人判定要 6 条 SELECT，100 人就是 606 条、一次请求 433ms。批量版把所有判定压成
     * 固定条数与成员数无关的几条查询。
     */
    @Select("""
        <script>
        SELECT DISTINCT ur.user_id
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE r.code = #{code}
          AND ur.user_id IN
          <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
        </script>
        """)
    List<Long> findUserIdsByRoleCode(@Param("userIds") Collection<Long> userIds,
                                     @Param("code") String code);
}
