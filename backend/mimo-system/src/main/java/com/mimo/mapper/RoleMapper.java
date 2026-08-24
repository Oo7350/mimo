package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT * FROM roles ORDER BY sort_order, id")
    List<Role> findAllOrdered();

    @Select("SELECT * FROM roles WHERE code = #{code}")
    Role findByCode(@Param("code") String code);
}
