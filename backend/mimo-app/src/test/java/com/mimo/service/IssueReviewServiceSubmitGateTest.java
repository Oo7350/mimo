package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BUG-115 的回归：管理员在别人项目里"一操作就被当成普通成员"。
 *
 * <p>用户原话是"管理员账号点击全员工作流进入成员的项目面板后，进行操作怎么是成员的逻辑"——
 * 界面里真的写着「只有任务负责人（lisi）可以提交验收」，因为提交验收这条规则
 * 只认负责人，而同一个类里的"验收"分支却留了管理员逃生口。两套口径并存。</p>
 *
 * <p>这里锁住四条：负责人能提交、普通成员不能替别人提交、系统管理员能代为提交、
 * 项目负责人（非系统管理员）也能代为提交。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueReviewServiceSubmitGateTest {

    private static final long ISSUE = 108L;
    private static final long PROJECT = 24L;
    private static final long TEAM = 1L;
    private static final long ASSIGNEE = 3L;   // lisi
    private static final long OTHER = 4L;      // 另一个普通成员
    private static final long OWNER = 5L;      // 项目负责人
    private static final long SYS_ADMIN = 1L;  // 系统管理员

    @Mock private IssueReviewMapper issueReviewMapper;
    @Mock private IssueMapper issueMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private UserMapper userMapper;
    @Mock private ActivityLogMapper activityLogMapper;
    @Mock private BoardColumnMapper boardColumnMapper;
    @Mock private NotificationService notificationService;
    @Mock private WebSocketService webSocketService;
    @Mock private TeamService teamService;
    @Mock private DeliveryAnalyticsService deliveryAnalyticsService;
    @Mock private ObjectMapper objectMapper;
    @Mock private AccessControlService accessControlService;
    @Mock private ProjectAccessService projectAccessService;

    @InjectMocks private IssueReviewService service;

    private Issue assignedIssue() {
        Issue issue = new Issue();
        issue.setId(ISSUE);
        issue.setProjectId(PROJECT);
        issue.setAssigneeId(ASSIGNEE);
        issue.setReporterId(SYS_ADMIN);
        return issue;
    }

    private void givenProjectOwnedBy(Long ownerId) {
        Project project = new Project();
        project.setId(PROJECT);
        project.setTeamId(TEAM);
        project.setOwnerId(ownerId);
        lenient().when(projectMapper.selectById(anyLong())).thenReturn(project);
    }

    @BeforeEach
    void setUp() {
        givenProjectOwnedBy(OWNER);
        // 默认：谁都不是系统管理员、谁都不是团队管理员，需要时单独打开
        lenient().when(accessControlService.isSystemAdmin(anyLong())).thenReturn(false);
        lenient().when(teamService.isTeamAdmin(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void assigneeCanSubmit() {
        assertThatCode(() -> service.assertCanSubmit(assignedIssue(), ASSIGNEE))
                .doesNotThrowAnyException();
    }

    @Test
    void plainMemberCannotSubmitForSomeoneElse() {
        assertThatThrownBy(() -> service.assertCanSubmit(assignedIssue(), OTHER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有任务负责人或管理员可以提交验收");
    }

    @Test
    void systemAdminCanSubmitOnBehalf() {
        // 用户在全员任务流里点的就是这个场景：管理员替成员提交验收
        when(accessControlService.isSystemAdmin(SYS_ADMIN)).thenReturn(true);

        assertThatCode(() -> service.assertCanSubmit(assignedIssue(), SYS_ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void projectOwnerCanSubmitOnBehalf() {
        assertThatCode(() -> service.assertCanSubmit(assignedIssue(), OWNER))
                .doesNotThrowAnyException();
    }

    @Test
    void teamAdminCanSubmitOnBehalf() {
        when(teamService.isTeamAdmin(TEAM, OTHER)).thenReturn(true);

        assertThatCode(() -> service.assertCanSubmit(assignedIssue(), OTHER))
                .doesNotThrowAnyException();
    }

    @Test
    void unassignedIssueAllowsReporterButNotStranger() {
        Issue unassigned = assignedIssue();
        unassigned.setAssigneeId(null);

        assertThatCode(() -> service.assertCanSubmit(unassigned, SYS_ADMIN))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertCanSubmit(unassigned, OTHER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未指派任务只能由报告人或管理员提交验收");
    }

    /** 读得到、写不了，是最让人困惑的那种：门槛必须与 ProjectAccessService 同一个口径 */
    @Test
    void projectGateDelegatesToProjectAccessService() {
        Issue issue = assignedIssue();
        mockProjectAccessOk();

        service.assertProjectMember(issue, OTHER);

        // 交给统一入口去判定，不再自己查 team_members
        org.mockito.Mockito.verify(projectAccessService).requireProject(PROJECT, OTHER);
    }

    private void mockProjectAccessOk() {
        // requireProject 返回 Project（不是 void），所以用 thenReturn 而不是 doNothing
        Project project = new Project();
        project.setId(PROJECT);
        project.setTeamId(TEAM);
        lenient().when(projectAccessService.requireProject(any(), any())).thenReturn(project);
    }

}
