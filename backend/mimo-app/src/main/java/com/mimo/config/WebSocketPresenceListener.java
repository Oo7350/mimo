package com.mimo.config;

import com.mimo.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 断线的兜底：客户端正常关闭走 STOMP DISCONNECT 帧，
 * 但网络闪断/进程被杀只会触发 SessionDisconnectEvent。
 * 两边都调到 PresenceService.disconnect（幂等），在线人数才不会只涨不跌。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final PresenceService presenceService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.disconnect(event.getSessionId());
        log.debug("WebSocket session disconnected: {}", event.getSessionId());
    }
}