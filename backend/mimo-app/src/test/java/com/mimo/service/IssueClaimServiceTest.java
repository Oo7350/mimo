package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.dto.IssueClaimResult;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueClaimServiceTest {

    @Mock private IssueMapper issueMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private BoardColumnMapper boardColumnMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ApprovalRequestMapper approvalRequestMapper;
    @Mock private NotificationService notificationService;
    @Mock private BoardService boardService;
    @Mock private ProjectAccessService projectAccessService;

    @Test
    void adminClaimAssignsAndMovesToInProgressDirectly() {
        Issue issue = issue(null);
        when(projectAccessService.requireIssue(1L, 9L)).thenReturn(issue);
        when(boardColumnMapper.selectList(any())).thenReturn(columns());
        when(boardColumnMapper.selectOne(any())).thenReturn(columns().get(0));
        when(userMapper.selectById(9L)).thenReturn(user(9L, "ROLE_ADMIN"));
        when(issueMapper.selectById(1L)).thenReturn(issue);

        IssueClaimResult result = service().claim(1L, 9L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(issue.getAssigneeId()).isEqualTo(9L);
        verify(issueMapper).updateById(issue);
        verify(boardService).moveIssue(1L, 20L, null, 9L);
        verify(approvalRequestMapper, never()).insert(any());
    }

    @Test
    void memberClaimCreatesApprovalInsteadOfAssigning() {
        Issue issue = issue(null);
        when(projectAccessService.requireIssue(1L, 9L)).thenReturn(issue);
        when(boardColumnMapper.selectList(any())).thenReturn(columns());
        when(userMapper.selectById(9L)).thenReturn(user(9L, "ROLE_MEMBER"));
        when(projectMapper.selectById(7L)).thenReturn(project(7L, 99L));
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(approvalRequestMapper.selectOne(any())).thenReturn(null);

        IssueClaimResult result = service().claim(1L, 9L);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getApprovalRequestId()).isNull(); // 未回写自增ID的stub场景
        verify(approvalRequestMapper).insert(any(ApprovalRequest.class));
        verify(issueMapper, never()).updateById(any());
        verify(boardService, never()).moveIssue(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void claimIsRejectedWhenTaskAlreadyAssignedToSomeoneElse() {
        Issue issue = issue(99L);
        when(projectAccessService.requireIssue(1L, 9L)).thenReturn(issue);
        when(userMapper.selectById(99L)).thenReturn(user(99L, "ROLE_MEMBER"));

        assertThatThrownBy(() -> service().claim(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("认领");
    }

    @Test
    void applyClaimAssignsAndMovesWhenApproved() {
        Issue issue = issue(null);
        when(issueMapper.selectById(1L)).thenReturn(issue);
        when(boardColumnMapper.selectOne(any())).thenReturn(columns().get(0));

        service().applyClaim(1L, 9L, 20L, 99L);

        assertThat(issue.getAssigneeId()).isEqualTo(9L);
        verify(boardService).moveIssue(1L, 20L, null, 99L);
    }

    private IssueClaimService service() {
        return new IssueClaimService(issueMapper, projectMapper, boardColumnMapper, teamMemberMapper,
                userMapper, approvalRequestMapper, notificationService, boardService,
                projectAccessService, new ObjectMapper());
    }

    private Issue issue(Long assigneeId) {
        Issue issue = new Issue();
        issue.setId(1L);
        issue.setProjectId(7L);
        issue.setType("TASK");
        issue.setTitle("测试任务");
        issue.setIssueKey("MIMO-1");
        issue.setColumnId(10L);
        issue.setAssigneeId(assigneeId);
        return issue;
    }

    private List<BoardColumn> columns() {
        BoardColumn todo = new BoardColumn();
        todo.setId(10L); todo.setName("待办"); todo.setSortOrder(0);
        BoardColumn doing = new BoardColumn();
        doing.setId(20L); doing.setName("进行中"); doing.setSortOrder(1);
        BoardColumn done = new BoardColumn();
        done.setId(30L); done.setName("已完成"); done.setSortOrder(2);
        return List.of(todo, doing, done);
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole(role);
        return user;
    }

    private Project project(Long id, Long ownerId) {
        Project project = new Project();
        project.setId(id);
        project.setTeamId(3L);
        project.setOwnerId(ownerId);
        return project;
    }
}
