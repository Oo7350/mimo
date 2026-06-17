package com.mimo.ai;

/**
 * AI 模型提供者接口
 * 支持多模型切换: DeepSeek / OpenAI兼容 / 通义千问 / 本地Ollama
 */
public interface AiProvider {

    /**
     * 非流式对话
     */
    String chat(String systemMessage, String userMessage);

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
