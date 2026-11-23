package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Project;
import com.mimo.entity.Team;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.CommentMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.NotificationMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.ProjectMemberMapper;
import com.mimo.mapper.ReportMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WorkLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 演示数据生成器：既要真的铺满页面，又不能重复堆数据、不能被普通用户乱调。
 */
@ExtendWith(MockitoExtension.class)
class DemoDataServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private TeamMapper teamMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private BoardColumnMapper boardColumnMapper;
    @Mock private SprintMapper sprintMapper;
    @Mock private IssueMapper issueMapper;
    @Mock private IssueKeyAllocator issueKeyAllocator;
    @Mock private CommentMapper commentMapper;
    @Mock private WorkLogMapper workLogMapper;
    @Mock private IssueReviewMapper issueReviewMapper;
    @Mock private ReportMapper reportMapper;
    @Mock private ActivityLogMapper activityLogMapper;
    @Mock private NotificationMapper notificationMapper;
    @Mock private AccessControlService accessControlService;

    @Test
    void shouldRejectNonAdmin() {
        when(accessControlService.isSystemAdmin(9L)).thenReturn(false);

        assertThatThrownBy(() -> service().generate(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有系统管理员");

        verify(projectMapper, never()).insert(any());
    }

    @Test
    void shouldSkipWhenDemoDataAlreadyExists() {
        when(accessControlService.isSystemAdmin(1L)).thenReturn(true);
        Project existing = new Project();
        existing.setId(42L);
        existing.setName(DemoDataService.DEMO_PROJECT_NAME);
        when(projectMapper.selectOne(any())).thenReturn(existing);
        when(issueMapper.selectCount(any())).thenReturn(15L);

        Map<String, Object> result = service().generate(1L);

        assertThat(result.get("created")).isEqualTo(false);
        assertThat(result.get("projectId")).isEqualTo(42L);
        verify(projectMapper, never()).insert(any());
        verify(issueKeyAllocator, never()).insertWithAllocatedKey(any(), anyString());
    }

    @Test
    void shouldSeedProjectSprintsIssuesAndAReviewMeanwhileIsNotMine() {
        when(accessControlService.isSystemAdmin(1L)).thenReturn(true);
        when(projectMapper.selectOne(any())).thenReturn(null);

        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(admin);

        // 已有团队 + 两名成员（第二个成员用来"提交验收"，这样管理员的待办里才有东西）
        TeamMember mine = new TeamMember();
        mine.setTeamId(7L);
        mine.setUserId(1L);
        TeamMember other = new TeamMember();
        other.setTeamId(7L);
        other.setUserId(2L);
        when(teamMemberMapper.selectOne(any())).thenReturn(mine);
        Team team = new Team();
        team.setId(7L);
        team.setName("示例团队");
        when(teamMapper.selectById(7L)).thenReturn(team);
        when(teamMemberMapper.selectList(any())).thenReturn(List.of(mine, other));
        when(projectMemberMapper.selectCount(any())).thenReturn(0L);
        // 插入时自增主键：MyBatis-Plus 会给实体回填 id，这里用 answer 模拟
        when(projectMapper.insert(any(Project.class))).thenAnswer(inv -> {
            inv.<Project>getArgument(0).setId(100L);
            return 1;
        });
        when(boardColumnMapper.insert(any())).thenAnswer(inv -> {
            com.mimo.entity.BoardColumn c = inv.getArgument(0);
            c.setId(200L + c.getSortOrder());
            return 1;
        });
        when(sprintMapper.insert(any())).thenAnswer(inv -> {
            com.mimo.entity.Sprint s = inv.getArgument(0);
            s.setId(300L + Math.abs(s.getName().hashCode() % 10));
            return 1;
        });
        when(issueKeyAllocator.insertWithAllocatedKey(any(Issue.class), anyString())).thenAnswer(inv -> {
            Issue issue = inv.getArgument(0);
            issue.setId(400L + Math.abs(issue.getTitle().hashCode() % 1000));
            return "DEMO-1";
        });

        Map<String, Object> result = service().generate(1L);

        assertThat(result.get("created")).isEqualTo(true);
        assertThat((Integer) result.get("issueCount")).isEqualTo(15);

        // 15 个工作项、4 个看板列、3 个迭代
        verify(issueKeyAllocator, times(15)).insertWithAllocatedKey(any(Issue.class), anyString());
        verify(boardColumnMapper, times(4)).insert(any());
        verify(sprintMapper, times(3)).insert(any());

        // 验收记录里必须有"别人提交、状态 PENDING"的 —— 否则管理员工作台还是空的
        ArgumentCaptor<IssueReview> reviews = ArgumentCaptor.forClass(IssueReview.class);
        verify(issueReviewMapper, times(3)).insert(reviews.capture());
        assertThat(reviews.getAllValues())
                .anyMatch(r -> "PENDING".equals(r.getStatus()) && !Long.valueOf(1L).equals(r.getSubmitterId()));

        // 评论、工时、报告、动态、通知都不能是空手
        verify(commentMapper, times(5)).insert(any());
        verify(workLogMapper, times(6)).insert(any());
        verify(reportMapper, times(2)).insert(any());
        verify(activityLogMapper, times(5)).insert(any());
        verify(notificationMapper, times(2)).insert(any());
    }

    @Test
    void shouldPickAFreeProjectKeyBecauseProjectKeyIsGloballyUnique() {
        // 踩过的坑：第一版硬写 "DEMO"，demo 项目本身占着 DEMO，一点就 500（Duplicate entry）
        when(projectMapper.selectList(any())).thenReturn(List.of(projectWithKey("DEMO"), projectWithKey("DEMO2")));

        assertThat(service().allocateProjectKey()).isEqualTo("DEMO3");
    }

    private Project projectWithKey(String key) {
        Project project = new Project();
        project.setKey(key);
        return project;
    }

    private DemoDataService service() {
        return new DemoDataService(userMapper, teamMapper, teamMemberMapper, projectMapper,
                projectMemberMapper, boardColumnMapper, sprintMapper, issueMapper, issueKeyAllocator,
                commentMapper, workLogMapper, issueReviewMapper, reportMapper, activityLogMapper,
                notificationMapper, accessControlService);
    }
}
