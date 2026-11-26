package com.mimo.config;

import com.mimo.service.InMemoryPresenceStore;
import com.mimo.service.LocalRealtimeBridge;
import com.mimo.service.PresenceStore;
import com.mimo.service.RealtimeBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 进程内版实时能力：Redis 用不了时的默认装配。
 *
 * 行为和改造前完全一致（在线状态只看本进程、广播只到本进程），
 * 区别只在于**现在它会说自己是什么**：接口里的 `presenceStore` / `realtimeBridge`
 * 会返回 `MEMORY` / `LOCAL`，界面据此写"仅本实例"，不再是看不出来的局限。
 */
@Slf4j
@Configuration
@Conditional(RedisUnreachableCondition.class)
public class RealtimeLocalConfig {

    @Bean
    public PresenceStore presenceStore() {
        log.info("实时能力：在线状态使用进程内实现（shared=false）");
        return new InMemoryPresenceStore();
    }

    @Bean
    public RealtimeBridge realtimeBridge(SimpMessagingTemplate messagingTemplate) {
        log.info("实时能力：实时广播只投本实例（shared=false）");
        return new LocalRealtimeBridge(messagingTemplate);
    }
}
