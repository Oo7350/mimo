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
}
