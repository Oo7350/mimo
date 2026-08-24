package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_messages WHERE team_id = #{teamId} ORDER BY created_at ASC LIMIT #{limit}")
    List<ChatMessage> findRecentByTeamId(Long teamId, int limit);
}
