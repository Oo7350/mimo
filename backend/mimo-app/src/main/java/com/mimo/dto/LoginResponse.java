package com.mimo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String token;
    private UserInfo userInfo;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String avatar;
        private String role;
        /** 管理范围：SYSTEM / TEAM / NONE（同 ProfileVO.adminScope，登录后立刻可用，徽标不用等刷新） */
        private String adminScope;
    }
}
