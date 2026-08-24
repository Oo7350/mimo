package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

public class WorkflowDTO {

    /** 工作流节点 — 映射到 board_columns */
    @Data
    public static class WorkflowNode {
        private Long columnId;
        private String name;
        private String color;
        private Boolean isTerminal;
    }

    /** 转换规则 — 定义允许的状态跳转 */
    @Data
    public static class WorkflowTransition {
        private Long fromColumnId;
        private Long toColumnId;
        /** 条件列表: "ROLE_ADMIN", "ASSIGNEE", "REPORTER" — 空列表 = 所有人 */
        private List<String> conditions;
    }

    /** 工作流配置 (存入 DB 的 JSON) */
    @Data
    public static class WorkflowConfig {
        private List<WorkflowNode> nodes;
        private List<WorkflowTransition> transitions;
    }

    /** 创建/更新工作流请求 */
    @Data
    public static class SaveWorkflowRequest {
        @NotNull
        private Long projectId;
        @NotBlank
        private String issueType;   // STORY / TASK / BUG
        @NotBlank
        private String name;
        @NotNull
        private WorkflowConfig config;
    }

    /** 工作流 VO */
    @Data
    public static class WorkflowVO {
        private Long id;
        private Long projectId;
        private String issueType;
        private String name;
        private WorkflowConfig config;
        private Boolean isActive;
        private Boolean isDefault;
    }

    /** 可用转换列表项 */
    @Data
    public static class AvailableTransitionVO {
        private Long toColumnId;
        private String toColumnName;
        private List<String> conditions;
    }
}
