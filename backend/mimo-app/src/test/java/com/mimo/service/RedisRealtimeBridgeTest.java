package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨实例广播（KNOWN-002）。
 *
 * 关键是两件事：
 * 1. 本实例先本地投一次，同时发布出去——本机用户不该为了"跨实例能力"多等一次往返；
 * 2. 自己发出去又从 Redis 转回来的那条**必须丢掉**，否则聊天消息会显示两遍。
 */
class RedisRealtimeBridgeTest {

    private SimpMessagingTemplate messaging;
    private StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RedisRealtimeBridge bridge;

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        redis = mock(StringRedisTemplate.class);
        bridge = new RedisRealtimeBridge(messaging, redis, objectMapper,
                "mimo:bus:realtime", "instance-A");
    }

    @Test
    @DisplayName("广播：本地投一次 + 往 Redis 发一条带 origin 的消息")
    void broadcastDeliversLocallyAndPublishes() {
        bridge.broadcast("/topic/team-chat/7", java.util.Map.of("content", "hi"));

        verify(messaging).convertAndSend(eq("/topic/team-chat/7"), any(Object.class));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("mimo:bus:realtime"), json.capture());
        assertThat(json.getValue())
                .contains("\"origin\":\"instance-A\"")
                .contains("\"destination\":\"/topic/team-chat/7\"")
                .contains("\"content\":\"hi\"");
        assertThat(bridge.shared()).isTrue();
        assertThat(bridge.name()).isEqualTo("REDIS");
    }

    @Test
    @DisplayName("收到自己发的消息要丢掉，否则本机用户会看到两遍")
    void ownMessageIsDropped() {
        bridge.onMessage("{\"origin\":\"instance-A\",\"destination\":\"/topic/team-chat/7\","
                + "\"payload\":{\"content\":\"hi\"}}");

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("收到别的实例的消息：按原目的地投给本实例的订阅者")
    void foreignMessageIsDelivered() {
        bridge.onMessage("{\"origin\":\"instance-B\",\"destination\":\"/topic/board/12\","
                + "\"payload\":{\"type\":\"CREATED\",\"issueKey\":\"MIMO-1\"}}");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/board/12"), payload.capture());
        assertThat(payload.getValue().toString()).contains("MIMO-1");
    }

    @Test
    @DisplayName("坏消息不炸：直接丢掉，不影响后面的消息")
    void malformedMessageIsIgnored() {
        bridge.onMessage("这不是 json");
        bridge.onMessage(null);
        bridge.onMessage("{\"no_destination\":true}");

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
        // 出错之后仍然能正常投递别的实例的消息
        bridge.onMessage("{\"origin\":\"instance-B\",\"destination\":\"/topic/x\",\"payload\":{\"a\":1}}");
        verify(messaging).convertAndSend(eq("/topic/x"), any(Object.class));
    }

    @Test
    @DisplayName("Redis 挂了：本地投递照旧成功，只是停止跨实例并降级为 LOCAL")
    void degradesToLocalWhenPublishFails() {
        // convertAndSend 返回 void，只能用 doThrow 打桩
        doThrow(new RedisConnectionFailureException("连接被拒绝"))
                .when(redis).convertAndSend(anyString(), anyString());

        bridge.broadcast("/topic/notifications/3", java.util.Map.of("title", "到期提醒"));

        verify(messaging).convertAndSend(eq("/topic/notifications/3"), any(Object.class));
        assertThat(bridge.shared()).isFalse();
        assertThat(bridge.name()).isEqualTo("LOCAL");
    }
}
