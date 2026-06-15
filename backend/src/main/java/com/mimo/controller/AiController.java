package com.mimo.controller;

import com.mimo.common.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@Slf4j
@RequiredArgsConstructor
public class AiController {

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com/chat/completions}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/polish")
    public Result<String> polish(@RequestBody Map<String, String> body, Authentication auth) {
        String input = body.get("text");
        if (input == null || input.isBlank()) {
            return Result.error(400, "文本不能为空");
        }
        String result = chat(
                "你是一个专业的项目管理助手，擅长将粗糙的草稿润色为正式、清晰、可执行的任务描述。使用中文输出，保持原意不添加新信息。",
                "请将以下任务描述润色为更专业、清晰的表达:\n\n" + input
        );
        return Result.success(result);
    }

    @PostMapping("/priority")
    public Result<Map<String, String>> analyzePriority(@RequestBody Map<String, String> body, Authentication auth) {
        String title = body.getOrDefault("title", "");
        String description = body.getOrDefault("description", "");
        String text = description != null && !description.isBlank()
                ? "标题: " + title + "\n描述: " + description
                : "标题: " + title;
        String result = chat(
                "你是一个敏捷项目管理专家，根据任务的紧急性和重要性评估优先级。只返回JSON格式，不要其他内容。",
                "根据以下任务信息，推荐最合适的优先级（HIGHEST/HIGH/MEDIUM/LOW/LOWEST），返回JSON: {\"priority\": \"HIGH\", \"reason\": \"简短理由(中文，不超过30字)\"}\n\n" + text
        );
        try {
            var match = result != null ? result.replaceAll("(?s).*?(\\{.*\\}).*", "$1") : null;
            if (match != null && match.startsWith("{")) {
                return Result.success(objectMapper.readValue(match, new TypeReference<Map<String, String>>() {}));
            }
        } catch (Exception e) { log.warn("解析AI优先级响应失败", e); }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("priority", "MEDIUM");
        fallback.put("reason", "无法解析AI响应，使用默认值");
        return Result.success(fallback);
    }

    private String chat(String systemMessage, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return "AI 服务未配置";
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", "deepseek-chat");
        req.put("messages", List.of(
                Map.of("role", "system", "content", systemMessage),
                Map.of("role", "user", "content", userMessage)
        ));
        req.put("temperature", 0.7);
        req.put("max_tokens", 1024);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, new HttpEntity<>(req, headers), Map.class);
            Map body = res.getBody();
            if (body != null) {
                List<Map> choices = (List<Map>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map msg = (Map) choices.get(0).get("message");
                    if (msg != null) return (String) msg.get("content");
                }
            }
        } catch (Exception e) {
            return "AI 服务调用失败: " + e.getMessage();
        }
        return "";
    }
}
