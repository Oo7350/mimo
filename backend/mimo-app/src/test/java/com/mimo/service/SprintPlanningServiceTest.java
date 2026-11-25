package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.ai.AiService;
import com.mimo.ai.dto.AiChatResponse;
import com.mimo.common.BusinessException;
import com.mimo.dto.DeliveryAnalyticsDTO.VelocityVO;
import com.mimo.dto.SprintPlanningDTO.ApplyPlanVO;
import com.mimo.dto.SprintPlanningDTO.SprintPlanVO;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintPlanningServiceTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private TeamService teamService;
    @Mock
    private DeliveryAnalyticsService deliveryAnalyticsService;
    @Mock
    private AiService aiService;
    @Mock
    private com.mimo.mapper.UserMapper userMapper;
    @Mock
    private RoleService roleService;
    @Mock
    private com.mimo.mapper.TeamMemberMapper teamMemberMapper;

    @Test
    void shouldReturnAdvisoryFallbackWithoutWritingIssues() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Issue high = issue(1L, "H", "HIGH", 2, null);
        Issue low = issue(2L, "L", "LOW", 3, null);
        when(issueMapper.selectList(any())).thenReturn(List.of(low, high));
        VelocityVO velocity = new VelocityVO();
        velocity.setAverageCompletedPoints(20.0);
        when(deliveryAnalyticsService.getVelocity(7L, 6)).thenReturn(velocity);
        when(aiService.chatWithQuota(anyLong(), any()))
                .thenReturn(AiChatResponse.error("offline"));

        SprintPlanVO plan = service().suggestPlan(7L, null, 3, 9L);

        assertThat(plan.getAiAvailable()).isFalse();
        assertThat(plan.getCapacityPoints()).isEqualTo(3);
        assertThat(plan.getRecommendedTasks()).extracting("id").containsExactly(1L);
        assertThat(plan.getRecommendedTasks().get(0).getReason()).contains("高优先级");
        verify(issueMapper, never()).updateById(any());
    }

    @Test
    void shouldApplyOnlyExplicitlySelectedIssues() {
        Sprint sprint = sprint();
        when(sprintMapper.selectById(20L)).thenReturn(sprint);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Issue first = issue(1L, "A", "HIGH", 2, null);
        Issue second = issue(2L, "B", "MEDIUM", 3, null);
        when(issueMapper.selectBatchIds(any())).thenReturn(List.of(first, second));

        ApplyPlanVO result = service().applyPlan(20L, List.of(1L, 2L), 9L);

        assertThat(result.getAppliedCount()).isEqualTo(2);
        assertThat(result.getTotalStoryPoints()).isEqualTo(5);
        assertThat(first.getSprintId()).isEqualTo(20L);
        assertThat(second.getSprintId()).isEqualTo(20L);
        verify(issueMapper).updateById(first);
        verify(issueMapper).updateById(second);
        verify(deliveryAnalyticsService).refreshTodaySnapshot(7L);
    }

    @Test
    void shouldReturnEmptyPlanWhenBacklogHasNoCandidates() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        when(issueMapper.selectList(any())).thenReturn(List.of());
        VelocityVO velocity = new VelocityVO();
        velocity.setAverageCompletedPoints(20.0);
        when(deliveryAnalyticsService.getVelocity(7L, 6)).thenReturn(velocity);

        SprintPlanVO plan = service().suggestPlan(7L, null, null, 9L);

        assertThat(plan.getCandidates()).isEmpty();
        assertThat(plan.getRecommendedTasks()).isEmpty();
        assertThat(plan.getSummary()).contains("没有");
        assertThat(plan.getRisks()).anyMatch(risk -> risk.contains("请先创建"));
        assertThat(plan.getCapacityPoints()).isEqualTo(20);
        verify(aiService, never()).chatWithQuota(anyLong(), any());
    }

    @Test
    void shouldUseValidAiSelectionAndItsRisks() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Issue first = issue(1L, "A", "HIGH", 2, null);
        Issue second = issue(2L, "B", "LOW", 3, null);
        when(issueMapper.selectList(any())).thenReturn(List.of(first, second));
        VelocityVO velocity = new VelocityVO();
        velocity.setAverageCompletedPoints(20.0);
        when(deliveryAnalyticsService.getVelocity(7L, 6)).thenReturn(velocity);
        when(aiService.chatWithQuota(anyLong(), any())).thenReturn(AiChatResponse.of(
                "{\"summary\":\"优先高优先级\",\"recommendedIssueIds\":[1],"
                        + "\"taskReasons\":{\"1\":\"高优先级且依赖就绪\"},\"risks\":[\"容量充足\"]}"));

        SprintPlanVO plan = service().suggestPlan(7L, null, 5, 9L);

        assertThat(plan.getAiAvailable()).isTrue();
        assertThat(plan.getSummary()).isEqualTo("优先高优先级");
        assertThat(plan.getRecommendedTasks()).hasSize(1);
        assertThat(plan.getRecommendedTasks().get(0).getReason()).isEqualTo("高优先级且依赖就绪");
        assertThat(plan.getRisks()).contains("容量充足");
    }

    @Test
    void shouldFallbackWhenAiReturnsUnknownIssueIds() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Issue first = issue(1L, "A", "HIGH", 2, null);
        when(issueMapper.selectList(any())).thenReturn(List.of(first));
        VelocityVO velocity = new VelocityVO();
        velocity.setAverageCompletedPoints(20.0);
        when(deliveryAnalyticsService.getVelocity(7L, 6)).thenReturn(velocity);
        when(aiService.chatWithQuota(anyLong(), any())).thenReturn(AiChatResponse.of(
                "{\"summary\":\"幻觉\",\"recommendedIssueIds\":[999]}"));

        SprintPlanVO plan = service().suggestPlan(7L, null, 3, 9L);

        assertThat(plan.getAiAvailable()).isFalse();
        assertThat(plan.getSummary()).contains("回退为规则建议");
        assertThat(plan.getRecommendedTasks()).extracting("id").containsExactly(1L);
    }

    @Test
    void shouldRecommendBlockedCandidatesWithDependencyWarning() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Issue blocked = issue(1L, "A", "HIGH", 2, null);
        blocked.setDependencies("[2]");
        Issue unfinishedDependency = issue(2L, "B", "LOW", 1, 30L);
        when(issueMapper.selectList(any())).thenReturn(List.of(blocked, unfinishedDependency));
        VelocityVO velocity = new VelocityVO();
        velocity.setAverageCompletedPoints(20.0);
        when(deliveryAnalyticsService.getVelocity(7L, 6)).thenReturn(velocity);
        when(aiService.chatWithQuota(anyLong(), any())).thenReturn(AiChatResponse.error("offline"));

        SprintPlanVO plan = service().suggestPlan(7L, null, 3, 9L);

        assertThat(plan.getRecommendedTasks()).hasSize(1);
        assertThat(plan.getRecommendedTasks().get(0).getBlocked()).isTrue();
        assertThat(plan.getRecommendedTasks().get(0).getReason()).contains("当前无依赖就绪项");
        assertThat(plan.getRisks()).anyMatch(risk -> risk.contains("未完成前置依赖"));
    }

    @Test
    void shouldRejectPlanningSprintOfAnotherProject() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Sprint sprint = sprint();
        sprint.setProjectId(8L);
        when(sprintMapper.selectById(20L)).thenReturn(sprint);

        assertThatThrownBy(() -> service().suggestPlan(7L, 20L, 10, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该项目");
    }

    @Test
    void shouldRejectApplyingToNonPlanningSprint() {
        Sprint sprint = sprint();
        sprint.setStatus("ACTIVE");
        when(sprintMapper.selectById(20L)).thenReturn(sprint);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);

        assertThatThrownBy(() -> service().applyPlan(20L, List.of(1L), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规划中");

        verify(issueMapper, never()).updateById(any());
    }

    @Test
    void shouldRejectApplyingIssueAlreadyInAnotherSprint() {
        when(sprintMapper.selectById(20L)).thenReturn(sprint());
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        Issue busy = issue(1L, "A", "HIGH", 2, 30L);
        when(issueMapper.selectBatchIds(any())).thenReturn(List.of(busy));

        assertThatThrownBy(() -> service().applyPlan(20L, List.of(1L), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他 Sprint");
    }

    private SprintPlanningService service() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        // 越权判定走真实 ProjectAccessService（BUG-117），成员/管理员规则才真的被测到
        ProjectAccessService projectAccessService = new ProjectAccessService(projectMapper, issueMapper,
                userMapper, teamService, new AccessControlService(roleService, userMapper, teamMemberMapper));
        return new SprintPlanningService(projectMapper, sprintMapper, issueMapper,
                teamService, projectAccessService, deliveryAnalyticsService, aiService, mapper);
    }

    private Project project() {
        Project project = new Project();
        project.setId(7L);
        project.setTeamId(3L);
        project.setName("Mimo");
        return project;
    }

    private Sprint sprint() {
        Sprint sprint = new Sprint();
        sprint.setId(20L);
        sprint.setProjectId(7L);
        sprint.setName("Sprint 20");
        sprint.setStatus("PLANNING");
        sprint.setStartDate(LocalDate.now().plusDays(1));
        sprint.setEndDate(LocalDate.now().plusDays(14));
        return sprint;
    }

    private Issue issue(Long id, String key, String priority,
                        Integer points, Long sprintId) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(7L);
        issue.setIssueKey(key);
        issue.setTitle("Task " + id);
        issue.setType("TASK");
        issue.setStatus("TODO");
        issue.setPriority(priority);
        issue.setStoryPoints(points);
        issue.setSprintId(sprintId);
        return issue;
    }
}
