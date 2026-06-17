package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.dto.DashboardDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
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
        List<Long> projectIds = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).collect(Collectors.toList());

        // New user with no projects -> return all zeros
        if (projectIds.isEmpty()) {
            return DashboardVO.builder()
                    .totalIssues(0).inProgressIssues(0).doneIssues(0)
                    .bugCount(0).thisWeekActivity(0)
                    .activeSprints(Collections.emptyList()).myProjects(Collections.emptyList())
                    .recentActivities(Collections.emptyList()).build();
        }

        LambdaQueryWrapper<Issue> issueQw = new LambdaQueryWrapper<Issue>()
                .in(Issue::getProjectId, projectIds);
        int totalIssues = issueMapper.selectCount(issueQw).intValue();
        int inProgressIssues = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getStatus, "IN_PROGRESS")).intValue();
        int doneIssues = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getStatus, "DONE")).intValue();

        int bugCount = issueMapper.selectCount(
                issueQw.clone().eq(Issue::getType, "BUG")).intValue();

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int thisWeekActivity = activityLogMapper.selectCount(
                new LambdaQueryWrapper<ActivityLog>()
                        .in(ActivityLog::getProjectId, projectIds)
                        .ge(ActivityLog::getCreatedAt, monday.atStartOfDay())).intValue();

        // Active Sprints
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
        for (Sprint s : sprints) {
            long total = issueMapper.selectCount(
                    new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, s.getId()));
            long done = issueMapper.selectCount(
                    new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, s.getId())
                            .eq(Issue::getStatus, "DONE"));
            sprintTotalMap.put(s.getId(), total);
            sprintDoneMap.put(s.getId(), done);
        }
        final Map<Long, Project> pm = projectMap;
        List<SprintInfo> activeSprints = new ArrayList<>();
        for (Sprint s : sprints) {
            Project p = pm.get(s.getProjectId());
            activeSprints.add(SprintInfo.builder()
                    .id(s.getId()).name(s.getName())
                    .projectId(s.getProjectId())
                    .projectName(p != null ? p.getName() : "")
                    .startDate(s.getStartDate().toString())
                    .endDate(s.getEndDate().toString())
                    .totalIssues(sprintTotalMap.getOrDefault(s.getId(), 0L).intValue())
                    .completedIssues(sprintDoneMap.getOrDefault(s.getId(), 0L).intValue())
                    .build());
        }

        // My Projects
        List<Project> projects = projectMapper.selectBatchIds(projectIds);
        Set<Long> teamIds = projects.stream().map(Project::getTeamId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Team> teamMap = teamIds.isEmpty() ? Collections.emptyMap() :
                teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(Team::getId, t -> t));
        final Map<Long, Team> tm = teamMap;
        List<ProjectInfo> myProjects = new ArrayList<>();
        for (Project p : projects) {
            Team t = tm.get(p.getTeamId());
            myProjects.add(ProjectInfo.builder()
                    .id(p.getId()).name(p.getName()).key(p.getKey())
                    .template(p.getTemplate())
                    .teamName(t != null ? t.getName() : "")
                    .build());
        }

        // Recent Activities
        List<ActivityLog> logs = activityLogMapper.selectList(
                new LambdaQueryWrapper<ActivityLog>()
                        .in(ActivityLog::getProjectId, projectIds)
                        .orderByDesc(ActivityLog::getCreatedAt)
                        .last("LIMIT 10"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<ActivityInfo> recentActivities = new ArrayList<>();
        for (ActivityLog l : logs) {
            recentActivities.add(ActivityInfo.builder()
                    .id(l.getId()).username(l.getUsername())
                    .targetType(l.getTargetType()).action(l.getAction())
                    .detail(l.getDetail())
                    .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().format(fmt) : "")
                    .build());
        }

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
