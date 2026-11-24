package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 已经被逻辑删除的行也要看见。
     *
     * <p>起因（v2.19.5）：清除示例数据后再生成，接口 500 —— 因为 {@code allocateProjectKey()} 走的是
     * 普通 {@code selectList}，逻辑删除的项目不在结果里，于是又挑了 `DEMO2` 这个已软删的行占着的 key，
     * 撞上 `projects.uk_project_key`（唯一索引不看 deleted 标记）。
     *
     * <p>所以凡是"这个 key 还能不能用"的判断，都要问这个方法，而不是普通查询。
     */
    @Select("SELECT `key` FROM projects")
    List<String> selectAllKeysIncludingDeleted();

    /** 同上：按名称找项目，包含已被逻辑删除的行（用于"演示数据是否已清除"的复查） */
    @Select("SELECT * FROM projects WHERE name = #{name} ORDER BY id DESC LIMIT 1")
    Project selectByNameIncludingDeleted(@Param("name") String name);
}
