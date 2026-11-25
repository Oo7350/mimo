package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WikiAttachmentMapper;
import com.mimo.mapper.WikiPageMapper;
import com.mimo.mapper.WikiVersionMapper;
import com.mimo.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiki 目录树的越权回归：目录里带标题/作者/时间，
 * 只要登着账号换个 projectId 就能列出别人项目的知识库结构属于信息泄漏。
 */
@ExtendWith(MockitoExtension.class)
class WikiServiceTest {

    @Mock
    private WikiPageMapper wikiPageMapper;
    @Mock
    private WikiVersionMapper wikiVersionMapper;
    @Mock
    private WikiAttachmentMapper wikiAttachmentMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TeamService teamService;
    @Mock
    private RoleService roleService;
    @Mock
    private com.mimo.mapper.TeamMemberMapper teamMemberMapper;
    @Mock
    private com.mimo.mapper.IssueMapper issueMapper;
    @Mock
    private JwtUtil jwtUtil;

    private WikiService service() {
        AccessControlService accessControlService =
                new AccessControlService(roleService, userMapper, teamMemberMapper);
        // 越权判定统一走 ProjectAccessService（BUG-117），这里用真实的那个，
        // 才能保证"非成员被拒、管理员放行"是真的在测规则，而不是测 mock
        ProjectAccessService projectAccessService =
                new ProjectAccessService(projectMapper, issueMapper, userMapper, teamService, accessControlService);
        return new WikiService(wikiPageMapper, wikiVersionMapper, wikiAttachmentMapper,
                projectMapper, userMapper, teamService, projectAccessService, accessControlService, jwtUtil);
    }

    @Test
    void treeShouldRejectNonMemberOfProjectTeam() {
        Project project = new Project();
        project.setId(1L);
        project.setTeamId(2L);
        when(projectMapper.selectById(1L)).thenReturn(project);

        User outsider = new User();
        outsider.setId(9L);
        outsider.setRole("ROLE_MEMBER");
        when(userMapper.selectById(9L)).thenReturn(outsider);
        when(teamService.isTeamMember(2L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service().getTree(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");

        // 越权被拦住之后不应该再去查目录内容
        verify(wikiPageMapper, never()).listTreeByProject(anyLong());
    }

    @Test
    void treeShouldRejectWhenProjectMissing() {
        when(projectMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service().getTree(404L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }
}
