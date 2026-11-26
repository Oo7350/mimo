package com.mimo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 发布订阅版广播（KNOWN-002）：一条消息，所有实例的浏览器都收得到。
 *
 * 做法很简单，也故意保持简单：
 * 1. 本实例先投一次（保证本机用户没有延迟、Redis 挂了也能收到）；
 * 2. 再把 `{origin, destination, payload}` 发到 Redis 频道；
 * 3. 每个实例（包括自己）都会收到这条消息，**origin 等于自己的直接丢掉**——
 *    否则本机用户会收到第二份，表现为"消息重复出现两次"。
 *
 * payload 用 Spring 的 ObjectMapper 序列化（就是 STOMP 转换器用的那个），
 * 日期格式、null 省略等口径才和本地投递一致；收到后按 JsonNode 原样投出去，
 * 客户端看到的 JSON 与单实例时相同。
 */
@Slf4j
public class RedisRealtimeBridge implements RealtimeBridge {

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String instanceId;

    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public RedisRealtimeBridge(SimpMessagingTemplate messagingTemplate,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               String channel,
                               String instanceId) {
        this.messagingTemplate = messagingTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.channel = channel;
        this.instanceId = instanceId;
    }

    @Override
    public String name() {
        return degraded.get() ? "LOCAL" : "REDIS";
    }

    @Override
    public boolean shared() {
        return !degraded.get();
    }

    public String instanceId() {
        return instanceId;
    }

    @Override
    public void broadcast(String destination, Object payload) {
        // 1. 本地先投：跨实例靠 Redis，但"自己这台"不该等一次网络往返
        messagingTemplate.convertAndSend(destination, payload);
        if (degraded.get()) return;

        // 2. 发布给其他实例
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("origin", instanceId);
            envelope.put("destination", destination);
            envelope.put("payload", payload);
            redis.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            if (degraded.compareAndSet(false, true)) {
                log.warn("Redis 广播不可用，实时消息将只在本实例内可见（其他实例的浏览器收不到）：{}: {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /** Redis 频道回调：把别的实例发出的消息投给本实例的订阅者 */
    public void onMessage(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode origin = root.get("origin");
            if (origin != null && instanceId.equals(origin.asText())) {
                return; // 自己发的：本地已经投过了
            }
            JsonNode destination = root.get("destination");
            if (destination == null) return;
            JsonNode payload = root.get("payload");
            messagingTemplate.convertAndSend(destination.asText(), payload);
            log.debug("跨实例广播已投递: {} (from {})", destination.asText(), origin);
        } catch (Exception e) {
            log.warn("跨实例广播解析失败，已丢弃：{}", e.getMessage());
        }
    }
}
