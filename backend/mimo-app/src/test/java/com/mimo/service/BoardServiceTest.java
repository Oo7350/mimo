package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.dto.BoardDTO.BoardVO;
import com.mimo.dto.BoardDTO.ColumnVO;
import com.mimo.dto.BoardDTO.CreateColumnRequest;
import com.mimo.dto.BoardDTO.UpdateColumnRequest;
import com.mimo.entity.ActivityLog;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueLabel;
import com.mimo.entity.IssueReview;
import com.mimo.entity.User;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.IssueLabelMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardColumnMapper boardColumnMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private IssueLabelMapper issueLabelMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ActivityLogMapper activityLogMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WebSocketService webSocketService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private IssueReviewService issueReviewService;
    @Mock
    private DeliveryAnalyticsService deliveryAnalyticsService;

    @Test
    void shouldMoveStoryAndSyncGenericStatus() {
        Issue issue = issue("STORY");
        when(issueMapper.selectById(10L)).thenReturn(issue);
        when(boardColumnMapper.selectById(2L)).thenReturn(column(2L, "进行中"));
        when(boardColumnMapper.selectById(1L)).thenReturn(column(1L, "待办"));
        when(workflowService.validateTransition(issue, 1L, 2L, 9L)).thenReturn(true);
        when(userMapper.selectById(9L)).thenReturn(user(9L, "张三"));

        service().moveIssue(10L, 2L, 5, 9L);

        assertThat(issue.getColumnId()).isEqualTo(2L);
        assertThat(issue.getSortOrder()).isEqualTo(5);
        assertThat(issue.getStatus()).isEqualTo("IN_PROGRESS");
        verify(issueMapper).updateById(issue);
        verify(workflowService).validateTransition(issue, 1L, 2L, 9L);
        verify(deliveryAnalyticsService).refreshTodaySnapshot(3L);
        verify(webSocketService).sendBoardUpdate(eq(3L), any());

        ArgumentCaptor<ActivityLog> log = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogMapper).insert(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("MOVE");
        assertThat(log.getValue().getDetail()).contains("待办").contains("进行中");
    }

    @Test
    void shouldBlockMoveToDoneWhenLatestReviewIsNotApproved() {
        Issue issue = issue("STORY");
        when(issueMapper.selectById(10L)).thenReturn(issue);
        when(boardColumnMapper.selectById(2L)).thenReturn(column(2L, "已完成"));
        when(workflowService.validateTransition(issue, 1L, 2L, 9L)).thenReturn(true);
        IssueReview pending = new IssueReview();
        pending.setStatus("PENDING");
        when(issueReviewService.latest(10L)).thenReturn(pending);

        assertThatThrownBy(() -> service().moveIssue(10L, 2L, 0, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验收");

        verify(issueMapper, never()).updateById(any());
        verify(activityLogMapper, never()).insert(any());
    }

    @Test
    void shouldRejectIllegalBugStatusRollback() {
        Issue issue = issue("BUG");
        issue.setBugStatus("RESOLVED");
        when(issueMapper.selectById(10L)).thenReturn(issue);
        when(boardColumnMapper.selectById(2L)).thenReturn(column(2L, "待办"));
        when(workflowService.validateTransition(issue, 1L, 2L, 9L)).thenReturn(true);

        assertThatThrownBy(() -> service().moveIssue(10L, 2L, 0, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能将缺陷");

        verify(issueMapper, never()).updateById(any());
    }

    @Test
    void shouldCloseVerifiedBugAndMapGenericStatusToDone() {
        Issue issue = issue("BUG");
        issue.setBugStatus("VERIFIED");
        when(issueMapper.selectById(10L)).thenReturn(issue);
        when(boardColumnMapper.selectById(2L)).thenReturn(column(2L, "已完成"));
        when(boardColumnMapper.selectById(1L)).thenReturn(column(1L, "待办"));
        when(workflowService.validateTransition(issue, 1L, 2L, 9L)).thenReturn(true);
        when(issueReviewService.latest(10L)).thenReturn(null);
        when(userMapper.selectById(9L)).thenReturn(user(9L, "李四"));

        service().moveIssue(10L, 2L, 0, 9L);

        assertThat(issue.getBugStatus()).isEqualTo("CLOSED");
        assertThat(issue.getStatus()).isEqualTo("DONE");
        verify(issueMapper).updateById(issue);
    }

    @Test
    void shouldRefuseDeletingColumnThatStillHasIssues() {
        when(issueMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service().deleteColumn(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有任务");

        verify(boardColumnMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldGroupBoardIssuesByColumnAndSortByOrder() {
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(column(1L, "待办"), column(2L, "进行中")));
        Issue second = issue(11L, 1L, 1);
        Issue first = issue(12L, 1L, 0);
        Issue third = issue(13L, 2L, 0);
        when(issueMapper.selectList(any())).thenReturn(List.of(second, first, third));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(7L, "张三"), user(8L, "李四")));
        IssueLabel label = new IssueLabel();
        label.setIssueId(11L);
        label.setLabel("性能");
        label.setColor("#f56c6c");
        when(issueLabelMapper.selectList(any())).thenReturn(List.of(label));

        BoardVO board = service().getBoard(3L);

        assertThat(board.getColumns()).hasSize(2);
        ColumnVO todo = board.getColumns().get(0);
        assertThat(todo.getIssues()).extracting(i -> i.getId()).containsExactly(12L, 11L);
        assertThat(todo.getIssues().get(1).getLabels()).hasSize(1);
        assertThat(board.getColumns().get(1).getIssues()).hasSize(1);
    }

    @Test
    void shouldCreateColumnWithDefaultColorAndNextSortOrder() {
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(column(1L, "待办"), column(2L, "进行中")));
        CreateColumnRequest request = new CreateColumnRequest();
        request.setProjectId(3L);
        request.setName("待验收");

        BoardColumn created = service().createColumn(request);

        assertThat(created.getColor()).isEqualTo("#409EFF");
        assertThat(created.getSortOrder()).isEqualTo(1);
        assertThat(created.getProjectId()).isEqualTo(3L);
        verify(boardColumnMapper).insert(created);
    }

    @Test
    void shouldRejectUpdatingMissingColumn() {
        when(boardColumnMapper.selectById(99L)).thenReturn(null);
        UpdateColumnRequest request = new UpdateColumnRequest();
        request.setId(99L);
        request.setName("新列名");

        assertThatThrownBy(() -> service().updateColumn(request))
                .isInstanceOf(BusinessException.class);

        verify(boardColumnMapper, never()).updateById(any());
    }

    @Test
    void shouldRewriteSortOrderByGivenSequence() {
        BoardColumn first = column(1L, "待办");
        first.setSortOrder(5);
        BoardColumn second = column(2L, "进行中");
        second.setSortOrder(9);
        when(boardColumnMapper.selectById(2L)).thenReturn(second);
        when(boardColumnMapper.selectById(1L)).thenReturn(first);

        service().sortColumns(List.of(2L, 1L));

        assertThat(second.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
        verify(boardColumnMapper, org.mockito.Mockito.times(2)).updateById(any());
    }

    private BoardService service() {
        return new BoardService(boardColumnMapper, issueMapper, issueLabelMapper, userMapper,
                activityLogMapper, notificationService, webSocketService, workflowService,
                issueReviewService, deliveryAnalyticsService);
    }

    private Issue issue(String type) {
        Issue issue = new Issue();
        issue.setId(10L);
        issue.setProjectId(3L);
        issue.setColumnId(1L);
        issue.setSortOrder(0);
        issue.setIssueKey("MIMO-10");
        issue.setType(type);
        issue.setStatus("TODO");
        return issue;
    }

    private Issue issue(Long id, Long columnId, Integer sortOrder) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(3L);
        issue.setColumnId(columnId);
        issue.setSortOrder(sortOrder);
        issue.setIssueKey("MIMO-" + id);
        issue.setType("TASK");
        issue.setStatus("TODO");
        issue.setAssigneeId(id % 2 == 0 ? 7L : 8L);
        return issue;
    }

    private BoardColumn column(Long id, String name) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setName(name);
        column.setSortOrder(0);
        return column;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
