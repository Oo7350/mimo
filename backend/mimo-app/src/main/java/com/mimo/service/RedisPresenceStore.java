package com.mimo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Redis 在线表（KNOWN-002）：多实例共享同一个在线视图。
 *
 * 键的设计（一个会话一条，按用户聚合）：
 * <pre>
 *   mimo:presence:sess:{sessionId}  = userId           TTL 90 秒，靠心跳续期
 *   mimo:presence:user:{userId}     = SET(sessionId)   TTL 90 秒（续期时一起推）
 *   mimo:presence:online            = SET(userId)      索引，读取时顺手清理死键
 * </pre>
 *
 * 为什么用 TTL 而不是"断线就删"：进程被杀、网络闪断时根本收不到 DISCONNECT，
 * 只靠删除会让在线人数只涨不跌。TTL + 心跳保证"最长 90 秒内收敛"，
 * 代价是刚断线的一小段时间可能多算——这比永久多算一个人强。
 *
 * **降级**：Redis 一旦抛异常（连不上、超时），本类标记 degraded，
 * 之后所有读写转到进程内的 {@link InMemoryPresenceStore}，并且
 * {@link #shared()} 变成 false —— 界面据此显示"仅本实例"，
 * 而不是继续展示一个看起来是全站、其实只有本机的数字。
 */
@Slf4j
public class RedisPresenceStore implements PresenceStore {

    static final String SESS_PREFIX = "mimo:presence:sess:";
    static final String USER_PREFIX = "mimo:presence:user:";
    static final String ONLINE_KEY = "mimo:presence:online";

    private final StringRedisTemplate redis;
    private final Duration sessionTtl;

    /** Redis 不可用时的兜底：宁可少算，也不要假装全站 */
    private final InMemoryPresenceStore fallback = new InMemoryPresenceStore();
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public RedisPresenceStore(StringRedisTemplate redis, Duration sessionTtl) {
        this.redis = redis;
        this.sessionTtl = sessionTtl;
    }

    @Override
    public String name() {
        return degraded.get() ? "MEMORY" : "REDIS";
    }

    @Override
    public boolean shared() {
        return !degraded.get();
    }

    /** 排查用：Redis 路径是否已降级 */
    public boolean degraded() {
        return degraded.get();
    }

    @Override
    public void connect(String sessionId, Long userId) {
        if (sessionId == null || userId == null) return;
        String uid = String.valueOf(userId);
        boolean ok = runOrDegrade(() -> {
            redis.opsForValue().set(SESS_PREFIX + sessionId, uid, sessionTtl);
            redis.opsForSet().add(USER_PREFIX + uid, sessionId);
            redis.expire(USER_PREFIX + uid, sessionTtl);
            redis.opsForSet().add(ONLINE_KEY, uid);
        });
        if (ok) {
            log.debug("presence online(redis): userId={}, sessionId={}", userId, sessionId);
        } else {
            fallback.connect(sessionId, userId);
        }
    }

    @Override
    public void disconnect(String sessionId) {
        if (sessionId == null) return;
        boolean ok = runOrDegrade(() -> {
            String uid = redis.opsForValue().get(SESS_PREFIX + sessionId);
            redis.delete(SESS_PREFIX + sessionId);
            if (uid == null) {
                // 会话键已过期：没有用户 id 可清，留给用户集合的 TTL 自然收敛
                return;
            }
            redis.opsForSet().remove(USER_PREFIX + uid, sessionId);
            Long left = redis.opsForSet().size(USER_PREFIX + uid);
            if (left == null || left == 0L) {
                redis.delete(USER_PREFIX + uid);
                redis.opsForSet().remove(ONLINE_KEY, uid);
            }
        });
        if (!ok) fallback.disconnect(sessionId);
    }

    @Override
    public void touch(Map<String, Long> sessionToUser) {
        if (sessionToUser == null || sessionToUser.isEmpty()) return;
        boolean ok = runOrDegrade(() -> {
            for (Map.Entry<String, Long> entry : sessionToUser.entrySet()) {
                String sessionId = entry.getKey();
                String uid = String.valueOf(entry.getValue());
                String sessKey = SESS_PREFIX + sessionId;
                if (Boolean.TRUE.equals(redis.hasKey(sessKey))) {
                    redis.expire(sessKey, sessionTtl);
                } else {
                    // TTL 已过期但人其实还在：重新登记，别让人"凭空离线"
                    redis.opsForValue().set(sessKey, uid, sessionTtl);
                }
                redis.opsForSet().add(USER_PREFIX + uid, sessionId);
                redis.expire(USER_PREFIX + uid, sessionTtl);
                redis.opsForSet().add(ONLINE_KEY, uid);
            }
        });
        if (!ok) fallback.touch(sessionToUser);
    }

    @Override
    public Set<Long> onlineUserIds() {
        Set<Long> result = readOrDegrade(() -> {
            Set<String> candidates = redis.opsForSet().members(ONLINE_KEY);
            Set<Long> online = new HashSet<>();
            if (candidates == null) return online;
            for (String uid : candidates) {
                Long sessions = redis.opsForSet().size(USER_PREFIX + uid);
                if (sessions != null && sessions > 0) {
                    online.add(Long.valueOf(uid));
                } else {
                    // 用户集合已过期／为空：把索引里的死键摘掉，别让它越积越多
                    redis.opsForSet().remove(ONLINE_KEY, uid);
                }
            }
            return online;
        });
        return result != null ? result : fallback.onlineUserIds();
    }

    @Override
    public boolean isOnline(Long userId) {
        if (userId == null) return false;
        Boolean online = readOrDegrade(() -> {
            String uid = String.valueOf(userId);
            if (!Boolean.TRUE.equals(redis.opsForSet().isMember(ONLINE_KEY, uid))) {
                return Boolean.FALSE;
            }
            Long sessions = redis.opsForSet().size(USER_PREFIX + uid);
            if (sessions != null && sessions > 0) return Boolean.TRUE;
            redis.opsForSet().remove(ONLINE_KEY, uid);
            return Boolean.FALSE;
        });
        return online != null ? online : fallback.isOnline(userId);
    }

    @Override
    public int onlineUserCount() {
        return onlineUserIds().size();
    }

    // ---------------------------------------------------------------- 内部

    /** 执行一个写动作；Redis 挂了返回 false，由调用方转兜底 */
    private boolean runOrDegrade(Runnable action) {
        if (degraded.get()) return false;
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            degrade(e);
            return false;
        }
    }

    /** 执行一个读动作；Redis 挂了返回 null，由调用方转兜底 */
    private <T> T readOrDegrade(Supplier<T> action) {
        if (degraded.get()) return null;
        try {
            return action.get();
        } catch (RuntimeException e) {
            degrade(e);
            return null;
        }
    }

    private void degrade(RuntimeException e) {
        if (degraded.compareAndSet(false, true)) {
            log.warn("Redis 在线表不可用，已降级为进程内统计（多实例下在线人数将只算本实例）：{}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
