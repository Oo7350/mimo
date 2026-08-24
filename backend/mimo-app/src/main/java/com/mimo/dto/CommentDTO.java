package com.mimo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class CommentDTO {

    @Data
    public static class CreateRequest {
        @NotNull(message = "任务ID不能为空")
        private Long issueId;

        @NotBlank(message = "评论内容不能为空")
        private String content;
    }

    @Data
    public static class CommentVO {
        private Long id;
        private Long issueId;
        private Long userId;
        private String username;
        private String content;
        private String createdAt;
    }
}
