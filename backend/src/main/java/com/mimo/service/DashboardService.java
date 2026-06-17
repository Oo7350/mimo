package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.dto.DashboardDTO.*;
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
public class DashboardService {

    private final IssueMapper issueMapper;
    private final SprintMapper sprintMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ActivityLogMapper activityLogMapper;
    private final TeamMapper teamMapper;

    public DashboardVO getDashboard(Long userId) {
        // 获取用户参与的所有项目ID
        List<Long> projectIds = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).collect(Collectors.toList());

        // 新用户未加入任何项目，返回空数据
        if (projectIds.isEmpty()) {
            return DashboardVO.builder()
                    .totalIssues(0).inProgressIssues(0).doneIssues(0)
                    .bugCount(0).thisWeekActivity(0)
                    .activeSprints(List.of()).myProjects(List.of())
                    .recentActivities(List.of()).build();
        }

        // 我的任务统计
        LambdaQueryWrapper<Issue> issueQw = new LambdaQueryWrapper<Issue>()
                .in(Issue::getProjectId, projectIds);
        int totalIssues = issueMapper.selectCount(issueQw).intValue();
        int inProgressIssues = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getStatus, "IN_PROGRESS")).intValue();
        int doneIssues = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getStatus, "DONE")).intValue();

        // BUG 缺陷数
        int bugCount = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getType, "BUG")).intValue();

        // 本周活跃（周一到今天的动态数）
        LocalDate monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        int thisWeekActivity = activityLogMapper.selectCount(
                new LambdaQueryWrapper<ActivityLog>()
                        .in(ActivityLog::getProjectId, projectIds)
                        .ge(ActivityLog::getCreatedAt, monday.atStartOfDay())).intValue();

        // 活跃 Sprint
        List<Sprint> sprints = sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .in(Sprint::getProjectId, projectIds)
                        .eq(Sprint::getIsActive, 1));
        Set<Long> sprintProjectIds = sprints.stream().map(Sprint::getProjectId).collect(Collectors.toSet());
        Map<Long, Project> projectMap = sprintProjectIds.isEmpty() ? Collections.emptyMap() :
                projectMapper.selectBatchIds(sprintProjectIds).stream()
                        .collect(Collectors.toMap(Project::getId, p -> p));
        Map<Long, Long> sprintTotalMap = new HashMap<>();
        Map<Long, Long> sprintDoneMap = new HashMap<>();
        if (!sprints.isEmpty()) {
            for (Sprint s : sprints) {
                long total = issueMapper.selectCount(
                        new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, s.getId()));
                long done = issueMapper.selectCount(
                        new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, s.getId())
                                .eq(Issue::getStatus, "DONE"));
                sprintTotalMap.put(s.getId(), total);
                sprintDoneMap.put(s.getId(), done);
            }
        }
        final Map<Long, Project> pm = projectMap;
        List<SprintInfo> activeSprints = sprints.stream().map(s -> {
            Project p = pm.get(s.getProjectId());
            return SprintInfo.builder()
                    .id(s.getId()).name(s.name())
                    .projectId(s.getProjectId())
                    .projectName(p != null ? p.getName() : "")
                    .startDate(s.getStartDate().toString())
                    .endDate(s.getEndDate().toString())
                    .totalIssues(sprintTotalMap.getOrDefault(s.getId(), 0L).intValue())
                    .completedIssues(sprintDoneMap.getOrDefault(s.getId(), 0L).intValue())
                    .build();
        }).collect(Collectors.toList());

        // 我的项目
        List<Project> projects = projectMapper.selectBatchIds(projectIds);
        Set<Long> teamIds = projects.stream().map(Project::getTeamId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Team> teamMap = teamIds.isEmpty() ? Collections.emptyMap() :
                teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(Team::getId, t -> t));
        final Map<Long, Team> tm = teamMap;
        List<ProjectInfo> myProjects = projects.stream().map(p -> {
            Team t = tm.get(p.getTeamId());
            return ProjectInfo.builder()
                    .id(p.getId()).name(p.getName()).key(p.getKey())
                    .template(p.getTemplate())
                    .teamName(t != null ? t.getName() : "")
                    .build();
        }).collect(Collectors.toList());

        // 近期动态
        List<ActivityLog> logs = activityLogMapper.selectList(
                new LambdaQueryWrapper<ActivityLog>()
                        .in(ActivityLog::getProjectId, projectIds)
                        .orderByDesc(ActivityLog::getCreatedAt)
                        .last("LIMIT 10"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<ActivityInfo> recentActivities = logs.stream().map(l -> ActivityInfo.builder()
                .id(l.getId()).username(l.getUsername())
                .targetType(l.getTargetType()).action(l.getAction())
                .detail(l.getDetail())
                .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().format(fmt) : "")
                .build()).collect(Collectors.toList());

        return DashboardVO.builder()
                .totalIssues(totalIssues)
                .inProgressIssues(inProgressIssues)
                .doneIssues(doneIssues)
                .bugCount(bugCount)
                .thisWeekActivity(thisWeekActivity)
                .activeSprints(activeSprints)
                .myProjects(myProjects)
                .recentActivities(recentActivities)
                .build();
    }
}
