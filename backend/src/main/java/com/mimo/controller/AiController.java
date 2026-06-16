package com.mimo.controller;

import com.mimo.ai.AiProvider;
import com.mimo.ai.AiService;
import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import com.mimo.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/ai")
@Slf4j
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // ==================== 现有接口 (兼容) ====================

    /**
     * 润色任务描述
     */
    @PostMapping("/polish")
    public Result<String> polish(@RequestBody Map<String, String> body, Authentication auth) {
        String input = body.get("text");
        if (input == null || input.isBlank()) {
            return Result.error(400, "文本不能为空");
        }
        Long userId = getLongPrincipal(auth);
        AiChatRequest request = AiChatRequest.builder()
                .systemMessage("你是一个专业的项目管理助手，擅长将粗糙的草稿润色为正式、清晰、可执行的任务描述。使用中文输出，保持原意不添加新信息。")
                .userMessage("请将以下任务描述润色为更专业、清晰的表达:\n\n" + input)
                .build();
        AiChatResponse response = aiService.chatWithQuota(userId, request);
        return Result.success(response.getContent());
    }

    /**
     * AI 推荐优先级
     */
    @PostMapping("/priority")
    public Result<Map<String, String>> analyzePriority(@RequestBody Map<String, String> body, Authentication auth) {
        String title = body.getOrDefault("title", "");
        String description = body.getOrDefault("description", "");
        String text = description != null && !description.isBlank()
                ? "标题: " + title + "\n描述: " + description
                : "标题: " + title;
        Long userId = getLongPrincipal(auth);
        AiChatRequest request = AiChatRequest.builder()
                .systemMessage("你是一个敏捷项目管理专家，根据任务的紧急性和重要性评估优先级。只返回JSON格式，不要其他内容。")
                .userMessage("根据以下任务信息，推荐最合适的优先级（HIGHEST/HIGH/MEDIUM/LOW/LOWEST），返回JSON: {\"priority\": \"HIGH\", \"reason\": \"简短理由(中文，不超过30字)\"}\n\n" + text)
                .build();
        try {
            Map<String, String> result = aiService.chatJson(request, Map.class);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("解析AI优先级响应失败", e);
            Map<String, String> fallback = Map.of("priority", "MEDIUM", "reason", "无法解析AI响应，使用默认值");
            return Result.success(fallback);
        }
    }

    // ==================== 新增AI能力接口 ====================

    /**
     * 智能任务创建 - 自然语言解析为结构化任务
     */
    @PostMapping("/parse-issue")
    public Result<Object> parseIssue(@RequestBody Map<String, Object> body, Authentication auth) {
        String input = (String) body.get("input");
        if (input == null || input.isBlank()) {
            return Result.error(400, "输入内容不能为空");
        }
        Long userId = getLongPrincipal(auth);
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());

        String systemPrompt = buildParseIssueSystemPrompt(context);
        AiChatRequest request = AiChatRequest.builder()
                .systemMessage(systemPrompt)
                .userMessage(input)
                .temperature(0.3)  // 低温度保证结构化输出稳定
                .maxTokens(2048)
                .build();
        try {
            Object result = aiService.chatJson(request, Object.class);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("AI解析任务失败", e);
            return Result.error(500, "AI 解析失败: " + e.getMessage());
        }
    }

    /**
     * Story 拆分 - 将Story拆分为子任务建议
     */
    @PostMapping("/suggest-tasks")
    public Result<Object> suggestTasks(@RequestBody Map<String, Object> body, Authentication auth) {
        String storyTitle = (String) body.getOrDefault("title", "");
        String storyDesc = (String) body.getOrDefault("description", "");
        String acceptanceCriteria = (String) body.getOrDefault("acceptanceCriteria", "");

        Long userId = getLongPrincipal(auth);
        String userMessage = String.format(
                "请将以下用户故事拆分为具体的开发子任务:\n\n" +
                "**标题**: %s\n**描述**: %s\n**验收标准**: %s\n\n" +
                "要求:\n" +
                "- 每个子任务应可独立完成和测试\n" +
                "- 包含预估工时(storyPoints 1-8)\n" +
                "- 标注依赖关系\n" +
                "- 返回JSON数组",
                storyTitle,
                storyDesc != null ? storyDesc : "无",
                acceptanceCriteria != null ? acceptanceCriteria : "无"
        );

        AiChatRequest request = AiChatRequest.builder()
                .systemMessage("你是一个资深Scrum Master，擅长将大型用户故事拆分为可执行的子任务。输出结构化JSON。")
                .userMessage(userMessage)
                .temperature(0.4)
                .maxTokens(2048)
                .build();
        try {
            Object result = aiService.chatJson(request, Object.class);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("AI拆分任务失败", e);
            return Result.error(500, "AI 拆分失败: " + e.getMessage());
        }
    }

    /**
     * AI 故事点估算
     */
    @PostMapping("/estimate-story-points")
    public Result<Map<String, Object>> estimateStoryPoints(@RequestBody Map<String, Object> body, Authentication auth) {
        String title = (String) body.getOrDefault("title", "");
        String description = (String) body.getOrDefault("description", "");
        String type = (String) body.getOrDefault("type", "TASK");

        Long userId = getLongPrincipal(auth);
        String prompt = String.format(
                "请估算以下任务的Fibonacci故事点数(1,2,3,5,8,13,21):\n\n" +
                "**类型**: %s | **标题**: %s\n**描述**: %s\n\n" +
                "返回JSON: {\"points\": 3, \"confidence\": 0.85, \"reason\": \"估算理由\"}",
                type, title, description != null ? description : "无"
        );

        AiChatRequest request = AiChatRequest.builder()
                .systemMessage("你是一个敏捷开发估算专家，基于Fibonacci序列进行故事点估算。考虑复杂度、工作量、不确定性因素。")
                .userMessage(prompt)
                .temperature(0.2)
                .maxTokens(512)
                .build();
        try {
            Map<String, Object> result = aiService.chatJson(request, Map.class);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("AI估算故事点失败", e);
            return Result.success(Map.of("points", 3, "confidence", 0.5, "reason", "默认值"));
        }
    }

    // ==================== 流式对话接口 ====================

    /**
     * 流式对话 - SSE (Server-Sent Events)
     * 前端可通过 EventSource 或 fetch+ReadableStream 接收
     */
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(
            @RequestParam String message,
            @RequestParam(defaultValue = "你是一个项目管理AI助手") String systemPrompt,
            Authentication auth,
            HttpServletResponse response) {

        // 手动设置SSE响应头，避免produces声明与异常处理器冲突
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时

        // 提取userId，失败时通过SSE返回错误
        final Long userId;
        try {
            userId = getLongPrincipal(auth);
        } catch (Exception e) {
            log.warn("chatStream 认证失败: {}", e.getMessage());
            try {
                emitter.send(SseEmitter.event().data("[错误] 未登录或登录状态异常，请重新登录"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 防止SSE异常冒泡为500
        emitter.onError(ex -> log.warn("SSE连接错误, userId={}: {}", userId, ex.getMessage()));
        emitter.onTimeout(() -> {
            log.warn("SSE超时, userId={}", userId);
            try { emitter.complete(); } catch (Exception ignored) {}
        });

        AiProvider.StreamCallback callback = new AiProvider.StreamCallback() {
            @Override
            public boolean onText(String textChunk, boolean done) {
                try {
                    emitter.send(SseEmitter.event().data(textChunk));
                    if (done) {
                        emitter.complete();
                    }
                    return true;
                } catch (IOException e) {
                    log.warn("SSE发送中断, userId={}", userId, e);
                    emitter.completeWithError(e);
                    return false;
                }
            }
        };

        // 异步执行避免阻塞Servlet线程
        new Thread(() -> {
            try {
                AiChatRequest request = AiChatRequest.builder()
                        .systemMessage(systemPrompt)
                        .userMessage(message)
                        .build();
                aiService.chatStreamWithQuota(userId, request, callback);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("[错误] " + e.getMessage()));
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        }).start();

        return emitter;
    }

    // ==================== 用量查询 ====================

    /**
     * 获取当前用户AI用量
     */
    @GetMapping("/usage")
    public Result<Map<String, Object>> getUsage(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(Map.of(
                "provider", aiService.getProviderName(),
                "used", aiService.getDailyLimitPerUser() - aiService.getRemainingQuota(userId),
                "remaining", aiService.getRemainingQuota(userId),
                "dailyLimit", aiService.getDailyLimitPerUser()
        ));
    }

    // ==================== 内部方法 ---

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new IllegalArgumentException("未登录或登录状态异常");
    }

    private String buildParseIssueSystemPrompt(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能项目管理助手，将用户的自然语言描述转换为结构化的任务数据。\n");
        sb.append("严格返回JSON格式，包含以下字段:\n");
        sb.append("{\n");
        sb.append("  \"title\": \"任务标题\",\n");
        sb.append("  \"type\": \"STORY/TASK/BUG(三选一)\",\n");
        sb.append("  \"priority\": \"HIGHEST/HIGH/MEDIUM/LOW/LOWEST\",\n");
        sb.append("  \"assigneeSuggestion\": \"建议指派人(从团队成员中选择)\",\n");
        sb.append("  \"storyPoints\": 数字,\n");
        sb.append("  \"labels\": [\"标签1\",\"标签2\"],\n");
        sb.append("  \"description\": \"## 背景\\n详细描述...\\n\\n## 验收标准\\n- [ ] ...\",\n");
        sb.append("  \"dueDateSuggestion\": \"YYYY-MM-DD(如提及时间则推断)\",\n");
        sb.append("  \"subTaskSuggestions\": [{\"title\":\"子任务\",\"type\":\"TASK\",\"estimate\":数字}],\n");
        sb.append("  \"confidence\": 0.0~1.0,\n");
        sb.append("  \"disclaimer\": \"建议人工审核后保存\"\n");
        sb.append("}\n");

        if (!context.isEmpty()) {
            sb.append("\n项目上下文:\n");
            sb.append(context.toString());
        }
        return sb.toString();
    }
}
