package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.Issue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

    /**
     * 取项目下所有有计划起止日期的 issue（甘特图用）
     */
    @Select("""
        SELECT * FROM issues
        WHERE project_id = #{projectId}
          AND plan_start_date IS NOT NULL
          AND plan_end_date   IS NOT NULL
          AND deleted = 0
        ORDER BY plan_start_date ASC, id ASC
    """)
    List<Issue> selectGanttByProject(Long projectId);
}
