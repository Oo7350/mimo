package com.mimo.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@link RedisReachableCondition} 的反面：Redis 用不了时装配进程内实现。
 *
 * 单独写一个"取反"的条件类，而不是在配置类上堆
 * `@ConditionalOnMissingBean`：后者取决于配置类的解析顺序，
 * 顺序一变就是"两套 bean 都在"或"一套都没有"，很难查。
 */
public class RedisUnreachableCondition implements Condition {

    private final RedisReachableCondition redis = new RedisReachableCondition();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !redis.matches(context, metadata);
    }
}
