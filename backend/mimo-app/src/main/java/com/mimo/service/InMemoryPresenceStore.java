package com.mimo.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内在线表（改造前的行为，逻辑原样搬过来）。
 *
 * 单实例部署够用，也是 Redis 不可用时的兜底：宁可少算（只算本实例），
 * 也不要算出一个"看着是全站、其实只有一部分"的数字而不说明。
 */
@Slf4j
public class InMemoryPresenceStore implements PresenceStore {

    /** sessionId -> userId */
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();

    /** userId -> 该用户的 sessionId 集合（一个浏览器多标签页 = 多个 session） */
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "MEMORY";
    }

    @Override
    public boolean shared() {
        return false;
    }

    @Override
    public void connect(String sessionId, Long userId) {
        if (sessionId == null || userId == null) return;
        sessionToUser.put(sessionId, userId);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("presence online: userId={}, sessionId={}, 当前在线用户 {}",
                userId, sessionId, userSessions.size());
    }

    @Override
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

    /** 内存表不会过期，不需要续期 */
    @Override
    public void touch(Map<String, Long> sessionToUser) {
        // 故意留空：说明"这里为什么不用做事"比留下一个空方法体更清楚
    }

    @Override
    public Set<Long> onlineUserIds() {
        return Collections.unmodifiableSet(new HashSet<>(userSessions.keySet()));
    }

    @Override
    public boolean isOnline(Long userId) {
        return userId != null && userSessions.containsKey(userId);
    }

    @Override
    public int onlineUserCount() {
        return userSessions.size();
    }
}
