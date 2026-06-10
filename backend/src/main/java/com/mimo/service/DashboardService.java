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

        // 我的任务统计
        LambdaQueryWrapper<Issue> issueQw = new LambdaQueryWrapper<Issue>();
        if (!projectIds.isEmpty()) {
            issueQw.in(Issue::getProjectId, projectIds);
        }
        int totalIssues = issueMapper.selectCount(issueQw).intValue();
        int inProgressIssues = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getStatus, "IN_PROGRESS")).intValue();
        int doneIssues = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getStatus, "DONE")).intValue();

        // 活跃 Sprint — 批量优化：避免循环内逐条查询
        List<SprintInfo> activeSprints = List.of();
        if (!projectIds.isEmpty()) {
            List<Sprint> sprints = sprintMapper.selectList(
                    new LambdaQueryWrapper<Sprint>()
                            .in(Sprint::getProjectId, projectIds)
                            .eq(Sprint::getIsActive, 1));
            // 批量查询 Sprint 关联的项目
            Set<Long> sprintProjectIds = sprints.stream().map(Sprint::getProjectId).collect(Collectors.toSet());
            Map<Long, Project> projectMap = sprintProjectIds.isEmpty() ? Collections.emptyMap() :
                    projectMapper.selectBatchIds(sprintProjectIds).stream()
                            .collect(Collectors.toMap(Project::getId, p -> p));
            // 批量统计每个 Sprint 的 issue 数量
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
            activeSprints = sprints.stream().map(s -> {
                Project p = pm.get(s.getProjectId());
                return SprintInfo.builder()
                        .id(s.getId()).name(s.getName())
                        .projectId(s.getProjectId())
                        .projectName(p != null ? p.getName() : "")
                        .startDate(s.getStartDate().toString())
                        .endDate(s.getEndDate().toString())
                        .totalIssues(sprintTotalMap.getOrDefault(s.getId(), 0L).intValue())
                        .completedIssues(sprintDoneMap.getOrDefault(s.getId(), 0L).intValue())
                        .build();
            }).collect(Collectors.toList());
        }

        // 我的项目 — 批量查询团队信息，避免 N+1
        List<ProjectInfo> myProjects = List.of();
        if (!projectIds.isEmpty()) {
            List<Project> projects = projectMapper.selectBatchIds(projectIds);
            Set<Long> teamIds = projects.stream().map(Project::getTeamId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Team> teamMap = teamIds.isEmpty() ? Collections.emptyMap() :
                    teamMapper.selectBatchIds(teamIds).stream()
                            .collect(Collectors.toMap(Team::getId, t -> t));
            final Map<Long, Team> tm = teamMap;
            myProjects = projects.stream().map(p -> {
                Team t = tm.get(p.getTeamId());
                return ProjectInfo.builder()
                        .id(p.getId()).name(p.getName()).key(p.getKey())
                        .template(p.getTemplate())
                        .teamName(t != null ? t.getName() : "")
                        .build();
            }).collect(Collectors.toList());
        }

        // 近期动态
        List<ActivityInfo> recentActivities = List.of();
        if (!projectIds.isEmpty()) {
            List<ActivityLog> logs = activityLogMapper.selectList(
                    new LambdaQueryWrapper<ActivityLog>()
                            .in(ActivityLog::getProjectId, projectIds)
                            .orderByDesc(ActivityLog::getCreatedAt)
                            .last("LIMIT 10"));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            recentActivities = logs.stream().map(l -> ActivityInfo.builder()
                    .id(l.getId()).username(l.getUsername())
                    .targetType(l.getTargetType()).action(l.getAction())
                    .detail(l.getDetail())
                    .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().format(fmt) : "")
                    .build()).collect(Collectors.toList());
        }

        return DashboardVO.builder()
                .totalIssues(totalIssues)
                .inProgressIssues(inProgressIssues)
                .doneIssues(doneIssues)
                .activeSprints(activeSprints)
                .myProjects(myProjects)
                .recentActivities(recentActivities)
                .build();
    }
}
