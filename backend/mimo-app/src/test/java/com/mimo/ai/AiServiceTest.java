package com.mimo.ai;

import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 无 Key / 上游失败时的降级契约：
 * 1) 不能把错误说明当成模型输出返回（否则前端会把"服务未配置"写进任务描述）；
 * 2) 失败的调用不计入当日用量；
 * 3) 流式对话把说明作为最后一个 chunk 推给前端，而不是静默断开。
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiProvider aiProvider;

    private AiService aiService;

    private static AiChatRequest req() {
        return AiChatRequest.builder().systemMessage("sys").userMessage("hi").build();
    }

    @BeforeEach
    void setUp() {
        aiService = new AiService(aiProvider);
        ReflectionTestUtils.setField(aiService, "dailyLimitPerUser", 100);
    }

    @Test
    void chatWithQuotaFlagsErrorAndDoesNotConsumeQuotaWhenProviderUnavailable() {
        when(aiProvider.isConfigured()).thenReturn(false);

        AiChatResponse response = aiService.chatWithQuota(1L, req());

        assertThat(response.hasError()).isTrue();
        assertThat(response.getContent()).isEqualTo(AiService.MSG_NOT_CONFIGURED);
        // 失败的调用不扣配额
        assertThat(aiService.getRemainingQuota(1L)).isEqualTo(100);
    }

    @Test
    void chatWithQuotaFlagsErrorWhenProviderFailsMidCall() {
        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.chat(anyString(), anyString()))
                .thenThrow(new AiUnavailableException("AI 服务暂时不可用，请稍后重试"));

        AiChatResponse response = aiService.chatWithQuota(4L, req());

        assertThat(response.hasError()).isTrue();
        assertThat(response.getContent()).contains("不可用");
        assertThat(aiService.getRemainingQuota(4L)).isEqualTo(100);
    }

    @Test
    void chatWithQuotaConsumesQuotaOnlyOnSuccess() {
        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.chat(anyString(), anyString())).thenReturn("润色后的描述");

        AiChatResponse response = aiService.chatWithQuota(2L, req());

        assertThat(response.hasError()).isFalse();
        assertThat(response.getContent()).isEqualTo("润色后的描述");
        assertThat(aiService.getRemainingQuota(2L)).isEqualTo(99);
    }

    @Test
    void chatJsonThrowsFriendlyErrorAndKeepsRawTextOutOfMessage() {
        when(aiProvider.chat(anyString(), anyString()))
                .thenReturn("AI 服务未配置，请设置环境变量 DEEPSEEK_API_KEY");

        assertThatThrownBy(() -> aiService.chatJson(req(), Object.class))
                .isInstanceOf(AiUnavailableException.class)
                // 面向用户的消息不含上游原文
                .hasMessageNotContaining("DEEPSEEK_API_KEY")
                .hasMessageContaining("无法解析");
    }

    @Test
    void chatStreamWithQuotaPushesMessageWhenProviderUnavailable() {
        when(aiProvider.isConfigured()).thenReturn(false);

        List<String> chunks = new ArrayList<>();
        boolean ok = aiService.chatStreamWithQuota(3L, req(), (text, done) -> {
            chunks.add(text + "|" + done);
            return !done;
        });

        assertThat(ok).isFalse();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(AiService.MSG_NOT_CONFIGURED + "|true");
        assertThat(aiService.getRemainingQuota(3L)).isEqualTo(100);
    }

    @Test
    void isConfiguredDelegatesToProvider() {
        when(aiProvider.isConfigured()).thenReturn(false);
        assertThat(aiService.isConfigured()).isFalse();
    }

    @Test
    void deepSeekProviderReportsUnconfiguredAndRefusesToAnswer() {
        DeepSeekProvider provider = new DeepSeekProvider("", "https://example.invalid", "deepseek-chat");

        assertThat(provider.isConfigured()).isFalse();
        assertThatThrownBy(() -> provider.chat("sys", "hi"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage(DeepSeekProvider.MSG_NOT_CONFIGURED);
    }
}
