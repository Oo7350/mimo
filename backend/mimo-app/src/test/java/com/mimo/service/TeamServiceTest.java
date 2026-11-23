package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Project;
import com.mimo.entity.Team;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserLevelMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KNOWN-005 回归：删团队只删 teams/team_members 时，团队下的项目会变成孤儿——
 * 项目列表按团队过滤，这些项目谁都看不见也删不掉。删除团队必须连带清掉项目。
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamMapper teamMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ActivityLogMapper activityLogMapper;
    @Mock private UserLevelMapper userLevelMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectService projectService;
    @Mock private RoleService roleService;

    private TeamService service() {
        return new TeamService(teamMapper, teamMemberMapper, userMapper, activityLogMapper,
                userLevelMapper, projectMapper, projectService,
                new AccessControlService(roleService, userMapper, teamMemberMapper));
    }

    private Team teamOwnedBy(long ownerId) {
        Team team = new Team();
        team.setId(7L);
        team.setName("研发一队");
        team.setOwnerId(ownerId);
        return team;
    }

    private Project project(long id) {
        Project p = new Project();
        p.setId(id);
        p.setTeamId(7L);
        p.setName("Mimo-" + id);
        return p;
    }

    @Test
    void deleteTeamShouldAlsoDeleteItsProjects() {
        when(teamMapper.selectById(7L)).thenReturn(teamOwnedBy(1L));
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");
        when(userMapper.selectById(1L)).thenReturn(owner);
        when(projectMapper.selectList(any())).thenReturn(List.of(project(11L), project(12L)));

        service().deleteTeam(7L, 1L);

        // 两个项目各自被级联删除，之后才删成员关系和团队本身
        verify(projectService, times(2)).deleteProject(anyLong(), any());
        verify(projectService).deleteProject(11L, 1L);
        verify(projectService).deleteProject(12L, 1L);
        verify(teamMemberMapper).delete(any());
        verify(teamMapper).deleteById(7L);
    }

    @Test
    void deleteTeamWithoutProjectsShouldNotTouchProjectService() {
        when(teamMapper.selectById(7L)).thenReturn(teamOwnedBy(1L));
        when(userMapper.selectById(1L)).thenReturn(null);
        when(projectMapper.selectList(any())).thenReturn(List.of());

        service().deleteTeam(7L, 1L);

        verify(projectService, never()).deleteProject(anyLong(), any());
        verify(teamMapper).deleteById(7L);
    }

    @Test
    void onlyOwnerCanDeleteTeam() {
        when(teamMapper.selectById(7L)).thenReturn(teamOwnedBy(1L));

        assertThatThrownBy(() -> service().deleteTeam(7L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅团队所有者");

        // 非所有者连项目都不该碰
        verify(projectService, never()).deleteProject(anyLong(), any());
        verify(teamMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteTeamShouldRejectMissingTeam() {
        when(teamMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service().deleteTeam(404L, 1L))
                .isInstanceOf(BusinessException.class);
    }
}
