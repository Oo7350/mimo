package com.mimo.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String content;
    private String model;
    private Long promptTokens;
    private Long completionTokens;
    private Boolean finished;

    /**
     * true 表示 content 是错误说明（未配置 / 上游失败 / 超限），不是模型输出。
     * 调用方必须先看这个标记，绝不能把错误说明当结果写回业务数据。
     */
    /** 错误说明标记，见 {@link #hasError()} */
    private Boolean error;

    /** 注意：刻意不叫 isError，避免与 Lombok 生成的 getError() 在 Jackson 里撞同一个属性名 */
    public boolean hasError() {
        return Boolean.TRUE.equals(error);
    }

    public static AiChatResponse of(String content) {
        return AiChatResponse.builder().content(content).finished(true).error(false).build();
    }

    public static AiChatResponse error(String message) {
        return AiChatResponse.builder().content(message).finished(true).error(true).build();
    }
}
