package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.dto.IssueGovernanceDTO.ImpactVO;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueChangeRequest;
import com.mimo.entity.Sprint;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.SprintMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueImpactServiceTest {

    @Mock
    private IssueMapper issueMapper;

    @Mock
    private SprintMapper sprintMapper;

    @Test
    void shouldAggregateSubtasksDependentsAndSprintRisk() {
        Issue root = issue(1L, "STORY", "登录改版", "IN_PROGRESS", 5,
                "2026-12-10", 1L, null);
        Issue completedChild = issue(2L, "TASK", "登录页 UI", "DONE", 3,
                "2026-12-08", 1L, 1L);
        Issue downstream = issue(3L, "TASK", "权限联调", "TODO", 2,
                "2026-12-12", 2L, null);
        downstream.setDependencies("[1]");
        Issue newDependency = issue(4L, "STORY", "账号中心", "TODO", 8,
                "2026-12-20", null, null);
        when(issueMapper.selectList(any())).thenReturn(
                List.of(root, completedChild, downstream, newDependency));

        Sprint activeSprint = sprint(1L, "Sprint 12", "ACTIVE", 1);
        Sprint nextSprint = sprint(2L, "Sprint 13", "PLANNING", 0);
        when(sprintMapper.selectBatchIds(any())).thenReturn(List.of(activeSprint, nextSprint));

        IssueChangeRequest request = request("""
                {
                  "storyPoints": 8,
                  "dueDate": "2026-12-05",
                  "dependencies": [1, 4],
                  "acceptanceCriteria": []
                }
                """);

        ImpactVO impact = service().analyze(request, root);

        assertThat(impact.getAffectedIssueCount()).isEqualTo(3);
        assertThat(impact.getAffectedTaskCount()).isEqualTo(2);
        assertThat(impact.getCompletedTaskCount()).isEqualTo(1);
        assertThat(impact.getStoryPointDelta()).isEqualTo(3);
        assertThat(impact.getDueDateDeltaDays()).isEqualTo(-5);
        assertThat(impact.getActiveSprintAffected()).isTrue();
        assertThat(impact.getAffectedSprints()).extracting("id")
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(impact.getAffectedIssues()).extracting("id")
                .containsExactlyInAnyOrder(2L, 3L, 4L);
        assertThat(impact.getRiskLevel()).isEqualTo("HIGH");
        assertThat(impact.getRiskReasons()).anyMatch(reason -> reason.contains("故事点由 5 增至 8"));
        assertThat(impact.getRiskReasons()).anyMatch(reason -> reason.contains("已完成工作项"));
        assertThat(impact.getRiskReasons()).anyMatch(reason -> reason.contains("验收标准"));
    }

    @Test
    void shouldKeepDescriptionOnlyChangeLowRisk() {
        Issue root = issue(10L, "STORY", "筛选体验", "IN_PROGRESS", 3,
                "2026-12-18", null, null);
        when(issueMapper.selectList(any())).thenReturn(List.of(root));

        IssueChangeRequest request = request("""
                { "description": "补充背景说明，不改变交付范围" }
                """);

        ImpactVO impact = service().analyze(request, root);

        assertThat(impact.getAffectedIssueCount()).isZero();
        assertThat(impact.getStoryPointDelta()).isZero();
        assertThat(impact.getAffectedSprints()).isEmpty();
        assertThat(impact.getRiskLevel()).isEqualTo("LOW");
        assertThat(impact.getRiskReasons()).anyMatch(reason -> reason.contains("低影响"));
    }

    private IssueImpactService service() {
        return new IssueImpactService(issueMapper, sprintMapper, new ObjectMapper());
    }

    private Issue issue(Long id, String type, String title, String status, Integer storyPoints,
                        String dueDate, Long sprintId, Long parentId) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(100L);
        issue.setIssueKey("MIMO-" + id);
        issue.setType(type);
        issue.setTitle(title);
        issue.setStatus(status);
        issue.setStoryPoints(storyPoints);
        issue.setDueDate(LocalDate.parse(dueDate));
        issue.setSprintId(sprintId);
        issue.setParentId(parentId);
        return issue;
    }

    private Sprint sprint(Long id, String name, String status, Integer isActive) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setName(name);
        sprint.setStatus(status);
        sprint.setIsActive(isActive);
        return sprint;
    }

    private IssueChangeRequest request(String proposedChanges) {
        IssueChangeRequest request = new IssueChangeRequest();
        request.setId(200L);
        request.setIssueId(1L);
        request.setProposedChanges(proposedChanges);
        return request;
    }
}
