package com.mimo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class WorkbenchDTO {

    @Data
    public static class WorkbenchVO {
        private List<WorkbenchTaskVO> tasks;
        private List<WorkbenchTaskVO> dueToday;
        private List<WorkbenchTaskVO> overdue;
        private List<PendingReviewVO> pendingChangeReviews;
        private List<PendingReviewVO> pendingTaskReviews;
        private List<RejectedItemVO> rejectedItems;
        private WeeklySummaryVO weeklySummary;
    }

    @Data
    public static class WorkbenchTaskVO {
        private Long id;
        private String issueKey;
        private String title;
        private String type;
        private String priority;
        private String status;
        private LocalDate dueDate;
        private Long projectId;
        private String projectName;
        private Long sprintId;
        private String sprintName;
        private String riskLevel;
        private List<String> riskReasons;
    }

    @Data
    public static class PendingReviewVO {
        private Long id;
        private String kind;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Long projectId;
        private String projectName;
        private Long submittedById;
        private String submittedByName;
        private LocalDateTime submittedAt;
        private String summary;
    }

    @Data
    public static class RejectedItemVO {
        private Long id;
        private String kind;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Long projectId;
        private String projectName;
        private String reason;
        private LocalDateTime reviewedAt;
    }

    @Data
    public static class WeeklySummaryVO {
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private BigDecimal workHours;
        private Integer completedTaskCount;
        private Integer activeSprintCount;
        private Integer activeSprintStoryPoints;
        private Integer activeSprintDoneStoryPoints;
        private BigDecimal activeSprintHours;
        private List<ActiveSprintEffortVO> activeSprints;
    }

    /**
     * 管理员视角的「全员任务流」（v2.19.8）。
     *
     * <p>为什么单独做一个 VO 而不是复用 {@link WorkbenchVO}：个人工作台的语义是
     * "分配给我的、还没做完的"，而管理员要的是"每个人手上有什么、卡在哪"。
     * 前者按任务平铺，后者必须按人分组，并且要能看出"谁空着、谁堆着、谁逾期"。
     */
    @Data
    public static class TeamWorkbenchVO {
        /** 数据范围说明：SYSTEM = 全部团队，TEAM = 我管理的团队 */
        private String scope;
        /** 这个管理员实际能看到的团队 id（前端用来解释"为什么只有这些人"） */
        private List<Long> teamIds;
        private List<String> teamNames;
        private TeamWorkbenchSummaryVO summary;
        /** 当前页的成员流（按"逾期降序 + 待办降序"排序后切页） */
        private List<MemberStreamVO> members;
        /** 成员总数（筛选后的，不是当前页人数） */
        private Integer memberTotal;
        /** 不筛选时的成员总数（前端用来写"筛出 3 / 共 12 人"） */
        private Integer memberTotalAll;
        /** 当前页码，从 1 开始 */
        private Integer memberPage;
        private Integer memberPageSize;
        /** 总页数；没有成员时也给 1，避免前端显示"第 1/0 页" */
        private Integer memberPageCount;
        /** 每个成员最多带回几条任务；超出部分只给计数（见 MemberStreamVO.taskTotal） */
        private Integer taskLimit;
        /** 近 7 天每天的新建 / 完成数（含今天，按日期升序） */
        private List<TrendPointVO> trend;
        /** 趋势口径说明，直接展示给用户 —— 图表背后不该藏着没人知道的口径 */
        private String trendNote;
    }

    @Data
    public static class TrendPointVO {
        /** 日期 yyyy-MM-dd */
        private String date;
        private Integer created;
        private Integer done;
    }

    @Data
    public static class TeamWorkbenchSummaryVO {
        private Integer memberCount;
        /** 手上还有未完成任务的人数（其余是"当前空闲"） */
        private Integer busyMemberCount;
        private Integer openTaskCount;
        private Integer overdueTaskCount;
        private Integer doneTaskCount;
        private Integer inProgressTaskCount;
        /** 未指派给任何人的未完成任务（管理员最该先处理的一类） */
        private Integer unassignedTaskCount;
        private Integer onlineMemberCount;
        private List<UnassignedTaskVO> unassignedTasks;
    }

    @Data
    public static class UnassignedTaskVO {
        private Long id;
        private String issueKey;
        private String title;
        private String type;
        private String priority;
        private Long projectId;
        private String projectName;
        private LocalDate dueDate;
    }

    /** 一个人的任务流 */
    @Data
    public static class MemberStreamVO {
        private Long userId;
        private String username;
        private String email;
        private String avatar;
        /** 旧字段角色（ROLE_ADMIN / ROLE_MEMBER），仅供展示 */
        private String role;
        /** 是否管理员（系统管理员或某个团队的管理员）—— 界面上给皇冠/盾牌 */
        private Boolean admin;
        /** 是否在线（本实例的 WebSocket 连接，口径同团队页在线人数） */
        private Boolean online;
        private Integer openCount;
        private Integer inProgressCount;
        private Integer overdueTaskCount;
        private Integer doneCount;
        /** 未完成任务，按 逾期 → 到期日 → 优先级 排好序；条数受 taskLimit 限制 */
        private List<WorkbenchTaskVO> tasks;
        /** 这个人一共有几条未完成任务（不管有没有被 taskLimit 截断） */
        private Integer taskTotal;
        /** 负载标签：OVERLOADED / BUSY / NORMAL / IDLE，前端据此上色 */
        private String loadLevel;
        /** 一句话解释负载怎么算出来的，避免界面给个标签却不说依据 */
        private String loadReason;
    }

    @Data
    public static class ActiveSprintEffortVO {
        private Long id;
        private String name;
        private Long projectId;
        private String projectName;
        private Integer assignedTaskCount;
        private Integer completedTaskCount;
        private Integer storyPoints;
        private Integer doneStoryPoints;
        private BigDecimal weekHours;
    }
}
