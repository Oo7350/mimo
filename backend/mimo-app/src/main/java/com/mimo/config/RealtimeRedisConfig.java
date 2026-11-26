package com.mimo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.service.PresenceStore;
import com.mimo.service.RealtimeBridge;
import com.mimo.service.RedisPresenceStore;
import com.mimo.service.RedisRealtimeBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

/**
 * Redis 版实时能力（KNOWN-002）：在线状态 + 跨实例广播。
 *
 * 只在探测到 Redis 时装配（见 {@link RedisReachableCondition}）。
 * 注意 `MimoApplication` 仍然 `exclude` 掉 `RedisAutoConfiguration`：
 * 这里的连接工厂是**自己建**的，跟 Spring Boot 的自动配置无关，
 * 免得"顺手把单设备登录也一起打开"——那会让同账号多端同时登录互相踢掉。
 */
@Slf4j
@Configuration
@Conditional(RedisReachableCondition.class)
public class RealtimeRedisConfig {

    @Value("${mimo.realtime.channel:mimo:bus:realtime}")
    private String channel;

    @Value("${mimo.realtime.presence-ttl-seconds:90}")
    private long presenceTtlSeconds;

    @Value("${mimo.realtime.redis.command-timeout-ms:500}")
    private long commandTimeoutMs;

    @Value("${mimo.realtime.instance-id:}")
    private String configuredInstanceId;

    @Bean(destroyMethod = "destroy")
    public LettuceConnectionFactory mimoRealtimeConnectionFactory(Environment env) {
        String host = env.getProperty("mimo.realtime.redis.host", "localhost");
        int port = Integer.parseInt(env.getProperty("mimo.realtime.redis.port", "6379"));
        String password = env.getProperty("mimo.realtime.redis.password", "");

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        if (!password.isEmpty()) {
            standalone.setPassword(password);
        }
        // 命令超时要短：Redis 卡住时宁可快速失败并降级，也不要拖着 HTTP 请求不放
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(commandTimeoutMs))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, clientConfig);
        log.info("实时能力：Redis 连接工厂 {}:{}（命令超时 {}ms）", host, port, commandTimeoutMs);
        return factory;
    }

    @Bean
    public StringRedisTemplate mimoRealtimeRedisTemplate(LettuceConnectionFactory mimoRealtimeConnectionFactory) {
        return new StringRedisTemplate(mimoRealtimeConnectionFactory);
    }

    @Bean
    public PresenceStore presenceStore(StringRedisTemplate mimoRealtimeRedisTemplate) {
        return new RedisPresenceStore(mimoRealtimeRedisTemplate, Duration.ofSeconds(presenceTtlSeconds));
    }

    @Bean
    public String realtimeInstanceId() {
        if (configuredInstanceId != null && !configuredInstanceId.isEmpty()) {
            return configuredInstanceId;
        }
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + "-" + ProcessHandle.current().pid() + "-"
                + UUID.randomUUID().toString().substring(0, 4);
    }

    @Bean
    public RealtimeBridge realtimeBridge(SimpMessagingTemplate messagingTemplate,
                                        StringRedisTemplate mimoRealtimeRedisTemplate,
                                        ObjectMapper objectMapper,
                                        String realtimeInstanceId) {
        log.info("实时能力：跨实例广播走 Redis 频道 {}（实例 {}）", channel, realtimeInstanceId);
        return new RedisRealtimeBridge(messagingTemplate, mimoRealtimeRedisTemplate,
                objectMapper, channel, realtimeInstanceId);
    }

    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(LettuceConnectionFactory mimoRealtimeConnectionFactory,
                                                                  RealtimeBridge realtimeBridge) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(mimoRealtimeConnectionFactory);
        MessageListener listener = (Message message, byte[] pattern) ->
                ((RedisRealtimeBridge) realtimeBridge).onMessage(new String(message.getBody()));
        container.addMessageListener(listener, new ChannelTopic(channel));
        return container;
    }
}
