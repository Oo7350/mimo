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
}
