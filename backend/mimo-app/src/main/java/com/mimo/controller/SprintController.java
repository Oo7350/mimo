package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.SprintDTO.*;
import com.mimo.dto.SprintHealthDTO.AiSummaryVO;
import com.mimo.dto.SprintHealthDTO.SprintHealthVO;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.service.SprintService;
import com.mimo.service.SprintHealthService;
import com.mimo.service.ProjectAccessService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final SprintHealthService sprintHealthService;
    private final SprintMapper sprintMapper;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;
    private final ProjectAccessService projectAccessService;

    @PostMapping
    public Result<SprintVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberByProject(request.getProjectId(), userId);
        return Result.success(sprintService.create(request));
    }

    @GetMapping("/{id}")
    public Result<SprintVO> getById(@PathVariable Long id, Authentication auth) {
        assertTeamMemberBySprint(id, getLongPrincipal(auth));
        return Result.success(sprintService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public Result<List<SprintVO>> listByProject(@PathVariable Long projectId, Authentication auth) {
        assertTeamMemberByProject(projectId, getLongPrincipal(auth));
        return Result.success(sprintService.listByProject(projectId));
    }

    /**
     * 更新 Sprint 名称/目标/起止日期（项目成员可用）
     */
    @PutMapping("/{id}")
    public Result<SprintVO> update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateRequest request,
                                   Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        return Result.success(sprintService.updateSprint(id, request));
    }

    @PutMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        sprintService.startSprint(id);
        return Result.successMessage("Sprint 已启动");
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        sprintService.completeSprint(id);
        return Result.successMessage("Sprint 已完成");
    }

    @GetMapping("/{id}/burndown")
    public Result<BurndownVO> burndown(@PathVariable Long id, Authentication auth) {
        assertTeamMemberBySprint(id, getLongPrincipal(auth));
        return Result.success(sprintService.getBurndown(id));
    }

    @PostMapping("/{id}/snapshot")
    public Result<Void> takeSnapshot(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        sprintService.generateSnapshot(id);
        return Result.successMessage("快照已生成");
    }

    @PostMapping("/quick/{projectId}")
    public Result<SprintVO> quickStart(@PathVariable Long projectId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberByProject(projectId, userId);
        return Result.success(sprintService.createQuickSprint(projectId));
    }

    @PutMapping("/{id}/complete-migration")
    public Result<Void> completeWithMigration(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetSprintId,
            Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        sprintService.completeSprintWithMigration(id, targetSprintId);
        return Result.successMessage("Sprint 已完成");
    }

    @PutMapping("/{issueId}/add-to-sprint/{sprintId}")
    public Result<Void> addIssueToSprint(
            @PathVariable Long issueId,
            @PathVariable Long sprintId,
            Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(sprintId, userId);
        sprintService.addIssueToSprint(issueId, sprintId);
        return Result.successMessage("任务已加入 Sprint");
    }

    @GetMapping("/{id}/stats")
    public Result<SprintStatsVO> getStats(@PathVariable Long id, Authentication auth) {
        assertTeamMemberBySprint(id, getLongPrincipal(auth));
        return Result.success(sprintService.getSprintStats(id));
    }

    @GetMapping("/{id}/health")
    public Result<SprintHealthVO> getHealth(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        return Result.success(sprintHealthService.getHealth(id));
    }

    @PostMapping("/{id}/health/ai-summary")
    public Result<AiSummaryVO> getHealthAiSummary(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberBySprint(id, userId);
        return Result.success(sprintHealthService.getAiSummary(id, userId));
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    private void assertTeamMemberByProject(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        // 以前只认 成员/团队管理员，漏了系统管理员（BUG-117 一起收敛）
        if (!projectAccessService.canAccessProject(project.getId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此项目的Sprint");
        }
    }

    private void assertTeamMemberBySprint(Long sprintId, Long userId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        assertTeamMemberByProject(sprint.getProjectId(), userId);
    }
}
