package com.mimo.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解 — 标记需要记录到 activity_logs 的 Controller 方法
 * <p>
 * 注解只负责声明"需要记录什么"，AOP 切面负责实际入库（含 IP/UA/RequestId 等审计字段）。
 * <p>
 * 示例：
 * <pre>
 * &#064;AuditLog(targetType = "'ISSUE'", targetId = "#issue.id", action = "'UPDATE'", detail = "'更新任务:' + #issue.title")
 * public IssueVO updateIssue(@RequestBody Issue issue) { ... }
 * </pre>
 * <p>
 * 所有字段支持 SpEL 表达式（字符串字面量要用单引号包起来）。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /** 操作对象类型：ISSUE / PROJECT / SPRINT / TEAM / ROLE / USER / APPROVAL / COMMENT / WIKI 等 */
    String targetType() default "";

    /** 操作对象 ID（支持 SpEL） */
    String targetId() default "";

    /** 动作：CREATE / UPDATE / DELETE / MOVE / ASSIGN / APPROVE 等 */
    String action() default "";

    /** 变更详情（支持 SpEL，会被记录到 detail 字段） */
    String detail() default "";
}
