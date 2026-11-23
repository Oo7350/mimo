package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.dto.IssueGovernanceDTO.ChangeRequestVO;
import com.mimo.dto.IssueGovernanceDTO.ReviewVO;
import com.mimo.dto.ProjectDTO.ProjectVO;
import com.mimo.dto.WorkbenchDTO.*;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueChangeRequest;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.entity.WorkLog;
import com.mimo.mapper.IssueChangeRequestMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.WorkLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
