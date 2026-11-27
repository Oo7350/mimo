package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueGovernanceDTO.ChangeRequestVO;
import com.mimo.dto.IssueGovernanceDTO.ReviewVO;
import com.mimo.dto.ProjectDTO.ProjectVO;
import com.mimo.dto.WorkbenchDTO.*;
import com.mimo.entity.ActivityLog;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueChangeRequest;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.entity.WorkLog;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.IssueChangeRequestMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WorkLogMapper;
import com.mimo.entity.Team;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.util.BoardColumnRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private static final Map<String, Integer> PRIORITY_RANK = Map.of(
            "HIGHEST", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3, "LOWEST", 4
    );

    /** 全员任务流默认每页几个人 / 每人最多带几条任务，以及上限（防止有人拿 pageSize=99999 打库） */
    static final int DEFAULT_MEMBER_PAGE_SIZE = 8;
    static final int MAX_MEMBER_PAGE_SIZE = 30;
    static final int DEFAULT_TASK_LIMIT = 6;
    static final int MAX_TASK_LIMIT = 20;
    /** 趋势看几天（含今天） */
    static final int TREND_DAYS = 7;

    private final IssueMapper issueMapper;
    private final IssueReviewMapper issueReviewMapper;
    private final IssueChangeRequestMapper issueChangeRequestMapper;
    private final SprintMapper sprintMapper;
    private final ProjectMapper projectMapper;
    private final WorkLogMapper workLogMapper;
    private final ProjectService projectService;
    private final IssueChangeRequestService issueChangeRequestService;
    private final IssueReviewService issueReviewService;
    private final ObjectMapper objectMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final AccessControlService accessControlService;
    private final PresenceService presenceService;

    public WorkbenchVO getMyWorkbench(Long userId) {
        List<Long> projectIds = projectService.listByUser(userId).stream()
                .map(ProjectVO::getId)
                .toList();
        // 注意：不能因为"我不在任何项目里"就整页返回空。待审批 / 待验收可能来自
        // 我能审批但不参与的项目（系统管理员、团队管理员）——这类人恰恰最需要看到它。
        boolean hasProjects = !projectIds.isEmpty();

        List<Issue> assignedIssues = hasProjects
                ? issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getAssigneeId, userId)
                        .in(Issue::getProjectId, projectIds))
                : List.of();
        List<Issue> openIssues = assignedIssues.stream()
                .filter(issue -> !isCompleted(issue))
                .toList();

        Map<Long, Project> projects = loadProjects(projectIds);
        Map<Long, Sprint> sprints = loadSprints(assignedIssues);
        Map<Long, Boolean> blockingDependencies = loadBlockingDependencies(openIssues);

        LocalDate today = LocalDate.now();
        List<WorkbenchTaskVO> tasks = openIssues.stream()
                .map(issue -> toTask(issue, projects, sprints, blockingDependencies, today))
                .sorted(Comparator
                        .comparing(WorkbenchTaskVO::getDueDate,
                                Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparingInt(task -> PRIORITY_RANK.getOrDefault(task.getPriority(), 9)))
                .toList();
        List<WorkbenchTaskVO> dueToday = tasks.stream()
                .filter(task -> today.equals(task.getDueDate()))
                .toList();
        List<WorkbenchTaskVO> overdue = tasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(today))
                .toList();

        List<PendingReviewVO> pendingChanges =
                mapPendingChanges(issueChangeRequestService.listPendingForUser(userId), projects);
        List<PendingReviewVO> pendingReviews =
                mapPendingReviews(issueReviewService.listPendingForUser(userId), projects);
        List<RejectedItemVO> rejectedItems =
                loadRejectedItems(userId, projects, projectIds);
        WeeklySummaryVO weeklySummary =
                buildWeeklySummary(userId, assignedIssues, sprints, projects);

        WorkbenchVO vo = new WorkbenchVO();
        vo.setTasks(tasks);
        vo.setDueToday(dueToday);
        vo.setOverdue(overdue);
        vo.setPendingChangeReviews(pendingChanges);
        vo.setPendingTaskReviews(pendingReviews);
        vo.setRejectedItems(rejectedItems);
        vo.setWeeklySummary(weeklySummary);
        return vo;
    }

    /**
     * 管理员视角：全员任务流（v2.19.8）。
     *
     * <p>口径三条，写在方法上而不是靠前端猜：
     * <ol>
     *   <li>**范围**：系统管理员看全部团队；团队管理员只看自己管理的团队（求并集）。</li>
     *   <li>**成员**：这些团队里的所有成员（含没有任务的人）——空闲的人是信息，不能因为没数据就消失。</li>
     *   <li>**任务**：这些团队下所有项目里的工作项，按 assignee 分组；未指派的单独列。</li>
     * </ol>
     *
     * <p>性能：工作项、项目、迭代、前置依赖各查一次，不是"每人一次"。人数 × 4 次查询的写法
     * 在 10 人团队上就是 40 次往返，管理员每刷新一次页面都要付这个代价。
     */
    public TeamWorkbenchVO getTeamWorkbench(Long operatorId) {
        return getTeamWorkbench(operatorId, 1, DEFAULT_MEMBER_PAGE_SIZE, DEFAULT_TASK_LIMIT,
                null, "all", "overdue");
    }

    /**
     * 管理员视角的全员任务流（v2.20.1 起支持筛选、排序、分页与趋势）。
     *
     * <p>为什么筛选和分页都放在后端：前端切页 + 前端筛选凑在一起会骗人——
     * "有逾期 3 人"实际只数了当前页的 8 个人。既然口径在服务端，筛选也一起在服务端做，
     * 前端只负责把条件发过来（`keyword` / `only` / `sort` / `page`）。</p>
     *
     * <p>为什么分页在后端：否则"分页"只是视觉效果，请求照样把全量数据拉下来。
     * 成员一多，载荷主要浪费在"某个人的第 30 条任务"上，所以每个人另有 `taskLimit`，
     * 超出部分只给计数（`taskTotal`），前端写"还有 N 项"。</p>
     *
     * <p>为什么趋势不自己编：完成数从 `activity_logs` 的真实 MOVE 事件里数
     * （目标列按 {@link com.mimo.util.BoardColumnRules#isTerminalName} 判定），
     * 不拿 `issues.updated_at` 近似——项目没有单独的完成时间字段，
     * 用更新时间冒充会在"改个标题"时虚增完成数。口径说明随接口一起下发（`trendNote`）。</p>
     */
    public TeamWorkbenchVO getTeamWorkbench(Long operatorId, int memberPage, int pageSize, int taskLimit,
                                            String keyword, String only, String sort) {
        int page = Math.max(1, memberPage);
        int size = Math.max(1, Math.min(pageSize, MAX_MEMBER_PAGE_SIZE));
        int limit = Math.max(1, Math.min(taskLimit, MAX_TASK_LIMIT));

        boolean systemAdmin = accessControlService.isSystemAdmin(operatorId);

        // 先确定范围：系统管理员 = 全部团队；团队管理员 = 自己管理的团队
        List<Team> allTeams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getDeleted, 0)
                .orderByAsc(Team::getId));
        List<Team> visibleTeams = new ArrayList<>();
        for (Team team : allTeams) {
            if (systemAdmin || accessControlService.isTeamAdmin(operatorId, team.getId())) {
                visibleTeams.add(team);
            }
        }
        if (!systemAdmin && visibleTeams.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "只有管理员能查看全员任务流（系统管理员看全部团队，团队管理员看自己管理的团队）");
        }

        List<Long> teamIds = visibleTeams.stream().map(Team::getId).toList();
        TeamWorkbenchVO vo = new TeamWorkbenchVO();
        vo.setScope(systemAdmin ? "SYSTEM" : "TEAM");
        vo.setTeamIds(teamIds);
        vo.setTeamNames(visibleTeams.stream().map(Team::getName).toList());

        TeamWorkbenchSummaryVO summary = new TeamWorkbenchSummaryVO();
        vo.setSummary(summary);
        vo.setMemberPage(1);
        vo.setMemberPageSize(size);
        vo.setMemberPageCount(1);
        vo.setMemberTotal(0);
        vo.setTaskLimit(limit);
        vo.setTrend(emptyTrend());
        vo.setTrendNote(trendNote());
        if (teamIds.isEmpty()) {
            summary.setMemberCount(0);
            summary.setBusyMemberCount(0);
            summary.setOpenTaskCount(0);
            summary.setOverdueTaskCount(0);
            summary.setDoneTaskCount(0);
            summary.setInProgressTaskCount(0);
            summary.setUnassignedTaskCount(0);
            summary.setOnlineMemberCount(0);
            summary.setUnassignedTasks(List.of());
            vo.setMembers(List.of());
            return vo;
        }

        // 成员：团队里去重后的用户
        List<TeamMember> teamMembers = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, teamIds));
        Map<Long, String> memberRoles = new LinkedHashMap<>();
        for (TeamMember member : teamMembers) {
            memberRoles.putIfAbsent(member.getUserId(), member.getRole());
        }
        Map<Long, User> users = new HashMap<>();
        if (!memberRoles.isEmpty()) {
            userMapper.selectBatchIds(memberRoles.keySet()).forEach(u -> users.put(u.getId(), u));
        }
        // 在线状态一次算完，别每个成员问一次
        Set<Long> onlineUserIds = presenceService.filterOnline(memberRoles.keySet());
        // 管理员标记也一次算完（v2.22.4）：原来是每个成员问 6 条 SELECT，100 人 = 606 条 / 433ms
        Set<Long> adminUserIds = accessControlService.filterTeamAdmins(memberRoles.keySet(), teamIds);

        // 项目与工作项：团队下全部项目（含我不参与的项目）
        List<Long> projectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .in(Project::getTeamId, teamIds)
                        .eq(Project::getDeleted, 0))
                .stream().map(Project::getId).toList();
        List<Issue> issues = projectIds.isEmpty() ? List.of()
                : issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                        .in(Issue::getProjectId, projectIds));

        Map<Long, Project> projects = loadProjects(projectIds);
        Map<Long, Sprint> sprints = loadSprints(issues);
        List<Issue> openIssues = issues.stream().filter(issue -> !isCompleted(issue)).toList();
        Map<Long, Boolean> blockingDependencies = loadBlockingDependencies(openIssues);

        LocalDate today = LocalDate.now();
        Map<Long, List<Issue>> byAssignee = new LinkedHashMap<>();
        List<UnassignedTaskVO> unassigned = new ArrayList<>();
        int doneCount = 0;
        for (Issue issue : issues) {
            if (isCompleted(issue)) {
                doneCount++;
                continue;
            }
            if (issue.getAssigneeId() == null) {
                unassigned.add(toUnassigned(issue, projects));
            } else {
                byAssignee.computeIfAbsent(issue.getAssigneeId(), key -> new ArrayList<>()).add(issue);
            }
        }

        List<MemberStreamVO> streams = new ArrayList<>();
        for (Map.Entry<Long, String> entry : memberRoles.entrySet()) {
            Long userId = entry.getKey();
            User user = users.get(userId);
            List<Issue> assigned = byAssignee.getOrDefault(userId, List.of());
            List<WorkbenchTaskVO> allTasks = assigned.stream()
                    .map(issue -> toTask(issue, projects, sprints, blockingDependencies, today))
                    .sorted(taskComparator())
                    .toList();
            // 任务数受 taskLimit 限制：成员一多，载荷主要浪费在"某个人的第 30 条任务"上，
            // 而管理员通常只关心最该处理的几条；剩余数量通过 taskTotal 让前端说清"还有 N 项"
            List<WorkbenchTaskVO> tasks = allTasks.size() > limit
                    ? new ArrayList<>(allTasks.subList(0, limit))
                    : allTasks;

            MemberStreamVO stream = new MemberStreamVO();
            stream.setUserId(userId);
            stream.setUsername(user == null ? ("用户#" + userId) : user.getUsername());
            stream.setEmail(user == null ? null : user.getEmail());
            stream.setAvatar(user == null ? null : user.getAvatar());
            stream.setRole(entry.getValue());
            stream.setOnline(onlineUserIds.contains(userId));
            // 口径仍是「旧字段 ∪ RBAC」，只是从逐人数次查询换成了一次批量判定（结果逐人相同）
            stream.setAdmin(adminUserIds.contains(userId));
            stream.setTasks(tasks);
            stream.setTaskTotal(allTasks.size());
            // 计数一律按"全部任务"算，不能按被截断的列表算 —— 否则负载标签会说谎
            stream.setOpenCount(allTasks.size());
            stream.setInProgressCount((int) allTasks.stream()
                    .filter(task -> "IN_PROGRESS".equals(task.getStatus())).count());
            stream.setOverdueTaskCount((int) allTasks.stream()
                    .filter(task -> "HIGH".equals(task.getRiskLevel())
                            && task.getRiskReasons() != null
                            && task.getRiskReasons().stream().anyMatch(reason -> reason.startsWith("已逾期")))
                    .count());
            stream.setDoneCount((int) issues.stream()
                    .filter(issue -> userId.equals(issue.getAssigneeId()) && isCompleted(issue)).count());
            applyLoad(stream);
            streams.add(stream);
        }
        streams.sort(Comparator
                .comparing(MemberStreamVO::getOverdueTaskCount, Comparator.reverseOrder())
                .thenComparing(MemberStreamVO::getOpenCount, Comparator.reverseOrder())
                .thenComparing(MemberStreamVO::getUserId));

        unassigned.sort(Comparator
                .comparing(UnassignedTaskVO::getDueDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(task -> PRIORITY_RANK.getOrDefault(task.getPriority(), 9)));

        // 概览统计永远按"全部成员"算，不受筛选影响 —— 否则筛一下"空闲"，
        // 面板顶上的"未完成/逾期"会跟着变成空闲成员的数字，看着像全队突然轻松了。
        summary.setMemberCount(streams.size());
        summary.setBusyMemberCount((int) streams.stream()
                .filter(stream -> stream.getOpenCount() != null && stream.getOpenCount() > 0).count());

        // 筛选 → 排序 → 切页，顺序不能反：先切页再筛选会得到"这一页里符合条件的几个人"
        List<MemberStreamVO> matched = filterStreams(streams, keyword, only);
        sortStreams(matched, sort);

        int memberTotal = matched.size();
        int pageCount = Math.max(1, (memberTotal + size - 1) / size);
        int safePage = Math.min(page, pageCount);
        int fromIndex = Math.min((safePage - 1) * size, memberTotal);
        int toIndex = Math.min(fromIndex + size, memberTotal);
        vo.setMembers(new ArrayList<>(matched.subList(fromIndex, toIndex)));
        vo.setMemberTotal(memberTotal);
        vo.setMemberTotalAll(streams.size());
        vo.setMemberPage(safePage);
        vo.setMemberPageSize(size);
        vo.setMemberPageCount(pageCount);
        vo.setTaskLimit(limit);
        vo.setTrend(buildTrend(projectIds, today));
        vo.setTrendNote(trendNote());

        summary.setMemberCount(streams.size());
        summary.setBusyMemberCount((int) streams.stream()
                .filter(stream -> stream.getOpenCount() != null && stream.getOpenCount() > 0).count());
        summary.setOpenTaskCount(openIssues.size());
        summary.setInProgressTaskCount((int) openIssues.stream()
                .filter(issue -> "IN_PROGRESS".equals(issue.getStatus())).count());
        summary.setOverdueTaskCount((int) openIssues.stream()
                .filter(issue -> issue.getDueDate() != null && issue.getDueDate().isBefore(today)).count());
        summary.setDoneTaskCount(doneCount);
        summary.setUnassignedTaskCount(unassigned.size());
        summary.setUnassignedTasks(unassigned);
        summary.setOnlineMemberCount(onlineUserIds.size());

        // 注意：这里不能再 vo.setMembers(streams) —— members 已经按"筛选 + 排序 + 切页"设置过，
        // 覆盖成全量会让分页看起来生效（页码在变）而实际把所有成员都发下来。
        return vo;
    }

    /** 只保留符合筛选条件的成员；条件为空/未知一律不筛 */
    private List<MemberStreamVO> filterStreams(List<MemberStreamVO> streams, String keyword, String only) {
        String text = keyword == null ? "" : keyword.trim().toLowerCase();
        String mode = only == null ? "all" : only.trim().toLowerCase();
        return streams.stream()
                .filter(stream -> switch (mode) {
                    case "overdue" -> stream.getOverdueTaskCount() != null && stream.getOverdueTaskCount() > 0;
                    case "doing" -> stream.getInProgressCount() != null && stream.getInProgressCount() > 0;
                    case "idle" -> stream.getOpenCount() != null && stream.getOpenCount() == 0;
                    default -> true;
                })
                .filter(stream -> text.isEmpty() || matchesKeyword(stream, text))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean matchesKeyword(MemberStreamVO stream, String text) {
        String username = stream.getUsername() == null ? "" : stream.getUsername().toLowerCase();
        if (username.contains(text)) return true;
        // 任务标题只看已下发的那部分：被 taskLimit 截断的任务没传给前端，
        // 若在后端按全量标题匹配，前端会出现"搜到了人但列不出那条任务"的怪状态
        return stream.getTasks() != null && stream.getTasks().stream()
                .anyMatch(task -> task.getTitle() != null && task.getTitle().toLowerCase().contains(text));
    }

    private void sortStreams(List<MemberStreamVO> streams, String sort) {
        String mode = sort == null ? "overdue" : sort.trim().toLowerCase();
        Comparator<MemberStreamVO> comparator = switch (mode) {
            case "name" -> Comparator.comparing(MemberStreamVO::getUsername,
                    Comparator.nullsLast(String::compareTo));
            case "load" -> Comparator
                    .comparing(MemberStreamVO::getOpenCount, Comparator.reverseOrder())
                    .thenComparing(MemberStreamVO::getUserId);
            default -> Comparator
                    .comparing(MemberStreamVO::getOverdueTaskCount, Comparator.reverseOrder())
                    .thenComparing(MemberStreamVO::getOpenCount, Comparator.reverseOrder())
                    .thenComparing(MemberStreamVO::getUserId);
        };
        streams.sort(comparator);
    }

    /** 任务排序：逾期的先上，再按计划完成日，再按优先级 —— 与个人工作台同一口径 */
    private Comparator<WorkbenchTaskVO> taskComparator() {
        return Comparator
                .comparing(WorkbenchTaskVO::getDueDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparingInt(task -> PRIORITY_RANK.getOrDefault(task.getPriority(), 9));
    }

    /**
     * 近 {@value #TREND_DAYS} 天的新建 / 完成趋势。
     *
     * <p>数据来源是 `activity_logs` 的真实事件，不是猜的：
     * 新建数 = `CREATE` 事件，完成数 = `MOVE` 事件且**目标列**是终态列。
     * 目标列名从事件描述里取（"将 DEMO-1 从 待办 移动到 完成"），再按该项目的列名集合判定，
     * 判定规则与看板拖拽共用 {@link com.mimo.util.BoardColumnRules#isTerminalName}。</p>
     *
     * <p>已知边界（写在 `trendNote` 里一起给前端）：
     * ① 只统计当前还能看到的项目；② 列被重命名后，历史事件里的旧列名匹配不上会被跳过（少算不多算）。</p>
     */
    private List<TrendPointVO> buildTrend(List<Long> projectIds, LocalDate today) {
        Map<String, TrendPointVO> byDate = new LinkedHashMap<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            TrendPointVO point = new TrendPointVO();
            point.setDate(date);
            point.setCreated(0);
            point.setDone(0);
            byDate.put(date, point);
        }
        if (projectIds.isEmpty()) return new ArrayList<>(byDate.values());

        LocalDateTime since = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        // v2.22.4：数在库里数完再回来（原来是把近 7 天明细整批搬进 JVM 再遍历）。
        // 5000 条活动日志时那一步就把一个 /workbench/team 拖掉了 200ms 以上。
        for (ActivityLogMapper.TrendCountRow row : activityLogMapper.countCreatedByDay(projectIds, since)) {
            TrendPointVO point = row.getDay() == null ? null : byDate.get(row.getDay().toString());
            if (point != null) point.setCreated((int) row.getCnt());
        }
        // 终态列名仍然是 Java 侧按 BoardColumnRules 算出来的（判定只在一处），
        // SQL 只负责"把描述里的目标列名和这个项目现有的列名对一下"。
        Map<Long, Set<String>> terminalCache = new HashMap<>();
        Set<String> terminalNames = new HashSet<>();
        for (Long projectId : projectIds) {
            terminalNames.addAll(terminalColumnsOf(projectId, terminalCache));
        }
        if (!terminalNames.isEmpty()) {
            for (ActivityLogMapper.TrendCountRow row
                    : activityLogMapper.countMovedToTerminalByDay(projectIds, since, terminalNames)) {
                TrendPointVO point = row.getDay() == null ? null : byDate.get(row.getDay().toString());
                if (point != null) point.setDone((int) row.getCnt());
            }
        }
        return new ArrayList<>(byDate.values());
    }

    /** 从 "将 DEMO-1 从 待办 移动到 完成" 里取出目标列名；取不到返回 null（不猜） */
    static String targetColumnName(String detail) {
        if (detail == null) return null;
        int idx = detail.lastIndexOf("移动到 ");
        if (idx < 0) idx = detail.lastIndexOf("移动到");
        if (idx < 0) return null;
        String target = detail.substring(idx + ("移动到".length())).trim();
        if (target.isEmpty()) return null;
        return target;
    }

    /**
     * 这个项目的**终态列名集合**（每次调用只查一次库，按项目缓存）。
     *
     * <p>一开始写的是"按（项目, 列名）逐个查"，一天几十条事件就是几十次往返；
     * 而且把判定逻辑靠 SQL 的 where 保证，单测里 mock 一粗就会误判。现在把项目的列一次取回来，
     * 判定留给 {@link BoardColumnRules}：既少查库，判定也只在一处。</p>
     */
    private Set<String> terminalColumnsOf(Long projectId, Map<Long, Set<String>> cache) {
        if (projectId == null) return Set.of();
        return cache.computeIfAbsent(projectId, key -> boardColumnMapper.selectList(
                        new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, key))
                .stream()
                .filter(column -> BoardColumnRules.isTerminalName(column.getName()))
                .map(BoardColumn::getName)
                .collect(Collectors.toSet()));
    }

    private List<TrendPointVO> emptyTrend() {
        List<TrendPointVO> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            TrendPointVO point = new TrendPointVO();
            point.setDate(today.minusDays(i).toString());
            point.setCreated(0);
            point.setDone(0);
            trend.add(point);
        }
        return trend;
    }

    private String trendNote() {
        return "近 " + TREND_DAYS + " 天：新建 = 工作项创建事件，完成 = 拖进「完成/关闭」列的事件"
                + "（取自活动日志；列被改名后更早的历史事件可能统计不到）";
    }

    private UnassignedTaskVO toUnassigned(Issue issue, Map<Long, Project> projects) {
        UnassignedTaskVO vo = new UnassignedTaskVO();
        vo.setId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setTitle(issue.getTitle());
        vo.setType(issue.getType());
        vo.setPriority(issue.getPriority());
        vo.setProjectId(issue.getProjectId());
        Project project = projects.get(issue.getProjectId());
        vo.setProjectName(project == null ? "" : project.getName());
        vo.setDueDate(issue.getDueDate());
        return vo;
    }

    /**
     * 负载分级。刻意用**能说清的规则**而不是加权评分：界面上写"为什么标红"，
     * 管理员才会信这个标签。逾期权重最高——一件事逾期了，数量多少都无所谓了。
     */
    private void applyLoad(MemberStreamVO stream) {
        int open = stream.getOpenCount() == null ? 0 : stream.getOpenCount();
        int overdue = stream.getOverdueTaskCount() == null ? 0 : stream.getOverdueTaskCount();
        if (open == 0) {
            stream.setLoadLevel("IDLE");
            stream.setLoadReason("手上没有未完成任务");
        } else if (overdue >= 2 || open >= 8) {
            stream.setLoadLevel("OVERLOADED");
            stream.setLoadReason(overdue >= 2
                    ? "已有 " + overdue + " 项逾期，未完成 " + open + " 项"
                    : "未完成 " + open + " 项，属于堆活最多的一档");
        } else if (overdue == 1 || open >= 5) {
            stream.setLoadLevel("BUSY");
            stream.setLoadReason(overdue == 1
                    ? "有 1 项逾期，未完成 " + open + " 项"
                    : "未完成 " + open + " 项，接近上限");
        } else {
            stream.setLoadLevel("NORMAL");
            stream.setLoadReason("未完成 " + open + " 项，节奏正常");
        }
    }

    private WorkbenchTaskVO toTask(Issue issue, Map<Long, Project> projects,
                                   Map<Long, Sprint> sprints,
                                   Map<Long, Boolean> blockingDependencies,
                                   LocalDate today) {
        WorkbenchTaskVO vo = new WorkbenchTaskVO();
        vo.setId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setTitle(issue.getTitle());
        vo.setType(issue.getType());
        vo.setPriority(issue.getPriority());
        vo.setStatus(issue.getStatus());
        vo.setDueDate(issue.getDueDate());
        vo.setProjectId(issue.getProjectId());
        Project project = projects.get(issue.getProjectId());
        vo.setProjectName(project == null ? "" : project.getName());
        vo.setSprintId(issue.getSprintId());
        Sprint sprint = sprints.get(issue.getSprintId());
        vo.setSprintName(sprint == null ? null : sprint.getName());

        List<String> reasons = new ArrayList<>();
        boolean overdue = issue.getDueDate() != null && issue.getDueDate().isBefore(today);
        if (overdue) {
            reasons.add("已逾期 " + ChronoUnit.DAYS.between(issue.getDueDate(), today) + " 天");
        }
        if (Boolean.TRUE.equals(blockingDependencies.get(issue.getId()))) {
            reasons.add("存在未完成的前置工作项");
        }
        boolean highPrioritySoon = issue.getDueDate() != null
                && !issue.getDueDate().isAfter(today.plusDays(2))
                && ("HIGHEST".equals(issue.getPriority()) || "HIGH".equals(issue.getPriority()));
        if (highPrioritySoon && !overdue) {
            reasons.add("高优先级且将在 2 天内到期");
        }
        if (overdue || Boolean.TRUE.equals(blockingDependencies.get(issue.getId()))) {
            vo.setRiskLevel("HIGH");
        } else if (highPrioritySoon || today.equals(issue.getDueDate())) {
            vo.setRiskLevel("MEDIUM");
        } else {
            vo.setRiskLevel("LOW");
        }
        vo.setRiskReasons(reasons);
        return vo;
    }

    private Map<Long, Boolean> loadBlockingDependencies(List<Issue> openIssues) {
        Set<Long> dependencyIds = openIssues.stream()
                .flatMap(issue -> readDependencyIds(issue.getDependencies()).stream())
                .collect(Collectors.toSet());
        Map<Long, Issue> dependencyIssues = dependencyIds.isEmpty() ? Collections.emptyMap()
                : issueMapper.selectBatchIds(dependencyIds).stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));
        Map<Long, Boolean> result = new HashMap<>();
        for (Issue issue : openIssues) {
            boolean blocked = readDependencyIds(issue.getDependencies()).stream()
                    .map(dependencyIssues::get)
                    .filter(Objects::nonNull)
                    .anyMatch(dependency -> !isCompleted(dependency));
            result.put(issue.getId(), blocked);
        }
        return result;
    }

    private List<PendingReviewVO> mapPendingChanges(List<ChangeRequestVO> changes,
                                                    Map<Long, Project> projects) {
        if (changes.isEmpty()) return List.of();
        Map<Long, Issue> issues = loadIssues(changes.stream()
                .map(ChangeRequestVO::getIssueId).collect(Collectors.toSet()));
        List<PendingReviewVO> result = new ArrayList<>();
        for (ChangeRequestVO change : changes) {
            Issue issue = issues.get(change.getIssueId());
            // 这里不再按"我的项目"过滤：listPendingForUser 已经按
            // "团队成员 + 非提交人 + 管理员/报告人"筛过一遍，能审批的人就该看得到。
            // 以前两处口径不一致，导致"系统管理员明明可以批，工作台里却一条都没有"。
            if (issue == null) continue;
            PendingReviewVO vo = new PendingReviewVO();
            vo.setId(change.getId());
            vo.setKind("CHANGE");
            vo.setIssueId(issue.getId());
            vo.setIssueKey(issue.getIssueKey());
            vo.setIssueTitle(issue.getTitle());
            vo.setProjectId(issue.getProjectId());
            vo.setProjectName(projectName(projects, issue.getProjectId()));
            vo.setSubmittedById(change.getApplicantId());
            vo.setSubmittedByName(change.getApplicantName());
            vo.setSubmittedAt(change.getCreatedAt());
            vo.setSummary(change.getChangeReason());
            result.add(vo);
        }
        return result;
    }

    private List<PendingReviewVO> mapPendingReviews(List<ReviewVO> reviews,
                                                    Map<Long, Project> projects) {
        if (reviews.isEmpty()) return List.of();
        Map<Long, Issue> issues = loadIssues(reviews.stream()
                .map(ReviewVO::getIssueId).collect(Collectors.toSet()));
        List<PendingReviewVO> result = new ArrayList<>();
        for (ReviewVO review : reviews) {
            Issue issue = issues.get(review.getIssueId());
            // 同上：能验收的人就该看到，不再要求项目也在"我的项目"里
            if (issue == null) continue;
            PendingReviewVO vo = new PendingReviewVO();
            vo.setId(review.getId());
            vo.setKind("REVIEW");
            vo.setIssueId(issue.getId());
            vo.setIssueKey(issue.getIssueKey());
            vo.setIssueTitle(issue.getTitle());
            vo.setProjectId(issue.getProjectId());
            vo.setProjectName(projectName(projects, issue.getProjectId()));
            vo.setSubmittedById(review.getSubmitterId());
            vo.setSubmittedByName(review.getSubmitterName());
            vo.setSubmittedAt(review.getSubmittedAt());
            vo.setSummary(review.getCompletionNote());
            result.add(vo);
        }
        return result;
    }

    private List<RejectedItemVO> loadRejectedItems(Long userId, Map<Long, Project> projects,
                                                   List<Long> allowedProjectIds) {
        Set<Long> allowed = new HashSet<>(allowedProjectIds);
        List<IssueChangeRequest> changeRows = issueChangeRequestMapper.selectList(
                new LambdaQueryWrapper<IssueChangeRequest>()
                        .eq(IssueChangeRequest::getApplicantId, userId)
                        .eq(IssueChangeRequest::getStatus, "REJECTED")
                        .orderByDesc(IssueChangeRequest::getReviewedAt));
        List<IssueReview> reviewRows = issueReviewMapper.selectList(
                new LambdaQueryWrapper<IssueReview>()
                        .eq(IssueReview::getSubmitterId, userId)
                        .eq(IssueReview::getStatus, "REJECTED")
                        .orderByDesc(IssueReview::getReviewedAt));
        Set<Long> issueIds = new HashSet<>();
        changeRows.forEach(row -> issueIds.add(row.getIssueId()));
        reviewRows.forEach(row -> issueIds.add(row.getIssueId()));
        Map<Long, Issue> issues = loadIssues(issueIds);

        List<RejectedItemVO> result = new ArrayList<>();
        for (IssueChangeRequest row : changeRows) {
            Issue issue = issues.get(row.getIssueId());
            if (issue == null || !allowed.contains(issue.getProjectId())) continue;
            RejectedItemVO vo = new RejectedItemVO();
            vo.setId(row.getId());
            vo.setKind("CHANGE");
            vo.setIssueId(issue.getId());
            vo.setIssueKey(issue.getIssueKey());
            vo.setIssueTitle(issue.getTitle());
            vo.setProjectId(issue.getProjectId());
            vo.setProjectName(projectName(projects, issue.getProjectId()));
            vo.setReason(row.getReviewNote());
            vo.setReviewedAt(row.getReviewedAt());
            result.add(vo);
        }
        for (IssueReview row : reviewRows) {
            Issue issue = issues.get(row.getIssueId());
            if (issue == null || !allowed.contains(issue.getProjectId())) continue;
            RejectedItemVO vo = new RejectedItemVO();
            vo.setId(row.getId());
            vo.setKind("REVIEW");
            vo.setIssueId(issue.getId());
            vo.setIssueKey(issue.getIssueKey());
            vo.setIssueTitle(issue.getTitle());
            vo.setProjectId(issue.getProjectId());
            vo.setProjectName(projectName(projects, issue.getProjectId()));
            vo.setReason(row.getReviewNote());
            vo.setReviewedAt(row.getReviewedAt());
            result.add(vo);
        }
        return result.stream()
                .sorted(Comparator.comparing(RejectedItemVO::getReviewedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private WeeklySummaryVO buildWeeklySummary(Long userId, List<Issue> assignedIssues,
                                               Map<Long, Sprint> sprints,
                                               Map<Long, Project> projects) {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        List<WorkLog> workLogs = workLogMapper.selectList(new LambdaQueryWrapper<WorkLog>()
                .eq(WorkLog::getUserId, userId)
                .between(WorkLog::getWorkDate, monday, sunday));
        BigDecimal workHours = sumHours(workLogs);

        List<Sprint> activeSprints = assignedIssues.stream()
                .map(Issue::getSprintId)
                .filter(Objects::nonNull)
                .distinct()
                .map(sprints::get)
                .filter(Objects::nonNull)
                .filter(this::isActive)
                .toList();
        Map<Long, Sprint> activeSprintMap = activeSprints.stream()
                .collect(Collectors.toMap(Sprint::getId, Function.identity()));
        List<ActiveSprintEffortVO> efforts = new ArrayList<>();
        for (Sprint sprint : activeSprints) {
            List<Issue> sprintIssues = assignedIssues.stream()
                    .filter(issue -> Objects.equals(issue.getSprintId(), sprint.getId()))
                    .toList();
            Set<Long> sprintIssueIds = sprintIssues.stream().map(Issue::getId)
                    .collect(Collectors.toSet());
            BigDecimal sprintHours = sumHours(workLogs.stream()
                    .filter(log -> sprintIssueIds.contains(log.getIssueId()))
                    .toList());
            ActiveSprintEffortVO effort = new ActiveSprintEffortVO();
            effort.setId(sprint.getId());
            effort.setName(sprint.getName());
            effort.setProjectId(sprint.getProjectId());
            effort.setProjectName(projectName(projects, sprint.getProjectId()));
            effort.setAssignedTaskCount(sprintIssues.size());
            effort.setCompletedTaskCount((int) sprintIssues.stream()
                    .filter(this::isCompleted).count());
            effort.setStoryPoints(sprintIssues.stream()
                    .map(Issue::getStoryPoints).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).sum());
            effort.setDoneStoryPoints(sprintIssues.stream()
                    .filter(this::isCompleted)
                    .map(Issue::getStoryPoints).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).sum());
            effort.setWeekHours(sprintHours);
            efforts.add(effort);
        }

        WeeklySummaryVO summary = new WeeklySummaryVO();
        summary.setWeekStart(monday);
        summary.setWeekEnd(sunday);
        summary.setWorkHours(workHours);
        summary.setCompletedTaskCount((int) assignedIssues.stream()
                .filter(this::isCompleted)
                .filter(issue -> issue.getUpdatedAt() != null
                        && !issue.getUpdatedAt().toLocalDate().isBefore(monday))
                .count());
        summary.setActiveSprintCount(activeSprintMap.size());
        summary.setActiveSprintStoryPoints(efforts.stream()
                .mapToInt(ActiveSprintEffortVO::getStoryPoints).sum());
        summary.setActiveSprintDoneStoryPoints(efforts.stream()
                .mapToInt(ActiveSprintEffortVO::getDoneStoryPoints).sum());
        summary.setActiveSprintHours(efforts.stream()
                .map(ActiveSprintEffortVO::getWeekHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));
        summary.setActiveSprints(efforts.stream()
                .sorted(Comparator.comparing(ActiveSprintEffortVO::getWeekHours).reversed())
                .toList());
        return summary;
    }

    private Map<Long, Project> loadProjects(List<Long> projectIds) {
        // 可变 Map：projectName() 会往里补"我不在但有权审批"的项目
        Map<Long, Project> map = new HashMap<>();
        if (projectIds.isEmpty()) return map;
        projectMapper.selectBatchIds(projectIds).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private Map<Long, Sprint> loadSprints(List<Issue> issues) {
        Set<Long> sprintIds = issues.stream().map(Issue::getSprintId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (sprintIds.isEmpty()) return Collections.emptyMap();
        return sprintMapper.selectBatchIds(sprintIds).stream()
                .collect(Collectors.toMap(Sprint::getId, Function.identity()));
    }

    private Map<Long, Issue> loadIssues(Set<Long> issueIds) {
        if (issueIds.isEmpty()) return Collections.emptyMap();
        return issueMapper.selectBatchIds(issueIds).stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));
    }

    private String projectName(Map<Long, Project> projects, Long projectId) {
        if (projectId == null) return "";
        Project project = projects.get(projectId);
        if (project == null) {
            // 待审批/待验收可能来自"我不在这个项目里、但我有资格审批"的工作项，
            // 名字要现查一次，不能因为不在缓存里就显示空白。
            project = projectMapper.selectById(projectId);
            if (project == null) return "";
            projects.put(projectId, project);
        }
        return project.getName();
    }

    private BigDecimal sumHours(List<WorkLog> logs) {
        return logs.stream()
                .map(WorkLog::getHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isActive(Sprint sprint) {
        return Integer.valueOf(1).equals(sprint.getIsActive())
                || "ACTIVE".equals(sprint.getStatus());
    }

    private boolean isCompleted(Issue issue) {
        return "DONE".equals(issue.getStatus())
                || "VERIFIED".equals(issue.getBugStatus())
                || "CLOSED".equals(issue.getBugStatus());
    }

    private List<Long> readDependencyIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
