package com.mimo.ai.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 一个**带版本号**的提示词。
 *
 * <p>计划书 §七 技术债「AI 输出不可复现」的根因不是模型不够稳定，而是**没人知道当时用的是哪版提示词、
 * 什么温度**：同一句需求今天写在这里、明天改在那里，出了问题只看得到一句"AI 猜的"。
 *
 * <p>所以每个提示词都必须是不可变对象，并且同时带三样东西：
 * <ol>
 *   <li>{@code code}：稳定的用途标识（如 {@code ISSUE_DRAFT}），代码里只引用它；</li>
 *   <li>{@code version}：内容一改就必须加一，{@link #ref()} 拼成 {@code CODE@v2} 落在日志和响应里；</li>
 *   <li>{@link #fingerprint()}：正文的短哈希，用来抓"改了文案却忘了改版本号"。</li>
 * </ol>
 *
 * <p>温度与 maxTokens 也随提示词一起定义：结构化输出的任务本来就不该用 0.7，把默认值写在提示词旁边，
 * 比散落在 Controller 里更容易审。
 */
public final class PromptTemplate {

    private final String code;
    private final String version;
    private final String systemMessage;
    private final double temperature;
    private final int maxTokens;

    public PromptTemplate(String code, String version, String systemMessage,
                          double temperature, int maxTokens) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("提示词 code 不能为空");
        if (version == null || !version.matches("v\\d+")) {
            throw new IllegalArgumentException("提示词版本必须是 v1/v2 这种形式: " + version);
        }
        if (systemMessage == null || systemMessage.isBlank()) {
            throw new IllegalArgumentException("提示词正文不能为空: " + code);
        }
        this.code = code;
        this.version = version;
        this.systemMessage = systemMessage;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public String getCode() { return code; }

    public String getVersion() { return version; }

    public String getSystemMessage() { return systemMessage; }

    public double getTemperature() { return temperature; }

    public int getMaxTokens() { return maxTokens; }

    /** 落到日志/响应里的可追溯标识，形如 {@code ISSUE_DRAFT@v1} */
    public String ref() { return code + "@" + version; }

    /**
     * 正文指纹（sha256 前 8 位）。
     *
     * <p>用途：如果只改了文案没改版本号，指纹会变 —— 单测里把它和 ref 一起断言，
     * 就能在 CI 阶段抓住"悄悄改提示词"这种情况，而不是等用户发现结果变了。
     */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(systemMessage.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 追加运行期上下文（如项目信息）后的新提示词，版本与 code 不变 */
    public PromptTemplate withAppendedContext(String context) {
        if (context == null || context.isBlank()) return this;
        return new PromptTemplate(code, version, systemMessage + "\n\n" + context,
                temperature, maxTokens);
    }

    @Override
    public String toString() {
        return ref() + "#" + fingerprint();
    }
}
