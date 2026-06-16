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

    public static AiChatResponse of(String content) {
        return AiChatResponse.builder().content(content).finished(true).build();
    }

    public static AiChatResponse error(String message) {
        return AiChatResponse.builder().content("AI 错误: " + message).finished(true).build();
    }
}
