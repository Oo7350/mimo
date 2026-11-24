package com.mimo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线状态（谁真的连着 WebSocket）。
 *
 * 背景：KNOWN-013 说过——团队页原来写的"N 人在线"其实是成员总数，
 * 属于"界面在编数字"。这里用 STOMP 会话表算真实在线人数。
 *
 * 已知边界（写在代码里比写在文档里更难被忽略）：
 * - 计数只在**当前实例**内存里，多实例部署时各算各的（和 KNOWN-002 的
 *   "WebSocket 用内存代理"是同一个限制）。要跨实例请换 Redis。
 * - 一个用户开多个标签页只算一个人（按 userId 去重）。
 * - 断线依赖服务端收到 DISCONNECT / 会话超时；网络闪断时会有一段时间偏大。
 */
@Slf4j
@Service
public class PresenceService {

    /** sessionId -> userId */
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();

    /** userId -> 该用户的 sessionId 集合（一个浏览器多标签页 = 多个 session） */
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    public void connect(String sessionId, Long userId) {
        if (sessionId == null || userId == null) return;
        sessionToUser.put(sessionId, userId);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("presence online: userId={}, sessionId={}, 当前在线用户 {}",
                userId, sessionId, userSessions.size());
    }

    public void disconnect(String sessionId) {
        if (sessionId == null) return;
        Long userId = sessionToUser.remove(sessionId);
        if (userId == null) return;
        Set<String> sessions = userSessions.get(userId);
        if (sessions == null) return;
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
        log.debug("presence offline: userId={}, sessionId={}, 当前在线用户 {}",
                userId, sessionId, userSessions.size());
    }

    /** 当前所有在线用户 id（只读快照） */
    public Set<Long> onlineUserIds() {
        return Collections.unmodifiableSet(new HashSet<>(userSessions.keySet()));
    }

    public boolean isOnline(Long userId) {
        return userId != null && userSessions.containsKey(userId);
    }

    /** 在给定用户集合里，有几个人在线 */
    public long countOnlineAmong(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;
        return userIds.stream().filter(userSessions::containsKey).count();
    }

    /** 在给定用户集合里，谁在线 */
    public Set<Long> filterOnline(Collection<Long> userIds) {
        Set<Long> online = new HashSet<>();
        if (userIds == null) return online;
        for (Long id : userIds) {
            if (id != null && userSessions.containsKey(id)) online.add(id);
        }
        return online;
    }

    /** 当前实例的在线用户数（运维排查用） */
    public int onlineUserCount() {
        return userSessions.size();
    }
}
