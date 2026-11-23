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

    @Around("@annotation(com.mimo.common.RequirePermission) || @within(com.mimo.common.RequirePermission)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RequirePermission rp = resolveAnnotation(pjp);
        if (rp == null) {
            // 切点已经限定过，正常不会走到这里；保底放行，绝不抛 NPE
            return pjp.proceed();
        }
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

    /**
     * 取当前调用上的 {@link RequirePermission}：先方法、再实现方法、最后声明类。
     *
     * <p>为什么不再用 {@code @Around("@annotation(rp) || @within(rp)")} 直接绑定参数：
     * 两个切点用 {@code ||} 合并时，参数绑定只在**命中的那一支**有效，另一支给 null。
     * RoleController 是方法级注解 → 命中 {@code @annotation}，{@code @within} 那一支传的就是 null，
     * 于是 {@code rp.scopeType()} 直接 NPE、接口回 500（BUG-059：角色新增/删除/分配/撤销全报错）。
     * 自己解析注解没有这个问题，也不依赖切入点表达式的绑定行为。
     */
    private RequirePermission resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        RequirePermission rp = method.getAnnotation(RequirePermission.class);
        if (rp != null) return rp;

        Object target = pjp.getTarget();
        if (target == null) return null;
        try {
            Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            rp = targetMethod.getAnnotation(RequirePermission.class);
            if (rp != null) return rp;
        } catch (NoSuchMethodException ignored) {
            // 退到类级注解
        }
        return target.getClass().getAnnotation(RequirePermission.class);
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
