package com.mimo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
}
