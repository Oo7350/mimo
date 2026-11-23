package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
