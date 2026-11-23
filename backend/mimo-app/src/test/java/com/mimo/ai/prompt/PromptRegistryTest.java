package com.mimo.ai.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提示词版本化的契约（计划书 §七 技术债「AI 输出不可复现」）。
 *
 * <p>要保证的三件事：
 * <ol>
 *   <li>每个提示词都有稳定的 {@code CODE@vN} 标识，能跟着日志/响应落到复盘材料里；</li>
 *   <li>正文改动会改变指纹 —— 抓"改了文案忘了升版本"；</li>
 *   <li>结构化输出（要 JSON）的温度不能被随手改成高值，否则解析成功率会抖。</li>
 * </ol>
 */
class PromptRegistryTest {

    private static final List<PromptTemplate> STRUCTURED = List.of(
            PromptRegistry.PARSE_ISSUE,
            PromptRegistry.SUGGEST_TASKS,
            PromptRegistry.ESTIMATE_POINTS);

    @Test
    void everyPromptHasVersionedRef() {
        for (PromptTemplate t : PromptRegistry.all().values()) {
            assertThat(t.getVersion()).matches("v\\d+");
            assertThat(t.ref()).isEqualTo(t.getCode() + "@" + t.getVersion());
            assertThat(t.getSystemMessage()).isNotBlank();
            assertThat(t.getMaxTokens()).isPositive();
            assertThat(t.getTemperature()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void structuredPromptsShouldStayCool() {
        // JSON 类输出用高温会随机漂格式，这里把默认值钉住
        for (PromptTemplate t : STRUCTURED) {
            assertThat(t.getTemperature()).isLessThanOrEqualTo(0.5);
        }
    }

    @Test
    void fingerprintIsStableForSameTextAndChangesWithIt() {
        PromptTemplate a = new PromptTemplate("X", "v1", "同样的正文", 0.3, 100);
        PromptTemplate b = new PromptTemplate("X", "v1", "同样的正文", 0.3, 100);
        PromptTemplate c = new PromptTemplate("X", "v2", "改过的正文", 0.3, 100);

        assertThat(a.fingerprint()).hasSize(8).isEqualTo(b.fingerprint());
        assertThat(c.fingerprint()).isNotEqualTo(a.fingerprint());
        // 版本变了但正文没变时，ref 变、指纹不变——两者互补
        PromptTemplate d = new PromptTemplate("X", "v2", "同样的正文", 0.3, 100);
        assertThat(d.ref()).isNotEqualTo(a.ref());
        assertThat(d.fingerprint()).isEqualTo(a.fingerprint());
    }

    @Test
    void registryPromptsAreIndexedByCode() {
        for (Map.Entry<String, PromptTemplate> e : PromptRegistry.all().entrySet()) {
            assertThat(e.getKey()).isEqualTo(e.getValue().getCode());
            assertThat(PromptRegistry.require(e.getKey())).isSameAs(e.getValue());
        }
    }

    @Test
    void unknownPromptShouldFailLoudly() {
        assertThatThrownBy(() -> PromptRegistry.require("NOT_REGISTERED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_REGISTERED");
    }

    @Test
    void appendedContextKeepsVersionButChangesContent() {
        PromptTemplate withContext = PromptRegistry.parseIssueWithContext(Map.of("project", "Mimo"));

        assertThat(withContext.ref()).isEqualTo(PromptRegistry.PARSE_ISSUE.ref());
        assertThat(withContext.getSystemMessage())
                .startsWith(PromptRegistry.PARSE_ISSUE.getSystemMessage())
                .contains("Mimo");
        // 空上下文不该产生任何变化
        assertThat(PromptRegistry.parseIssueWithContext(Map.of())).isSameAs(PromptRegistry.PARSE_ISSUE);
    }

    @Test
    void invalidDefinitionsShouldBeRejected() {
        assertThatThrownBy(() -> new PromptTemplate("", "v1", "x", 0.1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromptTemplate("C", "1.0", "x", 0.1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v1");
        assertThatThrownBy(() -> new PromptTemplate("C", "v1", "  ", 0.1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
