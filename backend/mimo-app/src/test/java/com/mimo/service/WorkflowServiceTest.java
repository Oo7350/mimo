package com.mimo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.dto.WorkflowDTO.AvailableTransitionVO;
import com.mimo.dto.WorkflowDTO.SaveWorkflowRequest;
import com.mimo.dto.WorkflowDTO.WorkflowConfig;
import com.mimo.dto.WorkflowDTO.WorkflowNode;
import com.mimo.dto.WorkflowDTO.WorkflowTransition;
import com.mimo.dto.WorkflowDTO.WorkflowVO;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.entity.Workflow;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WorkflowMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private WorkflowMapper workflowMapper;
    @Mock
    private BoardColumnMapper boardColumnMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleService roleService;

    @Test
    void shouldAllowAnyTransitionWhenProjectHasNoWorkflow() {
        when(workflowMapper.selectOne(any())).thenReturn(null);

        boolean allowed = service().validateTransition(issue(1L, 2L), 10L, 20L, 99L);

        assertThat(allowed).isTrue();
        verify(boardColumnMapper, never()).selectById(anyLong());
    }

    @Test
    void shouldAllowMoveInsideSameColumn() {
        when(workflowMapper.selectOne(any())).thenReturn(workflow());

        boolean allowed = service().validateTransition(issue(1L, 2L), 10L, 10L, 99L);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectTransitionNotDefinedInWorkflow() {
        when(workflowMapper.selectOne(any())).thenReturn(workflow());
        when(boardColumnMapper.selectById(10L)).thenReturn(column("待办"));
        when(boardColumnMapper.selectById(30L)).thenReturn(column("已上线"));

        assertThatThrownBy(() -> service().validateTransition(issue(10L, 30L), 10L, 30L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许")
                .hasMessageContaining("待办")
                .hasMessageContaining("已上线");
    }

    @Test
    void shouldAllowAssigneeToPassAssigneeCondition() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithConditions(List.of("ASSIGNEE")));
        Issue issue = issue(10L, 20L);
        issue.setAssigneeId(99L);

        boolean allowed = service().validateTransition(issue, 10L, 20L, 99L);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectNonAssigneeWhenOnlyAssigneeConditionMatches() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithConditions(List.of("ASSIGNEE")));
        Issue issue = issue(10L, 20L);
        issue.setAssigneeId(99L);
        issue.setReporterId(100L);

        assertThatThrownBy(() -> service().validateTransition(issue, 10L, 20L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条件");
    }

    @Test
    void shouldAllowReporterToPassReporterCondition() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithConditions(List.of("REPORTER")));
        Issue issue = issue(10L, 20L);
        issue.setReporterId(100L);

        assertThat(service().validateTransition(issue, 10L, 20L, 100L)).isTrue();
    }

    @Test
    void shouldTreatProjectOwnerAsAdminForRoleAdminCondition() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithConditions(List.of("ROLE_ADMIN")));
        when(userMapper.selectById(99L)).thenReturn(user(99L, "ROLE_MEMBER"));
        when(projectMapper.selectById(7L)).thenReturn(project(7L, 99L));

        assertThat(service().validateTransition(issue(10L, 20L), 10L, 20L, 99L)).isTrue();
    }

    @Test
    void shouldRejectNonAdminWhenRoleAdminConditionRequired() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithConditions(List.of("ROLE_ADMIN")));
        when(userMapper.selectById(100L)).thenReturn(user(100L, "ROLE_MEMBER"));
        when(projectMapper.selectById(7L)).thenReturn(project(7L, 99L));
        when(teamMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().validateTransition(issue(10L, 20L), 10L, 20L, 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldExposeConfiguredTransitionsForCurrentColumn() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithConditions(List.of("ASSIGNEE")));
        when(boardColumnMapper.selectById(20L)).thenReturn(column("进行中"));

        List<AvailableTransitionVO> transitions = service().getAvailableTransitions(7L, "STORY", 10L);

        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).getToColumnId()).isEqualTo(20L);
        assertThat(transitions.get(0).getToColumnName()).isEqualTo("进行中");
        assertThat(transitions.get(0).getConditions()).containsExactly("ASSIGNEE");
    }

    @Test
    void shouldCreateWorkflowWhenProjectHasNone() {
        when(workflowMapper.selectOne(any())).thenReturn(null);
        when(boardColumnMapper.selectCount(any())).thenReturn(2L);

        WorkflowVO vo = service().saveWorkflow(request(), 9L);

        assertThat(vo.getProjectId()).isEqualTo(7L);
        assertThat(vo.getIssueType()).isEqualTo("STORY");
        assertThat(vo.getConfig().getNodes()).hasSize(2);
        verify(workflowMapper).insert(any(Workflow.class));
        verify(workflowMapper, never()).updateById(any());
    }

    @Test
    void shouldUpdateExistingWorkflowInsteadOfInserting() {
        Workflow existing = workflow();
        existing.setName("旧名称");
        when(workflowMapper.selectOne(any())).thenReturn(existing);
        when(boardColumnMapper.selectCount(any())).thenReturn(2L);

        WorkflowVO vo = service().saveWorkflow(request(), 9L);

        assertThat(vo.getName()).isEqualTo("新工作流");
        verify(workflowMapper).updateById(existing);
        verify(workflowMapper, never()).insert(any());
    }

    @Test
    void shouldRejectNodesThatBelongToAnotherProject() {
        when(boardColumnMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().saveWorkflow(request(), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该项目");

        verify(workflowMapper, never()).insert(any());
    }

    @Test
    void shouldRejectWorkflowWithoutTerminalNode() {
        when(boardColumnMapper.selectCount(any())).thenReturn(2L);
        SaveWorkflowRequest request = request();
        request.getConfig().getNodes().forEach(node -> node.setIsTerminal(false));

        assertThatThrownBy(() -> service().saveWorkflow(request, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("终态");
    }

    @Test
    void shouldBuildScrumTemplateWithForwardAndBackwardTransitions() {
        when(workflowMapper.selectOne(any())).thenReturn(null);
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(
                column(10L, "待办", 0), column(20L, "进行中", 1), column(30L, "已完成", 2)));
        when(boardColumnMapper.selectCount(any())).thenReturn(3L);

        WorkflowVO vo = service().applyTemplate(7L, "STORY", "scrum", 9L);

        assertThat(vo.getConfig().getTransitions()).hasSize(4);
        assertThat(vo.getConfig().getNodes()).hasSize(3);
        assertThat(vo.getConfig().getNodes().get(2).getIsTerminal()).isTrue();
    }

    @Test
    void shouldRejectUnknownWorkflowTemplate() {
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(column(10L, "待办", 0)));

        assertThatThrownBy(() -> service().applyTemplate(7L, "STORY", "waterfall", 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知模板");
    }

    @Test
    void shouldListAllProjectColumnsWhenNoWorkflowConfigured() {
        when(workflowMapper.selectOne(any())).thenReturn(null);
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(
                column(10L, "待办", 0), column(20L, "进行中", 1), column(30L, "已完成", 2)));

        List<AvailableTransitionVO> transitions = service().getAvailableTransitions(7L, "STORY", 10L);

        assertThat(transitions).extracting(AvailableTransitionVO::getToColumnId)
                .containsExactly(20L, 30L);
        assertThat(transitions.get(0).getConditions()).isEmpty();
    }

    @Test
    void shouldRejectNonOwnerWhenNodeHasOwner() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithOwner(300L));
        when(userMapper.selectById(300L)).thenReturn(user(300L, "ROLE_MEMBER"));
        when(userMapper.selectById(99L)).thenReturn(user(99L, "ROLE_MEMBER"));
        when(projectMapper.selectById(7L)).thenReturn(project(7L, 500L));
        when(teamMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().validateTransition(issue(10L, 20L), 10L, 20L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("这一步由「user-300」负责");
    }

    @Test
    void shouldAllowNodeOwnerToPushForward() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithOwner(300L));
        when(userMapper.selectById(300L)).thenReturn(user(300L, "ROLE_MEMBER"));

        assertThat(service().validateTransition(issue(10L, 20L), 10L, 20L, 300L)).isTrue();
    }

    @Test
    void shouldLetProjectAdminOverrideNodeOwner() {
        // 责任人休假/离职时流程不能卡死：项目管理员永远能推进
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithOwner(300L));
        when(projectMapper.selectById(7L)).thenReturn(project(7L, 99L));

        assertThat(service().validateTransition(issue(10L, 20L), 10L, 20L, 99L)).isTrue();
    }

    @Test
    void shouldTellFrontendWhoBlocksTheNextStep() {
        when(workflowMapper.selectOne(any())).thenReturn(workflowWithOwner(300L));
        when(userMapper.selectById(300L)).thenReturn(user(300L, "ROLE_MEMBER"));
        when(userMapper.selectById(99L)).thenReturn(user(99L, "ROLE_MEMBER"));
        when(projectMapper.selectById(7L)).thenReturn(project(7L, 500L));
        when(boardColumnMapper.selectById(20L)).thenReturn(column("进行中"));

        List<AvailableTransitionVO> mine = service().getAvailableTransitions(7L, "STORY", 10L, 99L);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getAllowed()).isFalse();
        assertThat(mine.get(0).getReason()).contains("这一步由「user-300」负责");
        assertThat(mine.get(0).getFromOwnerId()).isEqualTo(300L);
        assertThat(mine.get(0).getFromOwnerName()).isEqualTo("user-300");

        List<AvailableTransitionVO> owners = service().getAvailableTransitions(7L, "STORY", 10L, 300L);
        assertThat(owners.get(0).getAllowed()).isTrue();
        assertThat(owners.get(0).getReason()).isNull();
    }

    @Test
    void shouldRejectWorkflowWhoseNodeOwnerDoesNotExist() {
        when(boardColumnMapper.selectCount(any())).thenReturn(2L);
        when(userMapper.selectById(300L)).thenReturn(null);
        SaveWorkflowRequest request = request();
        request.getConfig().getNodes().get(0).setOwnerUserId(300L);

        assertThatThrownBy(() -> service().saveWorkflow(request, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("责任人不存在");
    }

    @Test
    void shouldExposeNodeOwnerNameOnWorkflowRead() {
        when(workflowMapper.exists(any())).thenReturn(false);
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(column(10L, "待办", 0)));
        when(userMapper.selectById(300L)).thenReturn(user(300L, "ROLE_MEMBER"));
        Workflow wf = workflowWithOwner(300L);
        when(workflowMapper.selectList(any())).thenReturn(List.of(wf));

        List<WorkflowVO> list = service().listByProject(7L, 9L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getConfig().getNodes().get(0).getOwnerName()).isEqualTo("user-300");
    }

    private Workflow workflowWithOwner(Long ownerId) {
        WorkflowNode from = new WorkflowNode();
        from.setColumnId(10L);
        from.setName("待办");
        from.setIsTerminal(false);
        from.setOwnerUserId(ownerId);
        WorkflowNode to = new WorkflowNode();
        to.setColumnId(20L);
        to.setName("进行中");
        to.setIsTerminal(true);

        WorkflowTransition transition = new WorkflowTransition();
        transition.setFromColumnId(10L);
        transition.setToColumnId(20L);
        transition.setConditions(List.of());

        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(List.of(from, to));
        config.setTransitions(List.of(transition));

        try {
            Workflow wf = new Workflow();
            wf.setId(1L);
            wf.setProjectId(7L);
            wf.setIssueType("STORY");
            wf.setIsActive(1);
            wf.setIsDefault(0);
            wf.setConfig(JSON.writeValueAsString(config));
            return wf;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void shouldAutoGenerateDefaultWorkflowsForAllIssueTypes() {
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(
                column(10L, "待办", 0), column(20L, "进行中", 1), column(30L, "已完成", 2)));
        when(workflowMapper.exists(any())).thenReturn(false);

        service().ensureDefaultWorkflows(7L, 9L);

        ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
        verify(workflowMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Workflow::getIssueType)
                .containsExactly("STORY", "TASK", "BUG");
        Workflow first = captor.getAllValues().get(0);
        assertThat(first.getIsDefault()).isEqualTo(1);
        assertThat(first.getIsActive()).isEqualTo(1);
        assertThat(first.getName()).contains("自动生成");
        assertThat(first.getConfig()).contains("transitions");
    }

    @Test
    void shouldNotRegenerateWorkflowThatAlreadyExists() {
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(column(10L, "待办", 0)));
        when(workflowMapper.exists(any())).thenReturn(true);

        service().ensureDefaultWorkflows(7L, 9L);

        verify(workflowMapper, never()).insert(any());
    }

    @Test
    void shouldSkipAutoGenerationWhenProjectHasNoColumns() {
        when(boardColumnMapper.selectList(any())).thenReturn(List.of());

        service().ensureDefaultWorkflows(7L, 9L);

        verify(workflowMapper, never()).insert(any());
        verify(workflowMapper, never()).exists(any());
    }

    private SaveWorkflowRequest request() {
        WorkflowNode todo = new WorkflowNode();
        todo.setColumnId(10L);
        todo.setName("待办");
        todo.setIsTerminal(false);
        WorkflowNode done = new WorkflowNode();
        done.setColumnId(20L);
        done.setName("已完成");
        done.setIsTerminal(true);

        WorkflowTransition transition = new WorkflowTransition();
        transition.setFromColumnId(10L);
        transition.setToColumnId(20L);
        transition.setConditions(List.of());

        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(List.of(todo, done));
        config.setTransitions(List.of(transition));

        SaveWorkflowRequest request = new SaveWorkflowRequest();
        request.setProjectId(7L);
        request.setIssueType("STORY");
        request.setName("新工作流");
        request.setConfig(config);
        return request;
    }

    private WorkflowService service() {
        return new WorkflowService(workflowMapper, boardColumnMapper, projectMapper, teamMemberMapper, userMapper,
                new AccessControlService(roleService, userMapper, teamMemberMapper));
    }

    private Workflow workflow() {
        return workflowWithConditions(List.of());
    }

    private Workflow workflowWithConditions(List<String> conditions) {
        WorkflowTransition transition = new WorkflowTransition();
        transition.setFromColumnId(10L);
        transition.setToColumnId(20L);
        transition.setConditions(conditions);

        WorkflowConfig config = new WorkflowConfig();
        config.setTransitions(List.of(transition));
        try {
            Workflow wf = new Workflow();
            wf.setId(1L);
            wf.setProjectId(7L);
            wf.setIssueType("STORY");
            wf.setIsActive(1);
            wf.setIsDefault(0);
            wf.setConfig(JSON.writeValueAsString(config));
            return wf;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Issue issue(Long fromColumnId, Long toColumnId) {
        Issue issue = new Issue();
        issue.setId(1L);
        issue.setProjectId(7L);
        issue.setType("STORY");
        issue.setColumnId(fromColumnId);
        issue.setReporterId(100L);
        return issue;
    }

    private BoardColumn column(String name) {
        BoardColumn column = new BoardColumn();
        column.setName(name);
        return column;
    }

    private BoardColumn column(Long id, String name, Integer sortOrder) {
        BoardColumn column = column(name);
        column.setId(id);
        column.setSortOrder(sortOrder);
        return column;
    }

    private Project project(Long id, Long ownerId) {
        Project project = new Project();
        project.setId(id);
        project.setTeamId(3L);
        project.setOwnerId(ownerId);
        return project;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole(role);
        return user;
    }
}
