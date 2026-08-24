package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkflowDTO.*;
import com.mimo.entity.Project;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.TeamService;
import com.mimo.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 工作流引擎 REST API
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;

    /** 获取项目的所有工作流 */
    @GetMapping("/project/{projectId}")
    public Result<List<WorkflowVO>> listByProject(@PathVariable Long projectId, Authentication auth) {
        assertTeamMember(projectId, auth);
        return Result.success(workflowService.listByProject(projectId));
    }

    /** 获取项目+类型的激活工作流 */
    @GetMapping("/project/{projectId}/type/{issueType}")
    public Result<WorkflowVO> getActiveWorkflow(@PathVariable Long projectId,
                                                 @PathVariable String issueType,
                                                 Authentication auth) {
        assertTeamMember(projectId, auth);
        return Result.success(workflowService.getActiveWorkflow(projectId, issueType));
    }

    /** 创建或更新工作流 */
    @PostMapping
    public Result<WorkflowVO> saveWorkflow(@Valid @RequestBody SaveWorkflowRequest request,
                                           Authentication auth) {
        assertTeamMember(request.getProjectId(), auth);
        Long userId = getLongPrincipal(auth);
        return Result.success(workflowService.saveWorkflow(request, userId));
    }

    /** 删除工作流 */
    @DeleteMapping("/{workflowId}")
    public Result<Void> deleteWorkflow(@PathVariable Long workflowId, Authentication auth) {
        workflowService.deleteWorkflow(workflowId);
        return Result.successMessage("删除成功");
    }

    /** 应用预设模板 */
    @PostMapping("/template")
    public Result<WorkflowVO> applyTemplate(@RequestParam Long projectId,
                                            @RequestParam String issueType,
                                            @RequestParam String templateName,
                                            Authentication auth) {
        assertTeamMember(projectId, auth);
        Long userId = getLongPrincipal(auth);
        return Result.success(workflowService.applyTemplate(projectId, issueType, templateName, userId));
    }

    /** 获取当前状态下的可用转换 */
    @GetMapping("/transitions")
    public Result<List<AvailableTransitionVO>> getAvailableTransitions(
            @RequestParam Long projectId,
            @RequestParam String issueType,
            @RequestParam Long columnId,
            Authentication auth) {
        assertTeamMember(projectId, auth);
        return Result.success(workflowService.getAvailableTransitions(projectId, issueType, columnId));
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    private void assertTeamMember(Long projectId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        if (!teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此项目");
        }
    }
}
