package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ApprovalRequestDTO {
    @NotNull
    private Long teamId;
    private Long projectId;
    @NotBlank
    private String targetType;  // PROJECT_CREATE / PROJECT_UPDATE / PROJECT_DELETE
    private String title;
    private String description;
    private String dataJson;     // JSON格式存储操作数据
}
