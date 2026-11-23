package com.mimo.config;

import com.mimo.common.BusinessException;
import com.mimo.common.RequirePermission;
import com.mimo.common.ResultCode;
import com.mimo.service.RoleService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionAspect 单元测试 —— 覆盖 BUG-059。
 *
 * <p>BUG-059：切面写成 {@code @Around("@annotation(rp) || @within(rp)")}，方法级注解（RoleController 的
 * 新增角色 / 删除角色 / 分配角色 / 撤销角色）命中 @annotation 分支时，@within 那一支给 rp 绑定 null，
 * 于是 {@code rp.scopeType()} NPE，接口一律 500 —— 也就是「角色权限」页面上所有写操作都是坏的。
 *
 * <p>这里不启 Spring 上下文：直接构造 ProceedingJoinPoint 造出"注解在方法上 / 注解在类上 / 没注解"三种场景，
 * 断言校验行为正确、且不会因为注解绑定问题抛 NPE。
 */
class PermissionAspectTest {

    private RoleService roleService;
    private PermissionAspect aspect;

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        aspect = new PermissionAspect(roleService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- 被测目标：提供方法级 / 类级注解的方法 ----------

    static class MethodAnnotatedTarget {
        @RequirePermission("role.manage")
        public String assign() {
            return "ok";
        }
    }

    @RequirePermission("audit.log.view")
    static class ClassAnnotatedTarget {
        public String list() {
            return "ok";
        }
    }

    static class PlainTarget {
        public String open() {
            return "ok";
        }
    }

    private ProceedingJoinPoint pjpFor(Method method, Object target) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenReturn("ok");
        return pjp;
    }

    @Test
    @DisplayName("方法级注解：无权限时拒绝，且不再执行方法（BUG-059 回归点：不允许 NPE）")
    void methodAnnotatedShouldRejectWhenNoPermission() throws Throwable {
        when(roleService.hasPermission(anyLong(), anyString(), anyString(), isNull())).thenReturn(false);
        Method method = MethodAnnotatedTarget.class.getMethod("assign");
        ProceedingJoinPoint pjp = pjpFor(method, new MethodAnnotatedTarget());

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限: role.manage")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());

        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("方法级注解：有权限时放行，返回原方法结果")
    void methodAnnotatedShouldProceedWhenAllowed() throws Throwable {
        when(roleService.hasPermission(anyLong(), anyString(), anyString(), isNull())).thenReturn(true);
        Method method = MethodAnnotatedTarget.class.getMethod("assign");
        ProceedingJoinPoint pjp = pjpFor(method, new MethodAnnotatedTarget());

        assertThat(aspect.around(pjp)).isEqualTo("ok");
        // 默认 scope 是 GLOBAL，scopeId 为 null
        verify(roleService).hasPermission(7L, "role.manage", "GLOBAL", null);
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("类级注解同样生效")
    void classAnnotatedShouldBeEnforced() throws Throwable {
        when(roleService.hasPermission(anyLong(), anyString(), anyString(), isNull())).thenReturn(false);
        Method method = ClassAnnotatedTarget.class.getMethod("list");
        ProceedingJoinPoint pjp = pjpFor(method, new ClassAnnotatedTarget());

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("audit.log.view");
        verify(roleService).hasPermission(7L, "audit.log.view", "GLOBAL", null);
    }

    @Test
    @DisplayName("未登录：直接 401，不进入权限判断")
    void unauthenticatedShouldBeRejected() throws Throwable {
        SecurityContextHolder.clearContext();
        Method method = MethodAnnotatedTarget.class.getMethod("assign");
        ProceedingJoinPoint pjp = pjpFor(method, new MethodAnnotatedTarget());

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
        verify(roleService, never()).hasPermission(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("没有注解：放行且不查权限（保底路径不抛 NPE）")
    void plainMethodShouldProceedWithoutCheck() throws Throwable {
        Method method = PlainTarget.class.getMethod("open");
        ProceedingJoinPoint pjp = pjpFor(method, new PlainTarget());

        assertThat(aspect.around(pjp)).isEqualTo("ok");
        verify(roleService, never()).hasPermission(anyLong(), anyString(), any(), any());
    }
}
