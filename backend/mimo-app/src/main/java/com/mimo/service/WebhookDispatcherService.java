package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.entity.WebhookConfig;
import com.mimo.entity.WebhookDeliveryLog;
import com.mimo.mapper.WebhookConfigMapper;
import com.mimo.mapper.WebhookDeliveryLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Webhook 投递服务（v2.13.5）
 * <p>
 * 职责：
 * 1. 按事件类型查询订阅的 Webhook 列表
 * 2. 异步 POST 回调外部 URL，附带 HMAC-SHA256 签名
 * 3. 落库投递日志（成功/失败/响应码/耗时），便于排查
 */
@Service
@Slf4j
public class WebhookDispatcherService {

    private final WebhookConfigMapper webhookConfigMapper;
    private final WebhookDeliveryLogMapper deliveryLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    @Qualifier("webhookTaskExecutor")
    private AsyncTaskExecutor taskExecutor;

    public WebhookDispatcherService(WebhookConfigMapper m1, WebhookDeliveryLogMapper m2) {
        this.webhookConfigMapper = m1;
        this.deliveryLogMapper = m2;
    }

    /**
     * 异步派发事件到所有匹配的 Webhook。
     *
     * @param eventType 事件类型，如 ISSUE_CREATED
     * @param projectId 项目ID（用于过滤项目级 Webhook），可为 null
     * @param teamId   团队ID，可为 null
     * @param payload  事件负载（会被序列化为 JSON）
     */
    public void dispatch(String eventType, Long projectId, Long teamId, Object payload) {
        List<WebhookConfig> hooks = findMatching(eventType, projectId, teamId);
        if (hooks.isEmpty()) return;

        for (WebhookConfig hook : hooks) {
            taskExecutor.submit(() -> deliver(hook, eventType, payload));
        }
    }

    /**
     * 测试投递：直接向指定 Webhook 发送测试事件，绕过 events 过滤。
     * 用于 WebhookConfigController.test 端点，避免被 events 过滤掉。
     */
    public void deliverTest(WebhookConfig hook, Object payload) {
        taskExecutor.submit(() -> deliver(hook, "WEBHOOK_TEST", payload));
    }

    /**
     * 查询匹配的 Webhook 列表（启用 + 事件订阅 + 项目/团队范围）。
     */
    public List<WebhookConfig> findMatching(String eventType, Long projectId, Long teamId) {
        Long projFilter = projectId == null ? -1L : projectId;
        Long teamFilter = teamId == null ? -1L : teamId;
        return webhookConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WebhookConfig>()
                        .eq(WebhookConfig::getEnabled, 1)
                        .and(w -> w.isNull(WebhookConfig::getProjectId)
                                .or().eq(WebhookConfig::getProjectId, projFilter))
                        .and(w -> w.isNull(WebhookConfig::getTeamId)
                                .or().eq(WebhookConfig::getTeamId, teamFilter)))
                .stream()
                .filter(h -> h.getEvents() != null && Arrays.asList(h.getEvents().split(","))
                        .contains(eventType))
                .collect(Collectors.toList());
    }

    private void deliver(WebhookConfig hook, String eventType, Object payload) {
        long start = System.currentTimeMillis();
        WebhookDeliveryLog logEntry = new WebhookDeliveryLog();
        logEntry.setWebhookId(hook.getId());
        logEntry.setEventType(eventType);
        try {
            String body = objectMapper.writeValueAsString(buildBody(eventType, hook, payload));
            logEntry.setPayload(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String signature = sign(hook.getSecret(), body);
            if (signature != null) {
                headers.set("X-Mimo-Signature", signature);
            }
            headers.set("X-Mimo-Event", eventType);
            headers.set("User-Agent", "Mimo-Webhook/1.0");

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    hook.getUrl(), HttpMethod.POST, entity, String.class);

            logEntry.setStatusCode(resp.getStatusCodeValue());
            String respBody = resp.getBody();
            if (respBody != null && respBody.length() > 2000) {
                respBody = respBody.substring(0, 2000);
            }
            logEntry.setResponseBody(respBody);
            logEntry.setSuccess(resp.getStatusCode().is2xxSuccessful() ? 1 : 0);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                logEntry.setError("HTTP " + resp.getStatusCodeValue());
            }
        } catch (Exception e) {
            logEntry.setSuccess(0);
            logEntry.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.warn("[Webhook] 投递失败 webhook={} url={} err={}", hook.getId(), hook.getUrl(), e.getMessage());
        } finally {
            logEntry.setDurationMs((int) (System.currentTimeMillis() - start));
            logEntry.setCreatedAt(LocalDateTime.now());
            try {
                deliveryLogMapper.insert(logEntry);
            } catch (Exception ex) {
                log.error("[Webhook] 日志落库失败: {}", ex.getMessage());
            }
        }
    }

    private Map<String, Object> buildBody(String eventType, WebhookConfig hook, Object payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("event", eventType);
        body.put("webhookId", hook.getId());
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("data", payload);
        return body;
    }

    /**
     * 计算 HMAC-SHA256 签名（hex），用于对方校验请求来源。
     */
    private String sign(String secret, String body) {
        if (secret == null || secret.isEmpty()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("[Webhook] 签名失败: {}", e.getMessage());
            return null;
        }
    }
}
