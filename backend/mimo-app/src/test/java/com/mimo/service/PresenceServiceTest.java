package com.mimo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在线状态（KNOWN-013）。
 *
 * 这条曾经是"界面在编数字"的典型：团队页写「N 人在线」，取的是成员总数。
 * 现在数字真来自 WebSocket 会话表，这里把口径钉住：
 * 多标签页算一个人、断开就掉线、只看"这个团队的成员里谁在线"。
 */
class PresenceServiceTest {

    private final PresenceService presence = new PresenceService();

    @Test
    @DisplayName("连接后算在线，断开后算离线")
    void connectThenDisconnect() {
        presence.connect("s1", 100L);
        assertThat(presence.isOnline(100L)).isTrue();
        assertThat(presence.onlineUserCount()).isEqualTo(1);

        presence.disconnect("s1");
        assertThat(presence.isOnline(100L)).isFalse();
        assertThat(presence.onlineUserCount()).isZero();
    }

    @Test
    @DisplayName("同一个人开三个标签页，在线人数仍然只算一个")
    void multiTabCountsOnce() {
        presence.connect("s1", 100L);
        presence.connect("s2", 100L);
        presence.connect("s3", 100L);

        assertThat(presence.onlineUserCount()).isEqualTo(1);
        assertThat(presence.onlineUserIds()).containsExactly(100L);

        // 关掉一个标签页还在线，全关掉才离线
        presence.disconnect("s1");
        assertThat(presence.isOnline(100L)).isTrue();
        presence.disconnect("s2");
        presence.disconnect("s3");
        assertThat(presence.isOnline(100L)).isFalse();
    }

    @Test
    @DisplayName("只统计给定成员集合里的在线人数，外部的人不算")
    void countOnlineAmong() {
        presence.connect("s1", 100L);
        presence.connect("s2", 200L);
        presence.connect("s3", 999L); // 不是这个团队的成员

        List<Long> teamMembers = List.of(100L, 200L, 300L);
        assertThat(presence.countOnlineAmong(teamMembers)).isEqualTo(2);
        assertThat(presence.filterOnline(teamMembers)).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("空集合 / null 不会炸，也不会误报在线数")
    void emptyInputIsSafe() {
        presence.connect("s1", 100L);
        assertThat(presence.countOnlineAmong(List.of())).isZero();
        assertThat(presence.countOnlineAmong(null)).isZero();
        assertThat(presence.filterOnline(null)).isEmpty();
    }

    @Test
    @DisplayName("重复断开的同一个会话是幂等的")
    void disconnectIsIdempotent() {
        presence.connect("s1", 100L);
        presence.disconnect("s1");
        presence.disconnect("s1");
        presence.disconnect("never-existed");
        assertThat(presence.onlineUserCount()).isZero();
    }

    @Test
    @DisplayName("未连接过的人不会出现在在线列表里")
    void unknownUserNotOnline() {
        assertThat(presence.isOnline(42L)).isFalse();
        assertThat(presence.onlineUserIds()).isEqualTo(Set.of());
        // 拿没连过的人去问，也不该冒出假的在线状态
        assertThat(presence.filterOnline(List.of(42L))).isEmpty();
    }

    // ---------------------------------------------------------------- v2.21.0（KNOWN-002）

    @Test
    @DisplayName("默认装配是进程内存储，并且如实说自己不跨实例")
    void defaultStoreIsMemoryAndSaysSo() {
        assertThat(presence.storeName()).isEqualTo("MEMORY");
        assertThat(presence.sharedAcrossInstances()).isFalse();
    }

    @Test
    @DisplayName("在线读写都转到 store 上，并记住本实例持有哪些会话")
    void delegatesToStoreAndTracksLocalSessions() {
        RecordingStore store = new RecordingStore();
        PresenceService service = new PresenceService(store);

        service.connect("s1", 100L);
        service.connect("s2", 100L);
        service.disconnect("s1");
        service.touchAll();

        assertThat(store.connected).containsExactly("s1", "s2");
        assertThat(store.disconnected).containsExactly("s1");
        // 心跳只续期本实例还持有的会话：s1 已经断开，不该再出现在续期名单里
        assertThat(store.lastTouch).containsOnlyKeys("s2");
        assertThat(service.localSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有本地会话时心跳不打扰 store（空跑不打 Redis）")
    void touchWithoutSessionsDoesNothing() {
        RecordingStore store = new RecordingStore();
        PresenceService service = new PresenceService(store);

        service.touchAll();

        assertThat(store.touchCalls).isZero();
    }

    /** 只记录调用的假存储，用来验证 PresenceService 把活派给了 store */
    private static class RecordingStore implements PresenceStore {
        final List<String> connected = new ArrayList<>();
        final List<String> disconnected = new ArrayList<>();
        final Set<String> online = new HashSet<>();
        Map<String, Long> lastTouch = Map.of();
        int touchCalls;

        @Override
        public String name() {
            return "FAKE";
        }

        @Override
        public boolean shared() {
            return true;
        }

        @Override
        public void connect(String sessionId, Long userId) {
            connected.add(sessionId);
            online.add(String.valueOf(userId));
        }

        @Override
        public void disconnect(String sessionId) {
            disconnected.add(sessionId);
        }

        @Override
        public void touch(Map<String, Long> sessionToUser) {
            touchCalls++;
            lastTouch = sessionToUser;
        }

        @Override
        public Set<Long> onlineUserIds() {
            return Set.of();
        }

        @Override
        public boolean isOnline(Long userId) {
            return online.contains(String.valueOf(userId));
        }

        @Override
        public int onlineUserCount() {
            return online.size();
        }
    }
}
