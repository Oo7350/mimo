package com.mimo.service;

import com.mimo.dto.BoardSyncEvent;
import com.mimo.dto.ChatMessageDTO.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 实时消息出口。
 *
 * KNOWN-002 之前这里直接调 `SimpMessagingTemplate`，消息只到本进程；
 * 现在统一走 {@link RealtimeBridge}：单实例时是原来的行为，
 * 配了 Redis 就自动变成跨实例广播（见 RealtimeRedisConfig）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final RealtimeBridge realtimeBridge;

    public void sendNotification(Long userId, Object payload) {
        realtimeBridge.broadcast("/topic/notifications/" + userId, payload);
        log.debug("WebSocket notification sent to user {}", userId);
    }

    public void sendBoardUpdate(Long projectId, BoardSyncEvent event) {
        realtimeBridge.broadcast("/topic/board/" + projectId, event);
        log.debug("WebSocket board update sent to project {}: {}", projectId, event.getType());
    }

    /**
     * 广播团队聊天消息
     */
    public void sendTeamChat(Long teamId, ChatEvent event) {
        realtimeBridge.broadcast("/topic/team-chat/" + teamId, event);
        log.debug("WebSocket chat message sent to team {}", teamId);
    }

    /** 当前广播通道类型（`LOCAL` / `REDIS`），给管理端看 */
    public String bridgeName() {
        return realtimeBridge.name();
    }

    /** 广播是否跨实例 */
    public boolean bridgeShared() {
        return realtimeBridge.shared();
    }
}
