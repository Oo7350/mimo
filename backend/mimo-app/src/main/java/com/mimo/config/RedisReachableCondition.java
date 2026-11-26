package com.mimo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Redis 能不能连上（KNOWN-002 的开关）。
 *
 * 为什么要在启动时探一次而不是"用时再说"：
 * 多实例共享在线状态是**可选**能力，开发机没装 Redis 时不该因此起不来。
 * 探一次 300 毫秒，决定装配 Redis 版还是进程内版 —— 两套是互斥的，
 * 不存在"半套"状态，出问题也容易定位。
 *
 * `mimo.realtime.store` 三种取值：
 * - `auto`（默认）：探得到就用 Redis，探不到用进程内；
 * - `memory`：强制进程内（本地开发、跑 e2e 时最省事）；
 * - `redis`：强制用 Redis（连不上也照建，第一次调用失败后自动降级并打 WARN）。
 */
@Slf4j
public class RedisReachableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String mode = env.getProperty("mimo.realtime.store", "auto");
        if ("memory".equalsIgnoreCase(mode)) {
            log.info("实时能力：按要求固定使用进程内实现（mimo.realtime.store=memory）");
            return false;
        }
        String host = env.getProperty("mimo.realtime.redis.host", "localhost");
        int port = Integer.parseInt(env.getProperty("mimo.realtime.redis.port", "6379"));

        if ("redis".equalsIgnoreCase(mode)) {
            log.info("实时能力：按要求使用 Redis（mimo.realtime.store=redis，{}:{}）", host, port);
            return true;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            log.info("实时能力：探测到 Redis {}:{}，在线状态与实时广播跨实例共享", host, port);
            return true;
        } catch (IOException | RuntimeException e) {
            log.info("实时能力：Redis {}:{} 连不上（{}），改用进程内实现 —— 多实例部署时在线人数与实时消息只在本实例内准确",
                    host, port, e.getClass().getSimpleName());
            return false;
        }
    }
}
