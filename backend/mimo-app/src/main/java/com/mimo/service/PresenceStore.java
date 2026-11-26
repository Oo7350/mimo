package com.mimo.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 在线状态的存放处（KNOWN-002）。
 *
 * 为什么要有这层抽象：原来的在线表是 `PresenceService` 里的两个 ConcurrentHashMap，
 * 天生只能描述"本进程里谁连着"。多实例部署时 A 实例看到的人在 B 实例上不算数，
 * 界面上那个"N 人在线"就只对一半机器成立——属于"数字看着有、含义不对"。
 *
 * 两个实现：
 * - {@link InMemoryPresenceStore}：单实例，零依赖，行为与改造前一致（默认）。
 * - {@link RedisPresenceStore}：写进 Redis 并按 TTL 续期，多实例共享。
 *
 * 注意 {@link #shared()}：它回答的是"这个数字跨实例算不算得准"。
 * 界面必须把它显示出来，否则又变成"看不出准不准的数字"。
 */
public interface PresenceStore {

    /** 存储类型，写进接口与界面：`MEMORY` / `REDIS` */
    String name();

    /** 是否跨实例共享（Redis 挂了降级之后会变回 false，见 RedisPresenceStore） */
    boolean shared();

    /** 会话建立：一个会话对应一个浏览器标签页 */
    void connect(String sessionId, Long userId);

    /** 会话断开（幂等：重复调用、未知会话都不该报错） */
    void disconnect(String sessionId);

    /**
     * 心跳续期。Redis 实现靠它把 TTL 往前推，
     * 否则一次长时间挂着不动就会被判过期——"人还在，却显示离线"。
     *
     * 传"会话 -> 用户"的映射而不是只传会话 id：TTL 万一已经过期，
     * Redis 实现可以据此把这条会话补回去（自愈），而不是永久少算一个人。
     *
     * @param sessionToUser 本实例当前持有的会话
     */
    void touch(Map<String, Long> sessionToUser);

    /** 当前在线的用户 id（按 userId 去重，多标签页算一个人） */
    Set<Long> onlineUserIds();

    boolean isOnline(Long userId);

    /** 在线的用户数（不是会话数） */
    int onlineUserCount();
}
