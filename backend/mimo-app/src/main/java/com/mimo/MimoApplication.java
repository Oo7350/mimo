package com.mimo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启动类。
 *
 * 为什么排除两个 Redis 自动配置：
 * - {@code RedisAutoConfiguration}：本项目不用 Spring Boot 那套 `spring.redis.*`，
 *   而是 RealtimeRedisConfig 按 `mimo.realtime.*` 自己建连接工厂（KNOWN-002）。
 *   它一旦生效还会顺带激活单设备登录那段逻辑（见下面的开关说明）。
 * - {@code RedisRepositoriesAutoConfiguration}：这里没有 Spring Data Redis 仓库
 *   （不玩 @RedisHash），但它只要有连接工厂就会去建 `redisReferenceResolver`，
 *   然后因为找不到名为 `redisTemplate` 的 bean 直接启动失败。
 *
 * 单设备登录（同账号多端互踢）：逻辑保留在 JwtAuthenticationFilter / UserService 里，
 * 由 `mimo.security.single-device-login` 控制，默认关——本地开发和 e2e 会用同一个账号
 * 开多个浏览器上下文，打开就会互相踢下线。
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@EnableAsync
public class MimoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MimoApplication.class, args);
    }
}
