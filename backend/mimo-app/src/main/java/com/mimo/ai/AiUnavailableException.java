package com.mimo.ai;

/**
 * AI 服务不可用：未配置 Key、网络不通、上游报错或返回内容无法解析。
 *
 * 约定：message 直接面向用户，不包含堆栈、上游响应体等内部实现细节，
 * 细节只写日志。上层（Controller / Service）据此走规则回退或返回 503，
 * 绝不能把这条消息当成模型输出去写回业务数据。
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
