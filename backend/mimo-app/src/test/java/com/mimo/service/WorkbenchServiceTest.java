package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.dto.ProjectDTO.ProjectVO;
import com.mimo.dto.WorkbenchDTO.WorkbenchVO;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.entity.WorkLog;
import com.mimo.mapper.IssueChangeRequestMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.WorkLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceTest {

    @Mock
    private IssueMapper issueMapper;
    @Mock
    private IssueReviewMapper issueReviewMapper;
    @Mock
    private IssueChangeRequestMapper issueChangeRequestMapper;
    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkLogMapper workLogMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private IssueChangeRequestService issueChangeRequestService;
    @Mock
    private IssueReviewService issueReviewService;

    @Test
    void shouldReturnCrossProjectTasksRiskAndWeeklySummary() {
        ProjectVO accessibleProject = new ProjectVO();
        accessibleProject.setId(10L);
        accessibleProject.setName("Mimo");
        when(projectService.listByUser(9L)).thenReturn(List.of(accessibleProject));

        LocalDate today = LocalDate.now();
        Issue overdue = issue(1L, "TASK", "逾期联调", "HIGH", today.minusDays(2), 10L, 20L);
        Issue dueToday = issue(2L, "STORY", "今日评审", "MEDIUM", today, 10L, 20L);
        Issue blocked = issue(3L, "TASK", "依赖未完成", "LOW", today.plusDays(3), 10L, 20L);
        blocked.setDependencies("[4]");
        Issue dependency = issue(4L, "TASK", "前置任务", "HIGH", today.plusDays(1), 10L, 20L);
        Issue completed = issue(5L, "TASK", "本周完成", "MEDIUM", today.minusDays(1), 10L, 20L);
        completed.setStatus("DONE");
        completed.setUpdatedAt(LocalDateTime.now());
        when(issueMapper.selectList(any()))
                .thenReturn(List.of(blocked, completed, dueToday, overdue));
        when(issueMapper.selectBatchIds(any())).thenReturn(List.of(dependency));

        Project project = new Project();
        project.setId(10L);
        project.setName("Mimo");
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project));

        Sprint sprint = new Sprint();
        sprint.setId(20L);
        sprint.setName("Sprint 12");
        sprint.setProjectId(10L);
        sprint.setStatus("ACTIVE");
        sprint.setIsActive(1);
        when(sprintMapper.selectBatchIds(any())).thenReturn(List.of(sprint));

        WorkLog firstLog = workLog(1L, new BigDecimal("1.50"));
        WorkLog secondLog = workLog(5L, new BigDecimal("2.00"));
        when(workLogMapper.selectList(any())).thenReturn(List.of(firstLog, secondLog));
        when(issueChangeRequestService.listPendingForUser(9L)).thenReturn(List.of());
        when(issueReviewService.listPendingForUser(9L)).thenReturn(List.of());
        when(issueChangeRequestMapper.selectList(any())).thenReturn(List.of());
        when(issueReviewMapper.selectList(any())).thenReturn(List.of());

        WorkbenchVO result = service().getMyWorkbench(9L);

        assertThat(result.getTasks()).extracting("id").containsExactly(1L, 2L, 3L);
        assertThat(result.getOverdue()).extracting("id").containsExactly(1L);
        assertThat(result.getDueToday()).extracting("id").containsExactly(2L);
        assertThat(result.getTasks().get(0).getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getTasks().get(0).getRiskReasons())
                .anyMatch(reason -> reason.contains("已逾期 2 天"));
        assertThat(result.getTasks().get(2).getRiskReasons())
                .anyMatch(reason -> reason.contains("前置工作项"));
        assertThat(result.getWeeklySummary().getWorkHours())
                .isEqualByComparingTo("3.50");
        assertThat(result.getWeeklySummary().getCompletedTaskCount()).isEqualTo(1);
        assertThat(result.getWeeklySummary().getActiveSprintCount()).isEqualTo(1);
        assertThat(result.getWeeklySummary().getActiveSprints()).hasSize(1);
    }

    @Test
    void shouldReturnClearEmptyStateForUserWithoutProjects() {
        when(projectService.listByUser(99L)).thenReturn(List.of());

        WorkbenchVO result = service().getMyWorkbench(99L);

        assertThat(result.getTasks()).isEmpty();
        assertThat(result.getDueToday()).isEmpty();
        assertThat(result.getOverdue()).isEmpty();
        assertThat(result.getPendingChangeReviews()).isEmpty();
        assertThat(result.getPendingTaskReviews()).isEmpty();
        assertThat(result.getRejectedItems()).isEmpty();
        assertThat(result.getWeeklySummary().getWorkHours()).isEqualByComparingTo("0");
    }

    private WorkbenchService service() {
        return new WorkbenchService(issueMapper, issueReviewMapper, issueChangeRequestMapper,
                sprintMapper, projectMapper, workLogMapper, projectService,
                issueChangeRequestService, issueReviewService, new ObjectMapper());
    }

    private Issue issue(Long id, String type, String title, String priority,
                        LocalDate dueDate, Long projectId, Long sprintId) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setIssueKey("MIMO-" + id);
        issue.setType(type);
        issue.setTitle(title);
        issue.setPriority(priority);
        issue.setStatus("TODO");
        issue.setDueDate(dueDate);
        issue.setProjectId(projectId);
        issue.setSprintId(sprintId);
        issue.setStoryPoints(3);
        return issue;
    }

    private WorkLog workLog(Long issueId, BigDecimal hours) {
        WorkLog log = new WorkLog();
        log.setIssueId(issueId);
        log.setHours(hours);
        log.setWorkDate(LocalDate.now());
        return log;
    }
}
