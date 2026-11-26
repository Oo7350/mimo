package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

public class WorkflowDTO {

    /** 工作流节点 — 一个流程状态；看板列是它的"落脚点"（由它投影出来） */
    @Data
    public static class WorkflowNode {
        /**
         * 节点的稳定身份。
         *
         * <p>v2.22.0 起节点不再用 columnId 当主键：这样节点可以先存在、再落成看板列。
         * 旧配置没有这个字段，读出时按 {@code col-{columnId}} 回填。</p>
         */
        private String nodeKey;
        /** 对应的看板列；自由节点（还没投影）可以为空，保存时由后端建一列并回填 */
        private Long columnId;
        private String name;
        private String color;
        private Boolean isTerminal;
        /**
         * 这一节点的责任人（谁负责把工作项从这里交给下一步）。
         * null / 不填 = 不限制，任何有权限的成员都能推。
         */
        private Long ownerUserId;
        /** 只读回填：责任人显示名（前端不用再查一遍用户表） */
        private String ownerName;
        /** 画布坐标（拖动后保存；为空则按顺序排成一行） */
        private Integer x;
        private Integer y;
    }

    /** 转换规则 — 定义允许的状态跳转 */
    @Data
    public static class WorkflowTransition {
        /** 画布连线用：两端节点的 key（保存时会与 columnId 互相回填） */
        private String fromNodeKey;
        private String toNodeKey;
        private Long fromColumnId;
        private Long toColumnId;
        /** 谁可以做这一步: "ROLE_ADMIN", "ASSIGNEE", "REPORTER" — 命中任一即可；空列表 = 所有人 */
        private List<String> conditions;
        /**
         * 前提条件 — 必须全部满足才允许这一步。
         * 目前支持 "ACCEPTANCE_DONE"（验收标准全部勾完）。
         *
         * <p>和 {@link #conditions} 的区别：conditions 是"谁"，任一命中即可；prerequisites 是"什么情况下"，
         * 缺一条就不许走。两者都过才算允许。</p>
         */
        private List<String> prerequisites;
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
        /**
         * 本次保存时为「自由节点」自动创建的看板列名。
         *
         * <p>不静默建列：前端要明确告诉用户"我给「上线」补了一列"，
         * 否则用户下次看看板会莫名其妙多出一列。</p>
         */
        private List<String> createdColumns;
    }

    /** 可用转换列表项 */
    @Data
    public static class AvailableTransitionVO {
        private Long toColumnId;
        private String toColumnName;
        private List<String> conditions;
        /** 前提条件（全部满足才允许），与 conditions 一起给前端解释"为什么推不动" */
        private List<String> prerequisites;
        /** 出发节点的责任人（= 这一步该谁负责推进）；null 表示不限制 */
        private Long fromOwnerId;
        private String fromOwnerName;
        /** 当前用户能不能执行这一步（前端据此决定按钮是亮着还是给出原因） */
        private Boolean allowed;
        /** allowed=false 时的一句话原因，例如「这一步由「张三」负责」 */
        private String reason;
    }
}
