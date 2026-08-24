package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class TeamDTO {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "团队名称不能为空")
        private String name;
        private String description;
    }

    @Data
    public static class UpdateRequest {
        @NotNull
        private Long id;
        private String name;
        private String description;
    }

    @Data
    public static class InviteRequest {
        @NotNull
        private Long teamId;
        @NotNull
        private Long userId;
        private String role;
    }

    @Data
    public static class TeamVO {
        private Long id;
        private String name;
        private String description;
        private Long ownerId;
        private String ownerName;
        private Integer memberCount;
        private LocalDateTime createdAt;
    }

    @Data
    public static class MemberVO {
        private Long userId;
        private String username;
        private String email;
        private String avatar;
        private String role;
        private LocalDateTime joinedAt;
    }
}
