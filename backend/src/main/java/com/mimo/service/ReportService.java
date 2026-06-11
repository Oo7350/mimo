package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ReportDTO.*;
import com.mimo.dto.StatsDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final IssueMapper issueMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final SprintMapper sprintMapper;

    public ReportVO generateDraft(CreateRequest request, Long userId) {
        // 默认使用今天作为报告日期
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDate startDate = reportDate;
        if ("WEEKLY".equals(request.getType())) {
            startDate = startDate.minusDays(6);
        }

        List<Issue> allProjectIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, request.getProjectId()));

        // 已完成任务（按 updatedAt 在报告周期内筛选）
        List<Issue> completedIssues = allProjectIssues.stream()
                .filter(i -> "DONE".equals(i.getStatus()))
                .filter(i -> i.getUpdatedAt() != null && !i.getUpdatedAt().toLocalDate().isBefore(startDate))
                .collect(Collectors.toList());

        // 进行中的任务
        List<Issue> inProgressIssues = allProjectIssues.stream()
                .filter(i -> "IN_PROGRESS".equals(i.getStatus()))
                .collect(Collectors.toList());

        // 待办任务概览
        List<Issue> todoIssues = allProjectIssues.stream()
                .filter(i -> "TODO".equals(i.getStatus()) || ("BUG".equals(i.getType()) && "NEW".equals(i.getBugStatus())))
                .collect(Collectors.toList());

        // 缺陷统计
        long bugCount = allProjectIssues.stream().filter(i -> "BUG".equals(i.getType())).count();
        long openBugs = allProjectIssues.stream()
                .filter(i -> "BUG".equals(i.getType()) && !"CLOSED".equals(i.getBugStatus()))
                .count();

        StringBuilder content = new StringBuilder();
        content.append("## ").append("DAILY".equals(request.getType()) ? "日报" : "周报").append("\n\n");
        content.append("**日期**: ").append(startDate);
        if (!startDate.equals(reportDate)) {
            content.append(" 至 ").append(reportDate);
        }
        content.append("\n\n### 已完成任务 (").append(completedIssues.size()).append(")\n\n");
        if (!completedIssues.isEmpty()) {
            for (Issue issue : completedIssues) {
                content.append("- [x] **[").append(issue.getIssueKey()).append("]** ")
                        .append(issue.getTitle())
                        .append(issue.getAssigneeId() != null ? " → @" + issue.getAssigneeId() : "")
                        .append("\n");
            }
        } else {
            content.append("暂无已完成任务\n");
        }
        content.append("\n### 进行中任务 (").append(inProgressIssues.size()).append(")\n\n");
        if (!inProgressIssues.isEmpty()) {
            for (Issue issue : inProgressIssues) {
                content.append("- 🔄 **[").append(issue.getIssueKey()).append("]** ")
                        .append(issue.getTitle()).append("\n");
            }
        } else {
            content.append("暂无进行中任务\n");
        }

        // 新增：待办和缺陷摘要
        if (!todoIssues.isEmpty()) {
            content.append("\n### 待办任务 (").append(todoIssues.size()).append(")\n\n");
            for (Issue issue : todoIssues.subList(0, Math.min(10, todoIssues.size()))) {
                content.append("- ⏳ **[").append(issue.getIssueKey()).append("]** ")
                        .append(issue.getTitle()).append("\n");
            }
            if (todoIssues.size() > 10) {
                content.append("- _... 还有 ").append(todoIssues.size() - 10).append(" 项_\n");
            }
        }

        if (bugCount > 0) {
            content.append("\n### 缺陷概况\n\n");
            content.append("- 总缺陷: **").append(bugCount).append("**\n");
            content.append("- 未关闭: **").append(openBugs).append("**\n");
            content.append("- 已关闭: **").append(bugCount - openBugs).append("**\n");
        }

        // 项目总览
        content.append("\n---\n\n");
        content.append("**项目总览**: 共 **").append(allProjectIssues.size()).append("** 项任务 | ");
        content.append("完成: **").append(completedIssues.size()).append("** | ");
        content.append("进行中: **").append(inProgressIssues.size()).append("** | ");
        content.append("待办: **").append(todoIssues.size()).append("**\n");

        Report report = new Report();
        report.setUserId(userId);
        report.setProjectId(request.getProjectId());
        report.setType(request.getType());
        report.setReportDate(reportDate);
        report.setContent(content.toString());
        report.setStatus("DRAFT");
        reportMapper.insert(report);

        return toVO(report);
    }

    public ReportVO updateContent(UpdateRequest request, Long userId) {
        Report report = reportMapper.selectById(request.getId());
        if (report == null) throw new BusinessException(ResultCode.NOT_FOUND);
        report.setContent(request.getContent());
        reportMapper.updateById(report);
        return toVO(report);
    }

    public void submit(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException(ResultCode.NOT_FOUND);
        report.setStatus("SUBMITTED");
        reportMapper.updateById(report);
    }

    public List<ReportVO> listByUser(Long userId, Long projectId, String type) {
        LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .orderByDesc(Report::getReportDate);
        if (projectId != null) qw.eq(Report::getProjectId, projectId);
        if (type != null) qw.eq(Report::getType, type);
        return reportMapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    public ReportVO getById(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return toVO(report);
    }

    public StatsVO getProjectStats(Long projectId) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MM-dd");

        // 1. Weekly completed trend
        List<WeeklyItem> weeklyCompleted = new ArrayList<>();
        List<Issue> doneIssues = issueMapper.selectList(
            new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .eq(Issue::getStatus, "DONE")
                .ge(Issue::getUpdatedAt, weekAgo.atStartOfDay())
        );
        Map<LocalDate, Long> dailyCount = doneIssues.stream()
            .collect(Collectors.groupingBy(i -> i.getUpdatedAt().toLocalDate(), Collectors.counting()));
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            WeeklyItem item = new WeeklyItem();
            item.setDate(d.format(dateFmt));
            item.setCount(dailyCount.getOrDefault(d, 0L).intValue());
            weeklyCompleted.add(item);
        }

        // 2. Member distribution (all-time DONE issues)
        List<MemberItem> memberDistribution = new ArrayList<>();
        List<Issue> allDoneIssues = issueMapper.selectList(
            new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .eq(Issue::getStatus, "DONE")
        );
        Map<Long, Long> memberCount = allDoneIssues.stream()
            .filter(i -> i.getAssigneeId() != null)
            .collect(Collectors.groupingBy(Issue::getAssigneeId, Collectors.counting()));
        for (Map.Entry<Long, Long> entry : memberCount.entrySet()) {
            User user = userMapper.selectById(entry.getKey());
            MemberItem item = new MemberItem();
            item.setUsername(user != null ? user.getUsername() : "未知");
            item.setCount(entry.getValue().intValue());
            memberDistribution.add(item);
        }
        memberDistribution.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));

        // 3. Sprint velocity (last 3 completed sprints)
        List<SprintVelocityItem> sprintVelocity = new ArrayList<>();
        List<Sprint> completedSprints = sprintMapper.selectList(
            new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .eq(Sprint::getStatus, "COMPLETED")
                .orderByDesc(Sprint::getEndDate)
                .last("LIMIT 3")
        );
        for (Sprint s : completedSprints) {
            SprintVelocityItem item = new SprintVelocityItem();
            item.setSprintName(s.getName());
            Long total = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, projectId)
                    .eq(Issue::getSprintId, s.getId())
            );
            Long done = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, projectId)
                    .eq(Issue::getSprintId, s.getId())
                    .eq(Issue::getStatus, "DONE")
            );
            item.setTotalPoints(total.intValue());
            item.setCompletedPoints(done.intValue());
            sprintVelocity.add(item);
        }

        StatsVO vo = new StatsVO();
        vo.setWeeklyCompleted(weeklyCompleted);
        vo.setMemberDistribution(memberDistribution);
        vo.setSprintVelocity(sprintVelocity);

        // === 新增：通用数据（不依赖已完成状态）===

        // 4. 任务类型分布（STORY / BUG / TASK）
        List<TypeDistItem> typeDistribution = new ArrayList<>();
        List<Issue> allIssues = issueMapper.selectList(
            new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, projectId));
        Map<String, Long> typeCountMap = allIssues.stream()
            .collect(Collectors.groupingBy(Issue::getType, Collectors.counting()));
        for (String type : List.of("STORY", "TASK", "BUG")) {
            TypeDistItem item = new TypeDistItem();
            item.setType(type);
            item.setLabel("STORY".equals(type) ? "需求" : "BUG".equals(type) ? "缺陷" : "任务");
            item.setCount(typeCountMap.getOrDefault(type, 0L).intValue());
            typeDistribution.add(item);
        }
        vo.setTypeDistribution(typeDistribution);

        // 5. 状态概览
        StatusOverview statusOverview = new StatusOverview();
        statusOverview.setTodoCount((int) allIssues.stream().filter(i -> "TODO".equals(i.getStatus()) || (i.getType() == null ? false : ("BUG".equals(i.getType()) && "NEW".equals(i.getBugStatus())))).count());
        statusOverview.setInProgressCount((int) allIssues.stream().filter(i -> "IN_PROGRESS".equals(i.getStatus())).count());
        statusOverview.setDoneCount((int) allIssues.stream().filter(i -> "DONE".equals(i.getStatus())).count());
        statusOverview.setTotalCount(allIssues.size());
        vo.setStatusOverview(statusOverview);

        // 6. 每日活动（创建 + 更新）
        List<WeeklyActivityItem> weeklyActivity = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long created = allIssues.stream()
                .filter(it -> it.getCreatedAt() != null && it.getCreatedAt().toLocalDate().equals(d))
                .count();
            long updated = allIssues.stream()
                .filter(it -> it.getUpdatedAt() != null && it.getUpdatedAt().toLocalDate().equals(d)
                    && (it.getCreatedAt() == null || !it.getCreatedAt().toLocalDate().equals(d)))
                .count();
            WeeklyActivityItem item = new WeeklyActivityItem();
            item.setDate(d.format(dateFmt));
            item.setCreated((int) created);
            item.setUpdated((int) updated);
            weeklyActivity.add(item);
        }
        vo.setWeeklyActivity(weeklyActivity);

        return vo;
    }

    private ReportVO toVO(Report report) {
        User user = userMapper.selectById(report.getUserId());
        Project project = projectMapper.selectById(report.getProjectId());
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setUsername(user != null ? user.getUsername() : "");
        vo.setProjectId(report.getProjectId());
        vo.setProjectName(project != null ? project.getName() : "");
        vo.setType(report.getType());
        vo.setReportDate(report.getReportDate());
        vo.setContent(report.getContent());
        vo.setStatus(report.getStatus());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }
}
