package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.entity.Project;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.EmailSendLogMapper;
import com.mimo.mapper.WebhookConfigMapper;
import com.mimo.mapper.WebhookDeliveryLogMapper;
import com.mimo.service.ProjectAccessService;
import com.mimo.service.SprintHealthService;
import com.mimo.service.SprintService;
import com.mimo.service.TeamService;
import com.mimo.service.WebhookDispatcherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlTest {

    @Mock
    private TeamService teamService;
    @Mock
    private SprintService sprintService;
    @Mock
    private SprintHealthService sprintHealthService;
    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private EmailSendLogMapper emailSendLogMapper;
    @Mock
    private WebhookConfigMapper webhookConfigMapper;
    @Mock
    private WebhookDeliveryLogMapper webhookDeliveryLogMapper;
    @Mock
    private WebhookDispatcherService webhookDispatcherService;
    @Mock
    private Authentication authentication;

    @Test
    void shouldBlockCrossTeamSprintRead() {
        when(authentication.getPrincipal()).thenReturn(9L);
        Project project = project();
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(teamService.isTeamMember(2L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(2L, 9L)).thenReturn(false);

        SprintController controller = new SprintController(
                sprintService, sprintHealthService, sprintMapper, projectMapper, teamService);

        assertThatThrownBy(() -> controller.listByProject(1L, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void shouldAllowPlatformAdminSprintRead() {
        when(authentication.getPrincipal()).thenReturn(9L);
        Project project = project();
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(teamService.isTeamMember(2L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(2L, 9L)).thenReturn(true);
        when(sprintService.listByProject(1L)).thenReturn(List.of());

        SprintController controller = new SprintController(
                sprintService, sprintHealthService, sprintMapper, projectMapper, teamService);

        assertThat(controller.listByProject(1L, authentication).getCode()).isEqualTo(200);
    }

    @Test
    void shouldBlockCrossTeamRead() {
        when(authentication.getPrincipal()).thenReturn(9L);
        when(teamService.isTeamMember(2L, 9L)).thenReturn(false);
        when(teamService.isTeamAdmin(2L, 9L)).thenReturn(false);

        TeamController controller = new TeamController(teamService);

        assertThatThrownBy(() -> controller.getById(2L, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void shouldBlockNonAdminEmailLogAccess() {
        when(authentication.getPrincipal()).thenReturn(9L);
        when(projectAccessService.isSystemAdmin(9L)).thenReturn(false);
        EmailSendLogController controller = new EmailSendLogController(
                emailSendLogMapper, projectAccessService);

        assertThatThrownBy(() -> controller.list(
                1, 20, null, null, null, null, null, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅系统管理员");
    }

    @Test
    void shouldBlockNonAdminWebhookAccess() {
        when(authentication.getPrincipal()).thenReturn(9L);
        when(projectAccessService.isSystemAdmin(9L)).thenReturn(false);
        WebhookConfigController controller = new WebhookConfigController(
                webhookConfigMapper, webhookDeliveryLogMapper,
                webhookDispatcherService, projectAccessService);

        assertThatThrownBy(() -> controller.list(
                1, 20, null, null, false, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅系统管理员");
    }

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setTeamId(2L);
        return project;
    }
}
