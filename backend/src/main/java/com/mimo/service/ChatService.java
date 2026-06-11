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

    /**
     * 撤回消息：仅发送者可撤回，2分钟内有效
     */
    public ChatMessageVO recallMessage(Long messageId, Long senderId) {
        ChatMessage msg = chatMessageMapper.selectById(messageId);
        if (msg == null) throw new com.mimo.common.BusinessException(com.mimo.common.ResultCode.NOT_FOUND, "消息不存在");
        if (!msg.getSenderId().equals(senderId)) {
            throw new com.mimo.common.BusinessException(com.mimo.common.ResultCode.FORBIDDEN, "只能撤回自己的消息");
        }
        // 2分钟内可撤回
        if (msg.getCreatedAt() != null && msg.getCreatedAt().plusMinutes(2).isBefore(java.time.LocalDateTime.now())) {
            throw new com.mimo.common.BusinessException(com.mimo.common.ResultCode.BAD_REQUEST, "超过2分钟，无法撤回");
        }
        msg.setRecalled(true);
        msg.setContent("[消息已撤回]");
        chatMessageMapper.updateById(msg);

        ChatMessageVO vo = toVO(msg);

        // WebSocket 广播撤回事件
        ChatEvent event = new ChatEvent();
        event.setType("CHAT_RECALL");
        event.setTeamId(msg.getTeamId());
        event.setMessage(vo);
        webSocketService.sendTeamChat(msg.getTeamId(), event);

        return vo;
    }

    private ChatMessageVO toVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(msg.getId());
        vo.setTeamId(msg.getTeamId());
        vo.setSenderId(msg.getSenderId());
        vo.setSenderName(msg.getSenderName());
        vo.setSenderAvatar(msg.getSenderAvatar());
        vo.setContent(msg.getContent());
        vo.setRecalled(msg.getRecalled());
        vo.setCreatedAt(msg.getCreatedAt() != null ? msg.getCreatedAt().format(FMT) : "");
        return vo;
    }
}
