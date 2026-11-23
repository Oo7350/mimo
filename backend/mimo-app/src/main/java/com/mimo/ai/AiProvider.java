package com.mimo.ai;

/**
 * AI 模型提供者接口
 * 支持多模型切换: DeepSeek / OpenAI兼容 / 通义千问 / 本地Ollama
 */
public interface AiProvider {

    /** 调用方没指定温度时用的默认值（保持改造前的行为） */
    double DEFAULT_TEMPERATURE = 0.7;
    /** 调用方没指定 maxTokens 时用的默认值（保持改造前的行为） */
    int DEFAULT_MAX_TOKENS = 1024;

    /**
     * 非流式对话（带采样参数）
     *
     * <p>温度/maxTokens 必须真的下发：改造前 {@code AiChatRequest.temperature} 一路被丢掉，
     * Controller 里写的 {@code .temperature(0.3)}「低温度保证结构化输出稳定」是个摆设，
     * 实际每次都按 0.7 采样 —— 这正是"AI 输出不可复现"的另一半原因。
     *
     * @param temperature 采样温度
     * @param maxTokens   输出上限
     * @throws AiUnavailableException 未配置 / 网络失败 / 上游报错时抛出，
     *                                message 可直接展示给用户
     */
    String chat(String systemMessage, String userMessage, double temperature, int maxTokens);

    /**
     * 非流式对话（用默认采样参数）
     */
    default String chat(String systemMessage, String userMessage) {
        return chat(systemMessage, userMessage, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
    }

    /**
     * 实际使用的模型名，写进响应与日志用于追溯
     */
    default String getModel() {
        return getName();
    }

    /**
     * 是否已配置可用（未配置时前端应给出提示，而不是让用户点一次失败一次）
     */
    default boolean isConfigured() {
        return true;
    }

    /**
     * 流式对话 (SSE)
     * 回调函数接收每个文本块, 返回false可中断流
     */
    void chatStream(String systemMessage, String userMessage, StreamCallback callback);

    /**
     * 提供者名称
     */
    String getName();

    /**
     * 流式回调接口
     */
    interface StreamCallback {
        /**
         * @param textChunk 当前文本片段
         * @param done 是否为最后一个片段
         * @return 继续接收返回true, 中断返回false
         */
        boolean onText(String textChunk, boolean done);
    }
}
