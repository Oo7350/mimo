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
        // 查询该用户在此项目指定日期范围内完成的任务
        LocalDate startDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        if ("WEEKLY".equals(request.getType())) {
            startDate = startDate.minusDays(6);
        }

        List<Issue> completedIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, request.getProjectId())
                        .eq(Issue::getStatus, "DONE")
                        .ge(Issue::getUpdatedAt, startDate.atStartOfDay()));

        StringBuilder content = new StringBuilder();
        content.append("## ").append("DAILY".equals(request.getType()) ? "日报" : "周报").append("\n\n");
        content.append("**日期**: ").append(startDate);
        if (!request.getReportDate().equals(startDate)) {
            content.append(" 至 ").append(request.getReportDate());
        }
        content.append("\n\n### 已完成任务\n\n");
        for (Issue issue : completedIssues) {
            content.append("- [").append(issue.getIssueKey()).append("] ")
                    .append(issue.getTitle()).append("\n");
        }
        if (completedIssues.isEmpty()) {
            content.append("暂无已完成任务\n");
        }

        Report report = new Report();
        report.setUserId(userId);
        report.setProjectId(request.getProjectId());
        report.setType(request.getType());
        report.setReportDate(request.getReportDate() != null ? request.getReportDate() : LocalDate.now());
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
