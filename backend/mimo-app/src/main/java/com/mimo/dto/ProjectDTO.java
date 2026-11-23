package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class ProjectDTO {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "项目名称不能为空")
        private String name;
        @NotBlank(message = "项目Key不能为空")
        private String key;
        private String description;
        private String template = "SCRUM";
        @NotNull(message = "团队ID不能为空")
        private Long teamId;
    }

    /**
     * 项目更新：只允许改名称和描述。
     * Key 故意不可改——任务编号（issue_key）的前缀就是它，改了会让历史任务编号与项目对不上。
     */
    @Data
    public static class UpdateRequest {
        @NotBlank(message = "项目名称不能为空")
        private String name;
        private String description;
    }

    @Data
    public static class ProjectVO {
        private Long id;
        private String name;
        private String key;
        private String description;
        private String template;
        private Long teamId;
        private String teamName;
        private Long ownerId;
        private String ownerName;
        private LocalDateTime createdAt;
    }
}
