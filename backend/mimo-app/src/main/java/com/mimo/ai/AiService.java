package com.mimo.ai;

import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 服务层 - 统一入口
 * 负责: Provider路由 / 用量统计 / 缓存 / 配置管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProvider aiProvider;

    @Value("${ai.daily-limit-per-user:100}")
    private int dailyLimitPerUser;

    /** 用户当日用量计数 (userId → count) */
    private final ConcurrentHashMap<Long, Integer> dailyUsage = new ConcurrentHashMap<>();

    /**
     * 普通对话
     */
    public AiChatResponse chat(AiChatRequest request) {
        String sys = request.getSystemMessage() != null ? request.getSystemMessage() : "你是一个有用的助手。";
        String result = aiProvider.chat(sys, request.getUserMessage());
        return AiChatResponse.of(result);
    }

    /**
     * 带用量检查的对话
     * @param userId 用户ID (用于限额控制)
     */
    public AiChatResponse chatWithQuota(Long userId, AiChatRequest request) {
        if (!checkQuota(userId)) {
            return AiChatResponse.error("今日 AI 使用次数已达上限 (" + dailyLimitPerUser + "次)");
        }
        AiChatResponse response = chat(request);
        recordUsage(userId);
        return response;
    }

    /**
     * 流式对话 - 返回SSE文本供Controller直接输出
     */
    public void chatStream(AiChatRequest request, AiProvider.StreamCallback callback) {
        String sys = request.getSystemMessage() != null ? request.getSystemMessage() : "你是一个有用的助手。";
        aiProvider.chatStream(sys, request.getUserMessage(), callback);
    }

    /**
     * 带用量检查的流式对话
     */
    public boolean chatStreamWithQuota(Long userId, AiChatRequest request, AiProvider.StreamCallback callback) {
        if (!checkQuota(userId)) {
            callback.onText("今日 AI 使用次数已达上限 (" + dailyLimitPerUser + "次)", true);
            return false;
        }
        recordUsage(userId);
        chatStream(request, callback);
        return true;
    }

    /**
     * 结构化JSON输出 (自动解析)
     */
    public <T> T chatJson(AiChatRequest request, Class<T> type) {
        String result = aiProvider.chat(
                request.getSystemMessage(),
                request.getUserMessage()
        );
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, type);
        } catch (Exception e) {
            log.warn("解析AI JSON响应失败: {}", e.getMessage());
            throw new RuntimeException("AI返回格式异常: " + result.substring(0, Math.min(result.length(), 200)));
        }
    }

    /**
     * 获取当前Provider名称
     */
    public String getProviderName() {
        return aiProvider.getName();
    }

    /**
     * 获取用户剩余用量
     */
    public int getRemainingQuota(Long userId) {
        int used = dailyUsage.getOrDefault(userId, 0);
        return Math.max(0, dailyLimitPerUser - used);
    }

    /**
     * 获取每日限额
     */
    public int getDailyLimitPerUser() {
        return dailyLimitPerUser;
    }

    // --- 内部方法 ---

    private boolean checkQuota(Long userId) {
        int used = dailyUsage.getOrDefault(userId, 0);
        return used < dailyLimitPerUser;
    }

    private void recordUsage(Long userId) {
        dailyUsage.merge(userId, 1, Integer::sum);
    }
}
