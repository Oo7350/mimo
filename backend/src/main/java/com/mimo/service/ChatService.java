package com.mimo.service;

import com.mimo.dto.ChatMessageDTO.*;
import com.mimo.entity.ChatMessage;
import com.mimo.entity.User;
import com.mimo.mapper.ChatMessageMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final WebSocketService webSocketService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 发送消息：保存到数据库 + WebSocket 广播
     */
    public ChatMessageVO send(SendRequest request, Long senderId) {
        User user = userMapper.selectById(senderId);
        String senderName = user != null ? user.getUsername() : "未知用户";

        ChatMessage msg = new ChatMessage();
        msg.setTeamId(request.getTeamId());
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setContent(request.getContent().trim());
        chatMessageMapper.insert(msg);

        ChatMessageVO vo = toVO(msg);

        // WebSocket 广播给团队成员
        ChatEvent event = new ChatEvent();
        event.setType("CHAT_MESSAGE");
        event.setTeamId(request.getTeamId());
        event.setMessage(vo);
        webSocketService.sendTeamChat(request.getTeamId(), event);

        log.debug("Chat message sent in team {} by {}", request.getTeamId(), senderName);
        return vo;
    }

    /**
     * 获取团队最近消息（最多 100 条）
     */
    public List<ChatMessageVO> getHistory(Long teamId) {
        return chatMessageMapper.findRecentByTeamId(teamId, 100).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    private ChatMessageVO toVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(msg.getId());
        vo.setTeamId(msg.getTeamId());
        vo.setSenderId(msg.getSenderId());
        vo.setSenderName(msg.getSenderName());
        vo.setSenderAvatar(msg.getSenderAvatar());
        vo.setContent(msg.getContent());
        vo.setCreatedAt(msg.getCreatedAt() != null ? msg.getCreatedAt().format(FMT) : "");
        return vo;
    }
}
