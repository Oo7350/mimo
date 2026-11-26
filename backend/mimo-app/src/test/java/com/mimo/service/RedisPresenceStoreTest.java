package com.mimo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 在线表（KNOWN-002）。
 *
 * 这里钉的是**键与 TTL 的契约**：键名写错一个字母，多实例就静默地各算各的（最难查的那种）。
 * 注意：这是用 Mock 校验"我发了什么命令"，不是真 Redis 集成测试；
 * 真机双实例的验证记录在 bug-log.md 里（v2.21.0 验证段）。
 */
class RedisPresenceStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private SetOperations<String, String> sets;
    private RedisPresenceStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        store = new RedisPresenceStore(redis, Duration.ofSeconds(90));
    }

    @Test
    @DisplayName("连接：写会话键带 TTL，并登记用户集合与在线索引")
    void connectWritesKeysWithTtl() {
        store.connect("s1", 100L);

        verify(values).set("mimo:presence:sess:s1", "100", Duration.ofSeconds(90));
        verify(sets).add("mimo:presence:user:100", "s1");
        verify(redis).expire("mimo:presence:user:100", Duration.ofSeconds(90));
        verify(sets).add("mimo:presence:online", "100");
        assertThat(store.shared()).isTrue();
        assertThat(store.name()).isEqualTo("REDIS");
    }

    @Test
    @DisplayName("读到用户集合为空时顺手清理索引，避免死键越积越多")
    void onlineUserIdsCleansStaleIndex() {
        when(sets.members("mimo:presence:online")).thenReturn(Set.of("100", "200"));
        when(sets.size("mimo:presence:user:100")).thenReturn(2L);
        when(sets.size("mimo:presence:user:200")).thenReturn(0L);

        assertThat(store.onlineUserIds()).containsExactly(100L);
        verify(sets).remove("mimo:presence:online", "200");
    }

    @Test
    @DisplayName("断开最后一个会话时，把用户集合与在线索引一起清掉")
    void disconnectLastSessionCleansEverything() {
        when(values.get("mimo:presence:sess:s1")).thenReturn("100");
        when(sets.size("mimo:presence:user:100")).thenReturn(0L);

        store.disconnect("s1");

        verify(redis).delete("mimo:presence:sess:s1");
        verify(sets).remove("mimo:presence:user:100", "s1");
        verify(redis).delete("mimo:presence:user:100");
        verify(sets).remove("mimo:presence:online", "100");
    }

    @Test
    @DisplayName("还有别的标签页时，用户仍然在线，索引不动")
    void disconnectKeepsUserWhenOtherSessionsRemain() {
        when(values.get("mimo:presence:sess:s1")).thenReturn("100");
        when(sets.size("mimo:presence:user:100")).thenReturn(1L);

        store.disconnect("s1");

        verify(sets).remove("mimo:presence:user:100", "s1");
        verify(redis, never()).delete("mimo:presence:user:100");
        verify(sets, never()).remove("mimo:presence:online", "100");
    }

    @Test
    @DisplayName("心跳：会话键还在就续期，TTL 已过期就重新登记（自愈）")
    void touchRefreshesAndResurrects() {
        when(redis.hasKey("mimo:presence:sess:alive")).thenReturn(true);
        when(redis.hasKey("mimo:presence:sess:expired")).thenReturn(false);

        store.touch(Map.of("alive", 100L, "expired", 200L));

        verify(redis).expire("mimo:presence:sess:alive", Duration.ofSeconds(90));
        verify(values).set("mimo:presence:sess:expired", "200", Duration.ofSeconds(90));
        verify(sets).add("mimo:presence:user:200", "expired");
    }

    @Test
    @DisplayName("Redis 出错就降级：shared 变 false、名字变 MEMORY，之后的写入进内存表")
    void degradesWhenRedisFails() {
        // set 返回 void，只能用 doThrow 打桩
        doThrow(new RedisConnectionFailureException("连接被拒绝"))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        store.connect("s1", 100L);

        assertThat(store.shared()).isFalse();
        assertThat(store.name()).isEqualTo("MEMORY");
        assertThat(store.degraded()).isTrue();
        // 降级后退回内存表：这个人对本实例来说仍然在线（不是"查不到就当作没人"）
        assertThat(store.isOnline(100L)).isTrue();

        // 再连一个：这次完全不碰 Redis，直接走内存
        store.connect("s2", 200L);
        verify(values, atLeastOnce()).set(anyString(), anyString(), any(Duration.class));
        assertThat(store.isOnline(200L)).isTrue();
        assertThat(store.onlineUserIds()).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("空会话 id / null 用户不会打出脏键")
    void nullInputsAreIgnored() {
        store.connect(null, 100L);
        store.connect("s1", null);
        store.disconnect(null);
        verify(values, never()).set(anyString(), anyString(), eq(Duration.ofSeconds(90)));
    }
}
