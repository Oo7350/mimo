package com.mimo.service;

/**
 * 实时消息的出口（KNOWN-002）。
 *
 * 改造前 `WebSocketService` 直接调 `SimpMessagingTemplate.convertAndSend`，
 * 消息只会到达**本进程**里的订阅者：两台机器各连一半用户，
 * A 上的操作在 B 的浏览器里看不到，而且不会有任何报错——
 * 典型的"单机测试全过、一上多实例就少一半消息"。
 *
 * 两个实现：
 * - {@link LocalRealtimeBridge}：只投本进程（单实例，行为与改造前一致）。
 * - {@link RedisRealtimeBridge}：本进程投一次 + 发布到 Redis，各实例收到后再投给各自的连接。
 */
public interface RealtimeBridge {

    /** 通道类型：`LOCAL` / `REDIS` */
    String name();

    /** 这条广播能不能跨实例送达 */
    boolean shared();

    /**
     * 广播到某个 STOMP 目的地（如 `/topic/board/12`）。
     *
     * 语义：无论几个实例，每个浏览器**只收到一次**。Redis 实现靠消息里的
     * origin 标记丢掉自己发出去又回来的那份，否则本实例用户会收到两遍。
     */
    void broadcast(String destination, Object payload);
}
