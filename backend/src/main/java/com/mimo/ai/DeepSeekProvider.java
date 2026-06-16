package com.mimo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 提供者实现
 * 兼容 OpenAI API 格式
 */
@Slf4j
@Component
public class DeepSeekProvider implements AiProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekProvider(
            @Value("${deepseek.api-key:}") String apiKey,
            @Value("${deepseek.base-url:https://api.deepseek.com/chat/completions}") String baseUrl,
            @Value("${ai.model:deepseek-chat}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String chat(String systemMessage, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return "AI 服务未配置，请设置环境变量 DEEPSEEK_API_KEY";
        }
        try {
            Map<String, Object> body = buildBody(systemMessage, userMessage, 0.7, 1024);
            ResponseEntity<JsonNode> res = doRequest(body);
            return extractContent(res);
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            return "AI 服务调用失败: " + e.getMessage();
        }
    }

    @Override
    public void chatStream(String systemMessage, String userMessage, StreamCallback callback) {
        if (apiKey == null || apiKey.isBlank()) {
            callback.onText("AI 服务未配置，请设置环境变量 DEEPSEEK_API_KEY", true);
            return;
        }
        try {
            Map<String, Object> body = buildBody(systemMessage, userMessage, 0.7, 4096);
            body.put("stream", true);

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("DeepSeek流式API返回错误: status={}, body={}", response.statusCode(), response.body());
                callback.onText("AI服务返回错误(" + response.statusCode() + "): " + response.body(), true);
                return;
            }

            // 解析SSE流: data: {...}\n\n
            String raw = response.body();
            for (String line : raw.split("\n")) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        callback.onText("", true);
                        return;
                    }
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode choices = chunk.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta").path("content");
                            if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                                if (!callback.onText(delta.asText(), false)) return;
                            }
                        }
                    } catch (Exception ignored) { /* 跳过解析失败的块 */ }
                }
            }
            callback.onText("", true);
        } catch (Exception e) {
            log.error("DeepSeek 流式调用失败", e);
            callback.onText("AI 流式服务错误: " + e.getMessage(), true);
        }
    }

    @Override
    public String getName() {
        return "DeepSeek";
    }

    // --- 内部方法 ---

    private Map<String, Object> buildBody(String systemMsg, String userMsg,
                                            double temperature, int maxTokens) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMsg),
                        Map.of("role", "user", "content", userMsg)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
        );
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private ResponseEntity<JsonNode> doRequest(Map<String, Object> body) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildHeaders());
        return restTemplate.exchange(baseUrl, HttpMethod.POST, entity, JsonNode.class);
    }

    private String extractContent(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body == null) return "";
        JsonNode choices = body.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) return "";
        JsonNode msg = choices.get(0).path("message").path("content");
        return msg.isMissingNode() ? "" : msg.asText("");
    }
}
