package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
