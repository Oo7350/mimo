package com.mimo.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UserDTO {

    @Data
    public static class ProfileVO {
        private Long id;
        private String username;
        private String email;
        private String avatar;
        private String role;
        private String createdAt;
        /**
         * 管理范围：SYSTEM（全部团队）/ TEAM（只管自己管理的团队）/ NONE。
         *
         * <p>由后端 {@code AccessControlService#resolveAdminScope} 判定，前端只读它来决定
         * 显示不显示管理员身份徽标与「全员任务流」入口 —— 前端拿不到旧字段兜底，
         * 自己算会算错（v2.19.8 之前的实际故障：zhangsan 后端放行、前端把入口藏了）。
         */
        private String adminScope;
    }

    @Data
    public static class UpdateProfileRequest {
        @Size(min = 3, max = 50, message = "用户名长度3-50字符")
        private String username;

        @Email(message = "邮箱格式不正确")
        private String email;

        private String avatar;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "密码至少6位")
        private String newPassword;
    }

    @Data
    public static class UserSearchVO {
        private Long id;
        private String username;
        private String email;
        private String avatar;
    }
}
