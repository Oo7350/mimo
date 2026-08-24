package com.mimo.config;

import com.mimo.common.AuditLog;
import com.mimo.entity.ActivityLog;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.entity.User;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 操作审计 AOP 切面 — 处理 {@link AuditLog} 注解
 * <p>
 * 自动捕获当前登录用户、IP、UA、RequestId，并按 SpEL 表达式解析 targetType/targetId/action/detail。
 * 入库失败不阻断主流程（仅打 warn 日志），避免审计功能影响业务可用性。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final ActivityLogMapper activityLogMapper;
    private final UserMapper userMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(al)")
    public Object around(ProceedingJoinPoint pjp, AuditLog al) throws Throwable {
        Object result = pjp.proceed();
        try {
            ActivityLog log = buildLog(pjp, al, result);
            activityLogMapper.insert(log);
        } catch (Throwable t) {
            // 审计落库失败不能阻断业务
            AuditLogAspect.log.warn("[AuditLog] 入库失败: {}", t.getMessage());
        }
        return result;
    }

    private ActivityLog buildLog(ProceedingJoinPoint pjp, AuditLog al, Object result) {
        ActivityLog entity = new ActivityLog();
        entity.setCreatedAt(LocalDateTime.now());

        // 当前用户
        Long userId = currentUserId();
        entity.setUserId(userId);
        if (userId != null) {
            User u = userMapper.selectById(userId);
            if (u != null) entity.setUsername(u.getUsername());
        }

        // HTTP 请求信息
        HttpServletRequest req = currentRequest();
        if (req != null) {
            entity.setIpAddress(resolveIp(req));
            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
            entity.setUserAgent(ua);
        }
        entity.setRequestId(UUID.randomUUID().toString());

        // SpEL 表达式解析
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        StandardEvaluationContext ctx = buildContext(pjp, method, result);

        String targetType = eval(al.targetType(), ctx, String.class);
        if (StringUtils.hasText(targetType)) entity.setTargetType(targetType);
        Long targetId = eval(al.targetId(), ctx, Long.class);
        if (targetId != null) entity.setTargetId(targetId);
        String action = eval(al.action(), ctx, String.class);
        if (StringUtils.hasText(action)) entity.setAction(action);
        String detail = eval(al.detail(), ctx, String.class);
        if (StringUtils.hasText(detail)) entity.setDetail(detail);
        return entity;
    }

    private StandardEvaluationContext buildContext(ProceedingJoinPoint pjp, Method method, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        String[] paramNames = discoverer.getParameterNames(method);
        Object[] args = pjp.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }
        // 返回值可作为 detail 的引用
        ctx.setVariable("result", result);
        ctx.setRootObject(pjp.getTarget());
        return ctx;
    }

    private <T> T eval(String expr, StandardEvaluationContext ctx, Class<T> clazz) {
        if (!StringUtils.hasText(expr)) return null;
        try {
            return parser.parseExpression(expr).getValue(ctx, clazz);
        } catch (Exception e) {
            log.debug("[AuditLog] SpEL 解析失败 expr={} err={}", expr, e.getMessage());
            return null;
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        if (p instanceof Long l) return l;
        if (p instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /** 处理反向代理场景，优先取 X-Forwarded-For 的第一个 IP */
    private String resolveIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) return ip;
        return req.getRemoteAddr();
    }
}
