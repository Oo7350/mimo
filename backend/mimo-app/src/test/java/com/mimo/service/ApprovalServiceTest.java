package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.entity.ApprovalRequest;
import com.mimo.entity.User;
import com.mimo.mapper.ApprovalRequestMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KNOWN-006 回归：审批通过后执行失败以前被 {@code e.printStackTrace()} 吞掉——
 * 审批显示「已通过」，项目却没建出来，也没有任何痕迹可查、无法重试。
 * 现在执行结果必须落到 exec_status / exec_error / exec_attempts 上，并可重试。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private ApprovalRequestMapper approvalRequestMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private TeamMapper teamMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ApprovalExecutionService approvalExecutionService;
    @Mock private NotificationService notificationService;

    private ApprovalService service() {
        return new ApprovalService(approvalRequestMapper, teamMemberMapper, userMapper,
                teamMapper, projectMapper, approvalExecutionService, notificationService);
    }

    private ApprovalRequest request(String status) {
        ApprovalRequest r = new ApprovalRequest();
        r.setId(1L);
        r.setTeamId(2L);
        r.setRequesterId(3L);
        r.setTitle("新建项目：Mimo");
        r.setTargetType("PROJECT_CREATE");
        r.setDataJson("{\"name\":\"Mimo\",\"key\":\"MIMO\"}");
        r.setStatus(status);
        return r;
    }

    private User admin(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("admin");
        u.setRole("ROLE_ADMIN");
        return u;
    }

    @Test
    void approveShouldRecordFailureWhenExecutionFails() {
        ApprovalRequest req = request("PENDING");
        when(approvalRequestMapper.selectById(1L)).thenReturn(req);
        when(userMapper.selectById(9L)).thenReturn(admin(9L));
        doThrow(new BusinessException(ResultCode.CONFLICT, "项目Key已存在"))
                .when(approvalExecutionService).execute(any());

        service().approve(1L, 9L);

        // 审批本身确实通过了
        assertThat(req.getStatus()).isEqualTo("APPROVED");
        // 但执行失败必须留痕，而不是被吞掉
        assertThat(req.getExecStatus()).isEqualTo("FAILED");
        assertThat(req.getExecError()).contains("项目Key已存在");
        assertThat(req.getExecAttempts()).isEqualTo(1);
        assertThat(req.getLastExecAt()).isNotNull();
        // 首次通过 + 执行结果各写一次
        verify(approvalRequestMapper, times(2)).updateById(req);
    }

    @Test
    void approveShouldMarkSuccessWhenExecutionWorks() {
        ApprovalRequest req = request("PENDING");
        when(approvalRequestMapper.selectById(1L)).thenReturn(req);
        when(userMapper.selectById(9L)).thenReturn(admin(9L));

        service().approve(1L, 9L);

        assertThat(req.getStatus()).isEqualTo("APPROVED");
        assertThat(req.getExecStatus()).isEqualTo("SUCCESS");
        assertThat(req.getExecError()).isNull();
        assertThat(req.getExecAttempts()).isEqualTo(1);
    }

    @Test
    void retryShouldExecuteFailedApproval() {
        ApprovalRequest req = request("APPROVED");
        req.setExecStatus("FAILED");
        req.setExecError("数据库连接超时");
        req.setExecAttempts(1);
        when(approvalRequestMapper.selectById(1L)).thenReturn(req);
        when(userMapper.selectById(9L)).thenReturn(admin(9L));

        String status = service().retryExecution(1L, 9L);

        assertThat(status).isEqualTo("SUCCESS");
        assertThat(req.getExecStatus()).isEqualTo("SUCCESS");
        assertThat(req.getExecError()).isNull();
        assertThat(req.getExecAttempts()).isEqualTo(2);
    }

    @Test
    void retryShouldKeepFailureWhenItFailsAgain() {
        ApprovalRequest req = request("APPROVED");
        req.setExecStatus("FAILED");
        req.setExecAttempts(1);
        when(approvalRequestMapper.selectById(1L)).thenReturn(req);
        when(userMapper.selectById(9L)).thenReturn(admin(9L));
        doThrow(new BusinessException(ResultCode.CONFLICT, "项目Key已存在"))
                .when(approvalExecutionService).execute(any());

        String status = service().retryExecution(1L, 9L);

        assertThat(status).isEqualTo("FAILED");
        assertThat(req.getExecError()).contains("项目Key已存在");
        assertThat(req.getExecAttempts()).isEqualTo(2);
    }

    @Test
    void retryShouldRejectAlreadySucceededApproval() {
        ApprovalRequest req = request("APPROVED");
        req.setExecStatus("SUCCESS");
        when(approvalRequestMapper.selectById(1L)).thenReturn(req);
        when(userMapper.selectById(9L)).thenReturn(admin(9L));

        assertThatThrownBy(() -> service().retryExecution(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无需重试");
    }

    @Test
    void retryShouldRejectNonAdmin() {
        ApprovalRequest req = request("APPROVED");
        req.setExecStatus("FAILED");
        when(approvalRequestMapper.selectById(1L)).thenReturn(req);
        User member = new User();
        member.setId(9L);
        member.setRole("ROLE_MEMBER");
        when(userMapper.selectById(9L)).thenReturn(member);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().retryExecution(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有管理员");

        verify(approvalExecutionService, never()).execute(any());
    }
}
