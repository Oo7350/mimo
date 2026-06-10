package com.mimo.service;

import com.mimo.dto.BoardSyncEvent;
import com.mimo.dto.ChatMessageDTO.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotification(Long userId, Object payload) {
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, payload);
        log.debug("WebSocket notification sent to user {}", userId);
    }

    public void sendBoardUpdate(Long projectId, BoardSyncEvent event) {
        messagingTemplate.convertAndSend("/topic/board/" + projectId, event);
        log.debug("WebSocket board update sent to project {}: {}", projectId, event.getType());
    }

    /**
     * 广播团队聊天消息
     */
    public void sendTeamChat(Long teamId, ChatEvent event) {
        messagingTemplate.convertAndSend("/topic/team-chat/" + teamId, event);
        log.debug("WebSocket chat message sent to team {}", teamId);
    }
}
