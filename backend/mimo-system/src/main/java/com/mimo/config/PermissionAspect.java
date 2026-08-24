package com.mimo.config;

import com.mimo.common.BusinessException;
import com.mimo.common.RequirePermission;
import com.mimo.common.ResultCode;
import com.mimo.service.RoleService;
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

import java.lang.reflect.Method;

/**
 * 权限校验 AOP 切面 — 处理 {@link RequirePermission} 注解
 * <p>
 * scopeType/scopeId 通过 SpEL 解析，默认为 GLOBAL。
 * <p>
 * 超级管理员（SUPER_ADMIN 角色）由 RoleService 内部判定，直接通过。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionAspect {

    private final RoleService roleService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(rp) || @within(rp)")
    public Object around(ProceedingJoinPoint pjp, RequirePermission rp) throws Throwable {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        String scopeType = eval(rp.scopeType(), pjp, String.class);
        if (!StringUtils.hasText(scopeType)) scopeType = "GLOBAL";
        Long scopeId = StringUtils.hasText(rp.scopeId())
                ? eval(rp.scopeId(), pjp, Long.class) : null;

        if (!roleService.hasPermission(userId, rp.value(), scopeType, scopeId)) {
            log.warn("[Permission] 拒绝: userId={} code={} scope={}/{}",
                    userId, rp.value(), scopeType, scopeId);
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限: " + rp.value());
        }
        return pjp.proceed();
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

    private <T> T eval(String expr, ProceedingJoinPoint pjp, Class<T> clazz) {
        if (!StringUtils.hasText(expr)) return null;
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        // 设置方法参数
        String[] paramNames = new DefaultParameterNameDiscoverer().getParameterNames(method);
        Object[] args = pjp.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }
        // 设置 root 为方法调用上下文
        ctx.setRootObject(pjp.getTarget());
        return parser.parseExpression(expr).getValue(ctx, clazz);
    }
}
