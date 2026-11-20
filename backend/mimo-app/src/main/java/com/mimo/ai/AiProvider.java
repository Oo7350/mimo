package com.mimo.ai;

/**
 * AI 模型提供者接口
 * 支持多模型切换: DeepSeek / OpenAI兼容 / 通义千问 / 本地Ollama
 */
public interface AiProvider {

    /**
     * 非流式对话
     *
     * @throws AiUnavailableException 未配置 / 网络失败 / 上游报错时抛出，
     *                                message 可直接展示给用户
     */
    String chat(String systemMessage, String userMessage);

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
