package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.ai.AiService;
import com.mimo.dto.SprintHealthDTO.AiSummaryVO;
import com.mimo.dto.SprintHealthDTO.SprintHealthVO;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintHealthServiceTest {

    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private IssueReviewMapper issueReviewMapper;
    @Mock
    private AiService aiService;

    @Test
    void shouldReportHealthyWhenWorkTracksTime() {
        LocalDate today = LocalDate.now();
        Sprint sprint = sprint(1L, today.minusDays(2), today.plusDays(5), "ACTIVE");
        when(sprintMapper.selectById(1L)).thenReturn(sprint);

        Issue completed = issue(11L, 2L, "DONE", 3, today.minusDays(1));
        Issue pending = issue(12L, 2L, "TODO", 3, today.plusDays(3));
        List<Issue> issues = List.of(completed, pending);
        when(issueMapper.selectList(any())).thenReturn(issues);
        when(projectMapper.selectById(2L)).thenReturn(project());
        when(issueReviewMapper.selectCount(any())).thenReturn(0L);

        SprintHealthVO health = service().getHealth(1L);

        assertThat(health.getHealthLevel()).isEqualTo("HEALTHY");
        assertThat(health.getTimeProgress()).isEqualTo(37.5);
        assertThat(health.getWorkProgress()).isEqualTo(50.0);
        assertThat(health.getProgressGap()).isEqualTo(-12.5);
        assertThat(health.getRemainingStoryPoints()).isEqualTo(3);
        assertThat(health.getEstimatedDelayDays()).isZero();
        assertThat(health.getRiskReasons()).isNotEmpty();
    }

    @Test
    void shouldReportRiskForEndedSprintWithBlockedAndOverdueWork() {
        LocalDate today = LocalDate.now();
        Sprint sprint = sprint(2L, today.minusDays(10), today.minusDays(1), "ACTIVE");
        when(sprintMapper.selectById(2L)).thenReturn(sprint);

        Issue completed = issue(21L, 2L, "DONE", 3, today.minusDays(8));
        Issue blockedOne = issue(22L, 2L, "TODO", 5, today.minusDays(2));
        blockedOne.setDependencies("[24]");
        Issue blockedTwo = issue(23L, 2L, "IN_PROGRESS", 5, today.minusDays(1));
        blockedTwo.setDependencies("[24]");
        Issue dependency = issue(24L, 2L, "TODO", 2, today.plusDays(2));
        List<Issue> sprintIssues = List.of(completed, blockedOne, blockedTwo);
        List<Issue> projectIssues = List.of(completed, blockedOne, blockedTwo, dependency);
        when(issueMapper.selectList(any())).thenReturn(sprintIssues, projectIssues);
        when(projectMapper.selectById(2L)).thenReturn(project());
        when(issueReviewMapper.selectCount(any())).thenReturn(2L);

        SprintHealthVO health = service().getHealth(2L);

        assertThat(health.getHealthLevel()).isEqualTo("AT_RISK");
        assertThat(health.getBlockedCount()).isEqualTo(2);
        assertThat(health.getOverdueCount()).isEqualTo(2);
        assertThat(health.getPendingReviewCount()).isEqualTo(2);
        assertThat(health.getEstimatedDelayDays()).isGreaterThan(0);
        assertThat(health.getRiskReasons())
                .anyMatch(reason -> reason.contains("超过结束日期"));
    }

    @Test
    void shouldFallbackWhenAiSummaryFails() {
        LocalDate today = LocalDate.now();
        Sprint sprint = sprint(3L, today.plusDays(1), today.plusDays(14), "PLANNING");
        when(sprintMapper.selectById(3L)).thenReturn(sprint);
        when(issueMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectById(2L)).thenReturn(project());
        when(aiService.getProviderName()).thenReturn("DeepSeek");
        when(aiService.chatWithQuota(anyLong(), any())).thenThrow(new RuntimeException("offline"));

        AiSummaryVO summary = service().getAiSummary(3L, 9L);

        assertThat(summary.getAiAvailable()).isFalse();
        assertThat(summary.getSummary()).contains("确定性建议");
        assertThat(summary.getDeterministicSuggestions()).isNotEmpty();
    }

    private SprintHealthService service() {
        return new SprintHealthService(sprintMapper, issueMapper, projectMapper,
                issueReviewMapper, aiService, new ObjectMapper());
    }

    private Sprint sprint(Long id, LocalDate start, LocalDate end, String status) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setProjectId(2L);
        sprint.setName("Sprint " + id);
        sprint.setStartDate(start);
        sprint.setEndDate(end);
        sprint.setStatus(status);
        sprint.setIsActive("ACTIVE".equals(status) ? 1 : 0);
        return sprint;
    }

    private Issue issue(Long id, Long projectId, String status, Integer points, LocalDate dueDate) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(projectId);
        issue.setIssueKey("MIMO-" + id);
        issue.setType("TASK");
        issue.setTitle("Task " + id);
        issue.setStatus(status);
        issue.setStoryPoints(points);
        issue.setDueDate(dueDate);
        return issue;
    }

    private Project project() {
        Project project = new Project();
        project.setId(2L);
        project.setName("Mimo");
        return project;
    }
}
