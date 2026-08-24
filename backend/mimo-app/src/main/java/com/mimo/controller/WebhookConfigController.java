package com.mimo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.AuditLog;
import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.entity.WebhookConfig;
import com.mimo.entity.WebhookDeliveryLog;
import com.mimo.mapper.WebhookConfigMapper;
import com.mimo.mapper.WebhookDeliveryLogMapper;
import com.mimo.service.WebhookDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 配置管理端点（v2.13.5）
 * <p>
 * 增删改查 Webhook 配置，查询投递日志，提供测试触发端点。
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookConfigController {

    private final WebhookConfigMapper webhookConfigMapper;
    private final WebhookDeliveryLogMapper deliveryLogMapper;
    private final WebhookDispatcherService dispatcher;

    @GetMapping
    public Result<IPage<WebhookConfig>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false, defaultValue = "false") Boolean includeDisabled) {
        QueryWrapper<WebhookConfig> qw = new QueryWrapper<WebhookConfig>().orderByDesc("created_at");
        if (projectId != null) qw.eq("project_id", projectId);
        if (teamId != null) qw.eq("team_id", teamId);
        if (!Boolean.TRUE.equals(includeDisabled)) {
            qw.eq("enabled", 1);
        }
        IPage<WebhookConfig> p = webhookConfigMapper.selectPage(new Page<>(page, size), qw);
        return Result.success(p);
    }

    @GetMapping("/{id}")
    public Result<WebhookConfig> getById(@PathVariable Long id) {
        WebhookConfig c = webhookConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.NOT_FOUND, "Webhook 不存在");
        return Result.success(c);
    }

    @PostMapping
    @AuditLog(targetType = "'WEBHOOK'", targetId = "#result.data.id", action = "'CREATE'", detail = "'创建 Webhook: ' + #req.name")
    public Result<WebhookConfig> create(@RequestBody WebhookConfig req, org.springframework.security.core.Authentication auth) {
        validateWebhook(req);
        req.setId(null);
        req.setCreatedBy(getUserId(auth));
        req.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        webhookConfigMapper.insert(req);
        log.info("[Webhook] 已创建 id={} name={} url={}", req.getId(), req.getName(), req.getUrl());
        return Result.success(req);
    }

    @PutMapping("/{id}")
    @AuditLog(targetType = "'WEBHOOK'", targetId = "#id", action = "'UPDATE'", detail = "'更新 Webhook'")
    public Result<WebhookConfig> update(@PathVariable Long id, @RequestBody WebhookConfig req) {
        WebhookConfig existing = webhookConfigMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "Webhook 不存在");
        validateWebhook(req);
        req.setId(id);
        req.setUpdatedAt(LocalDateTime.now());
        webhookConfigMapper.updateById(req);
        return Result.success(webhookConfigMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    @AuditLog(targetType = "'WEBHOOK'", targetId = "#id", action = "'DELETE'", detail = "'删除 Webhook'")
    public Result<Void> delete(@PathVariable Long id) {
        WebhookConfig existing = webhookConfigMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "Webhook 不存在");
        webhookConfigMapper.deleteById(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/test")
    @AuditLog(targetType = "'WEBHOOK'", targetId = "#id", action = "'TEST'", detail = "'触发 Webhook 测试投递'")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        WebhookConfig hook = webhookConfigMapper.selectById(id);
        if (hook == null) throw new BusinessException(ResultCode.NOT_FOUND, "Webhook 不存在");
        Map<String, Object> payload = new HashMap<>();
        payload.put("test", true);
        payload.put("message", "Mimo Webhook 测试投递");
        // 立即派发（异步），等待 2s 让日志落库
        dispatcher.deliverTest(hook, payload);
        try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        // 取最近一条投递日志
        WebhookDeliveryLog latest = deliveryLogMapper.selectOne(
                new QueryWrapper<WebhookDeliveryLog>()
                        .eq("webhook_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
        Map<String, Object> ret = new HashMap<>();
        if (latest != null) {
            ret.put("success", latest.getSuccess());
            ret.put("statusCode", latest.getStatusCode());
            ret.put("responseBody", latest.getResponseBody());
            ret.put("error", latest.getError());
            ret.put("durationMs", latest.getDurationMs());
        } else {
            ret.put("success", false);
            ret.put("error", "未在 2 秒内取得投递日志");
        }
        return Result.success(ret);
    }

    @GetMapping("/{id}/deliveries")
    public Result<IPage<WebhookDeliveryLog>> deliveries(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<WebhookDeliveryLog> qw = new QueryWrapper<WebhookDeliveryLog>()
                .eq("webhook_id", id)
                .orderByDesc("created_at");
        IPage<WebhookDeliveryLog> p = deliveryLogMapper.selectPage(new Page<>(page, size), qw);
        return Result.success(p);
    }

    private void validateWebhook(WebhookConfig req) {
        if (req.getName() == null || req.getName().trim().isEmpty())
            throw new BusinessException(ResultCode.BAD_REQUEST, "名称不能为空");
        if (req.getUrl() == null || !req.getUrl().startsWith("http"))
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL 必须以 http 开头");
        if (req.getEvents() == null || req.getEvents().trim().isEmpty())
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择至少一个事件类型");
    }

    private Long getUserId(org.springframework.security.core.Authentication auth) {
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof Long l) return l;
        if (p instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
