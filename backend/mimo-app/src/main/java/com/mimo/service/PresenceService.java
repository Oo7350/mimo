package com.mimo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 在线状态（谁真的连着 WebSocket）。
 *
 * 背景：KNOWN-013 说过——团队页原来写的"N 人在线"其实是成员总数，
 * 属于"界面在编数字"。这里用 STOMP 会话算真实在线人数。
 *
 * KNOWN-002 之后这层只做三件事：
 * 1. 记住**本实例**持有哪些会话（心跳续期与降级要用）；
 * 2. 把读写转给 {@link PresenceStore}（内存 / Redis 两种实现）；
 * 3. 定期心跳续期——Redis 实现靠 TTL 判活，不续期会"人在却显示离线"。
 *
 * 口径仍然成立：一个用户开多个标签页只算一个人（按 userId 去重）；
 * 断线依赖 DISCONNECT / 会话超时 / TTL，网络闪断时会有一小段时间偏大。
 */
@Slf4j
@Service
public class PresenceService {

    private final PresenceStore store;

    /** 本实例持有的会话（sessionId -> userId），心跳与降级都靠它 */
    private final Map<String, Long> localSessions = new ConcurrentHashMap<>();

    private ScheduledExecutorService heartbeat;

    @Value("${mimo.realtime.presence-heartbeat-seconds:30}")
    private long heartbeatSeconds = 30;

    public PresenceService() {
        this(new InMemoryPresenceStore());
    }

    @Autowired
    public PresenceService(PresenceStore store) {
        this.store = store;
    }

    @PostConstruct
    void startHeartbeat() {
        // 只有 Redis 表需要续期；内存表是"连着就在表里"，不存在过期
        if (!store.shared() || heartbeatSeconds <= 0) return;
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presence-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleWithFixedDelay(this::touchAll,
                heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
        log.info("在线状态心跳已启动：每 {} 秒续期一次本实例会话（存储={}）", heartbeatSeconds, store.name());
    }

    @PreDestroy
    void stopHeartbeat() {
        if (heartbeat != null) heartbeat.shutdownNow();
    }

    public void connect(String sessionId, Long userId) {
        if (sessionId == null || userId == null) return;
        localSessions.put(sessionId, userId);
        store.connect(sessionId, userId);
    }

    public void disconnect(String sessionId) {
        if (sessionId == null) return;
        localSessions.remove(sessionId);
        store.disconnect(sessionId);
    }

    /** 心跳一次（定时调用；单测里直接调） */
    public void touchAll() {
        if (localSessions.isEmpty()) return;
        try {
            store.touch(new HashMap<>(localSessions));
        } catch (RuntimeException e) {
            log.warn("在线状态心跳失败（不影响业务）：{}", e.getMessage());
        }
    }

    /** 当前所有在线用户 id（只读快照） */
    public Set<Long> onlineUserIds() {
        return Collections.unmodifiableSet(new HashSet<>(store.onlineUserIds()));
    }

    public boolean isOnline(Long userId) {
        return store.isOnline(userId);
    }

    /** 在给定用户集合里，有几个人在线 */
    public long countOnlineAmong(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;
        return userIds.stream().filter(store::isOnline).count();
    }

    /** 在给定用户集合里，谁在线 */
    public Set<Long> filterOnline(Collection<Long> userIds) {
        Set<Long> online = new HashSet<>();
        if (userIds == null) return online;
        for (Long id : userIds) {
            if (id != null && store.isOnline(id)) online.add(id);
        }
        return online;
    }

    /** 当前在线用户数（不是会话数） */
    public int onlineUserCount() {
        return store.onlineUserCount();
    }

    /** 存储类型：`MEMORY` / `REDIS`（界面上要显示出来，见 KNOWN-002） */
    public String storeName() {
        return store.name();
    }

    /** 这个在线数字跨实例算不算得准 */
    public boolean sharedAcrossInstances() {
        return store.shared();
    }

    /** 本实例持有的会话数（排查用） */
    public int localSessionCount() {
        return localSessions.size();
    }
}
