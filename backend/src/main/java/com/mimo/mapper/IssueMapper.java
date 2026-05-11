package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.Issue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {
}
