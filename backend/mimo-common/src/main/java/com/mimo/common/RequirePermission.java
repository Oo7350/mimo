package com.mimo.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解 — 用于 Controller 方法或类
 * <p>
 * 示例：
 * <pre>
 * &#064;RequirePermission("issue.delete")
 * public void deleteIssue(...) { ... }
 * </pre>
 * <p>
 * scopeType/scopeId 通过 SpEL 从方法参数解析，例如：
 * <pre>
 * &#064;RequirePermission(value = "project.member.remove", scopeType = "'PROJECT'", scopeId = "#projectId")
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** 权限编码，如 "issue.delete" */
    String value();

    /** scope 类型，可选：'GLOBAL' / 'TEAM' / 'PROJECT'，支持 SpEL */
    String scopeType() default "'GLOBAL'";

    /** scope ID，支持 SpEL，GLOBAL 时忽略 */
    String scopeId() default "";
}
