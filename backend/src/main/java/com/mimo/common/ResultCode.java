package com.mimo.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码 1xxx
    USERNAME_EXISTS(1001, "用户名已存在"),
    EMAIL_EXISTS(1002, "邮箱已被注册"),
    USER_NOT_FOUND(1003, "用户不存在"),
    PASSWORD_ERROR(1004, "密码错误"),
    TOKEN_EXPIRED(1005, "Token已过期"),
    TOKEN_INVALID(1006, "Token无效"),

    TEAM_NOT_FOUND(1101, "团队不存在"),
    TEAM_NAME_EXISTS(1102, "团队名称已存在"),
    NOT_TEAM_ADMIN(1103, "非团队管理员，无权操作"),
    ALREADY_IN_TEAM(1104, "已是团队成员"),

    PROJECT_NOT_FOUND(1201, "项目不存在"),
    NOT_PROJECT_MEMBER(1202, "非项目成员"),

    ISSUE_NOT_FOUND(1301, "任务不存在"),
    COLUMN_NOT_FOUND(1302, "看板列不存在"),

    SPRINT_NOT_FOUND(1401, "Sprint不存在"),
    SPRINT_DATE_CONFLICT(1402, "Sprint日期冲突");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
