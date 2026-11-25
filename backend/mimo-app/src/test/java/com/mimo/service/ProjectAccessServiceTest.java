package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectAccessServiceTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TeamService teamService;
    @Mock
    private RoleService roleService;
    @Mock
    private com.mimo.mapper.TeamMemberMapper teamMemberMapper;

    /** 默认：都是普通成员、都不在成员表里 —— 各测试再按需覆盖 */
    @BeforeEach
    void baseStubs() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(any())).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(anyLong(), anyLong())).thenReturn(false);
        when(teamService.isTeamAdmin(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void shouldRejectUserOutsideProjectTeam() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(3L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service().requireProject(7L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void shouldAllowSystemAdminOutsideTeam() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_ADMIN"));

        Project result = service().requireProject(7L, 9L);

        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    void shouldCheckIssueProjectAccess() {
        Issue issue = new Issue();
        issue.setId(42L);
        issue.setProjectId(7L);
        when(issueMapper.selectById(42L)).thenReturn(issue);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);

        assertThat(service().requireIssue(42L, 9L).getId()).isEqualTo(42L);
    }

    /**
     * BUG-115 / BUG-117：各 Controller/Service 以前自己写 teamService.isTeamMember(...)，
     * 把管理员挡在门外。这三个判定方法是唯一口径，下面几条锁住它们。
     */
    @Test
    void canAccessProjectAllowsSystemAdminOutsideTeam() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_ADMIN"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(3L, 9L)).thenReturn(false);

        assertThat(service().canAccessProject(7L, 9L)).isTrue();
    }

    @Test
    void canAccessProjectAllowsTeamAdminWhoIsNotMember() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(3L, 9L)).thenReturn(true);

        assertThat(service().canAccessProject(7L, 9L)).isTrue();
    }

    @Test
    void canAccessProjectRejectsOutsider() {
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(3L, 9L)).thenReturn(false);

        assertThat(service().canAccessProject(7L, 9L)).isFalse();
    }

    @Test
    void canAccessProjectIsFalseWhenProjectMissing() {
        when(projectMapper.selectById(7L)).thenReturn(null);

        assertThat(service().canAccessProject(7L, 9L)).isFalse();
    }

    @Test
    void canAccessTeamFollowsSameRule() {
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(3L, 9L)).thenReturn(false);
        assertThat(service().canAccessTeam(3L, 9L)).isFalse();

        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        assertThat(service().canAccessTeam(3L, 9L)).isTrue();
    }

    @Test
    void canManageProjectRequiresManagementLevel() {
        Project owned = project();
        owned.setOwnerId(9L);
        when(projectMapper.selectById(7L)).thenReturn(owned);
        when(userMapper.selectById(9L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 9L)).thenReturn(true);
        when(teamService.isTeamAdmin(3L, 9L)).thenReturn(false);
        // 项目负责人能管，但只是普通成员时不能管别人的项目
        assertThat(service().canManageProject(7L, 9L)).isTrue();

        when(userMapper.selectById(10L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamMember(3L, 10L)).thenReturn(true);
        assertThat(service().canManageProject(7L, 10L)).isFalse();

        when(userMapper.selectById(11L)).thenReturn(user("ROLE_MEMBER"));
        when(teamService.isTeamAdmin(3L, 11L)).thenReturn(true);
        assertThat(service().canManageProject(7L, 11L)).isTrue();
    }

    private ProjectAccessService service() {
        return new ProjectAccessService(projectMapper, issueMapper, userMapper, teamService,
                new AccessControlService(roleService, userMapper, teamMemberMapper));
    }

    private Project project() {
        Project project = new Project();
        project.setId(7L);
        project.setTeamId(3L);
        return project;
    }

    private User user(String role) {
        User user = new User();
        user.setId(9L);
        user.setRole(role);
        return user;
    }
}
