package com.mimo.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String systemMessage;
    private String userMessage;
    private Double temperature;
    private Integer maxTokens;

    /**
     * 提示词版本标识，形如 {@code ISSUE_DRAFT@v1}（见 {@code PromptRegistry}）。
     *
     * <p>它会随实际调用一起进日志和 {@link AiChatResponse}，用来回答"这条 AI 结果是哪版提示词产出的"。
     */
    private String promptRef;
}
