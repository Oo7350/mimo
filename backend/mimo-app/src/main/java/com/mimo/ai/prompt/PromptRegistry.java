package com.mimo.ai.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 所有 AI 提示词的**唯一来源**（计划书 §七 技术债「AI 输出不可复现」）。
 *
 * <p>以前这些 systemMessage 字符串直接写在 {@code AiController} 的各个方法里，
 * 温度也用 {@code .temperature(0.3)} 硬编码在 builder 链上：改一个字没人知道，
 * 同一功能两个入口能写出两份不一样的说法，出问题也无法回答"这次用的是哪版"。
 *
 * <p>这里的约定：
 * <ul>
 *   <li>只增不改 —— 要改就新增一个 {@code v2} 常量，并把老版本标记 {@code @Deprecated} 保留一轮，
 *       这样历史日志里的 {@code ref} 还能对得上；</li>
 *   <li>调用方通过 {@link #ref()} 把版本号带进请求，最终落到日志和响应体里；</li>
 *   <li>结构化输出（要 JSON）的提示词温度一律 ≤ 0.3，温湿度这类默认值跟提示词写在同一个地方。</li>
 * </ul>
 */
public final class PromptRegistry {

    private PromptRegistry() {}

    /** 任务描述润色：保原意、不加新信息 */
    public static final PromptTemplate POLISH = new PromptTemplate(
            "ISSUE_POLISH", "v1",
            "你是一个专业的项目管理助手，擅长将粗糙的草稿润色为正式、清晰、可执行的任务描述。"
                    + "使用中文输出，保持原意不添加新信息。",
            0.7, 1024);

    /** 优先级推荐：只要 JSON */
    public static final PromptTemplate PRIORITY = new PromptTemplate(
            "ISSUE_PRIORITY", "v1",
            "你是一个敏捷项目管理专家，根据任务的紧急性和重要性评估优先级。只返回JSON格式，不要其他内容。",
            0.7, 1024);

    /** 自然语言 → 结构化任务 */
    public static final PromptTemplate PARSE_ISSUE = new PromptTemplate(
            "ISSUE_DRAFT", "v1",
            "你是一个智能项目管理助手，将用户的自然语言描述转换为结构化的任务数据。\n"
                    + "严格返回JSON格式，包含以下字段:\n"
                    + "{\n"
                    + "  \"title\": \"任务标题\",\n"
                    + "  \"type\": \"STORY/TASK/BUG(三选一)\",\n"
                    + "  \"priority\": \"HIGHEST/HIGH/MEDIUM/LOW/LOWEST\",\n"
                    + "  \"assigneeSuggestion\": \"建议指派人(从团队成员中选择)\",\n"
                    + "  \"storyPoints\": 数字,\n"
                    + "  \"labels\": [\"标签1\",\"标签2\"],\n"
                    + "  \"description\": \"## 背景\\n详细描述...\\n\\n## 验收标准\\n- [ ] ...\",\n"
                    + "  \"dueDateSuggestion\": \"YYYY-MM-DD(如提及时间则推断)\",\n"
                    + "  \"subTaskSuggestions\": [{\"title\":\"子任务\",\"type\":\"TASK\",\"estimate\":数字}],\n"
                    + "  \"confidence\": 0.0~1.0,\n"
                    + "  \"disclaimer\": \"建议人工审核后保存\"\n"
                    + "}",
            0.3, 2048);

    /** Story → 子任务拆分 */
    public static final PromptTemplate SUGGEST_TASKS = new PromptTemplate(
            "STORY_SPLIT", "v1",
            "你是一个资深Scrum Master，擅长将大型用户故事拆分为可执行的子任务。输出结构化JSON。",
            0.4, 2048);

    /** 故事点估算 */
    public static final PromptTemplate ESTIMATE_POINTS = new PromptTemplate(
            "STORY_POINTS", "v1",
            "你是一个敏捷开发估算专家，基于Fibonacci序列进行故事点估算。"
                    + "考虑复杂度、工作量、不确定性因素。",
            0.2, 512);

    /** 通用流式对话的兜底人设（前端没传 systemPrompt 时用） */
    public static final PromptTemplate CHAT = new PromptTemplate(
            "CHAT_DEFAULT", "v1",
            "你是一个项目管理AI助手",
            0.7, 4096);

    private static final Map<String, PromptTemplate> BY_CODE = new LinkedHashMap<>();

    static {
        for (PromptTemplate t : new PromptTemplate[]{POLISH, PRIORITY, PARSE_ISSUE,
                SUGGEST_TASKS, ESTIMATE_POINTS, CHAT}) {
            BY_CODE.put(t.getCode(), t);
        }
    }

    /** 按 code 取提示词（找不到直接抛，避免悄悄用上"无提示词"的默认行为） */
    public static PromptTemplate require(String code) {
        PromptTemplate t = BY_CODE.get(code);
        if (t == null) throw new IllegalArgumentException("未登记的提示词: " + code);
        return t;
    }

    /** 已登记的提示词快照，供诊断/测试枚举 */
    public static Map<String, PromptTemplate> all() {
        return Map.copyOf(BY_CODE);
    }

    /** 解析任务时把项目上下文追加进去，版本号不变（上下文是运行时数据，不是提示词内容） */
    public static PromptTemplate parseIssueWithContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) return PARSE_ISSUE;
        return PARSE_ISSUE.withAppendedContext("项目上下文:\n" + context);
    }
}
