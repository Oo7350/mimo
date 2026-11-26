package com.mimo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 只投本进程（单实例 / Redis 不可用时的兜底）。
 */
@Slf4j
public class LocalRealtimeBridge implements RealtimeBridge {

    private final SimpMessagingTemplate messagingTemplate;

    public LocalRealtimeBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public String name() {
        return "LOCAL";
    }

    @Override
    public boolean shared() {
        return false;
    }

    @Override
    public void broadcast(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
