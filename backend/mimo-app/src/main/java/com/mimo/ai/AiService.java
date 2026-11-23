package com.mimo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 服务层 - 统一入口
 * 负责: Provider路由 / 用量统计 / 缓存 / 配置管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 未配置模型 Key 时给用户的说明（与具体 Provider 无关，但点明该配哪个环境变量） */
    public static final String MSG_NOT_CONFIGURED = "AI 服务未配置，请联系管理员配置模型密钥（DEEPSEEK_API_KEY）";

    private final AiProvider aiProvider;

    @Value("${ai.daily-limit-per-user:100}")
    private int dailyLimitPerUser;

    /** 用户当日用量计数 (userId → count) */
    private final ConcurrentHashMap<Long, Integer> dailyUsage = new ConcurrentHashMap<>();

    /**
     * 普通对话
     *
     * <p>每次调用都会把 **模型 / 提示词版本 / 温度 / maxTokens** 记进日志与响应体：
     * 同一句需求两次结果不同时，至少能回答"参数一不一样"。
     * 采样参数以前在这里被整段丢弃（Provider 内部硬编码 0.7/1024），
     * 现在原样下发，见 {@link AiProvider#chat(String, String, double, int)}。
     *
     * @throws AiUnavailableException 未配置或调用失败
     */
    public AiChatResponse chat(AiChatRequest request) {
        String sys = request.getSystemMessage() != null ? request.getSystemMessage() : "你是一个有用的助手。";
        double temperature = request.getTemperature() != null
                ? request.getTemperature() : AiProvider.DEFAULT_TEMPERATURE;
        int maxTokens = request.getMaxTokens() != null
                ? request.getMaxTokens() : AiProvider.DEFAULT_MAX_TOKENS;
        String model = aiProvider.getModel() != null ? aiProvider.getModel() : aiProvider.getName();
        // INLINE@v0 = 调用方没走 PromptRegistry（自己拼的 systemMessage），复盘时一眼能看出来
        String promptRef = request.getPromptRef() != null ? request.getPromptRef() : "INLINE@v0";

        log.info("[AI] model={} prompt={} temperature={} maxTokens={} userMessageLength={}",
                model, promptRef, temperature, maxTokens,
                request.getUserMessage() != null ? request.getUserMessage().length() : 0);

        String result = aiProvider.chat(sys, request.getUserMessage(), temperature, maxTokens);
        return AiChatResponse.builder()
                .content(result)
                .model(model)
                .temperature(temperature)
                .promptRef(promptRef)
                .finished(true)
                .error(false)
                .build();
    }

    /**
     * 带用量检查的对话
     * <p>
     * 只有真正拿到模型输出才计一次用量：未配置 / 调用失败返回带 error 标记的响应，不扣配额。
     *
     * @param userId 用户ID (用于限额控制)
     */
    public AiChatResponse chatWithQuota(Long userId, AiChatRequest request) {
        if (!aiProvider.isConfigured()) {
            return AiChatResponse.error(MSG_NOT_CONFIGURED);
        }
        if (!checkQuota(userId)) {
            return AiChatResponse.error("今日 AI 使用次数已达上限 (" + dailyLimitPerUser + "次)");
        }
        AiChatResponse response;
        try {
            response = chat(request);
        } catch (AiUnavailableException e) {
            log.warn("AI 不可用, userId={}: {}", userId, e.getMessage());
            return AiChatResponse.error(e.getMessage());
        }
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
        if (!aiProvider.isConfigured()) {
            callback.onText(MSG_NOT_CONFIGURED, true);
            return false;
        }
        if (!checkQuota(userId)) {
            callback.onText("今日 AI 使用次数已达上限 (" + dailyLimitPerUser + "次)", true);
            return false;
        }
        try {
            chatStream(request, callback);
        } catch (AiUnavailableException e) {
            log.warn("AI 流式对话不可用, userId={}: {}", userId, e.getMessage());
            callback.onText(e.getMessage(), true);
            return false;
        }
        recordUsage(userId);
        return true;
    }

    /**
     * 结构化JSON输出 (自动解析)
     *
     * @throws AiUnavailableException 未配置 / 调用失败 / 模型返回的不是合法 JSON
     */
    public <T> T chatJson(AiChatRequest request, Class<T> type) {
        // 走 chat() 而不是直接打 Provider：JSON 类调用同样要留下 模型/温度/提示词版本 的痕迹
        String result = chat(request).getContent();
        try {
            return JSON.readValue(result, type);
        } catch (Exception e) {
            // 原始内容只进日志，不拼进给用户看的消息里
            log.warn("解析AI JSON响应失败: {} | raw={}", e.getMessage(), abbreviate(result));
            throw new AiUnavailableException("AI 返回的内容无法解析，请重试", e);
        }
    }

    /**
     * 是否已配置可用的 Provider
     */
    public boolean isConfigured() {
        return aiProvider.isConfigured();
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

    private static String abbreviate(String text) {
        if (text == null) return "null";
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }

    private boolean checkQuota(Long userId) {
        int used = dailyUsage.getOrDefault(userId, 0);
        return used < dailyLimitPerUser;
    }

    private void recordUsage(Long userId) {
        dailyUsage.merge(userId, 1, Integer::sum);
    }
}
