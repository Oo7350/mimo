package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.common.AuditLog;
import com.mimo.dto.IssueDTO.*;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.IssueService;
import com.mimo.service.TeamService;
import com.mimo.service.WebhookDispatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;
    private final WebhookDispatcherService webhookDispatcher;

    @PostMapping
    @AuditLog(targetType = "'ISSUE'", targetId = "#request.projectId", action = "'CREATE'", detail = "'创建任务: ' + #request.title")
    public Result<IssueVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        IssueVO vo = issueService.create(request, userId);
        Project p = projectMapper.selectById(request.getProjectId());
        Long teamId = p == null ? null : p.getTeamId();
        webhookDispatcher.dispatch("ISSUE_CREATED", request.getProjectId(), teamId, vo);
        return Result.success(vo);
    }

    @GetMapping("/{id}")
    public Result<IssueVO> getById(@PathVariable Long id) {
        return Result.success(issueService.getById(id));
    }

    @PutMapping
    @AuditLog(targetType = "'ISSUE'", targetId = "#request.id", action = "'UPDATE'", detail = "'更新任务#' + #request.id")
    public Result<Void> update(@Valid @RequestBody UpdateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        issueService.update(request, userId);
        Issue i = issueMapper.selectById(request.getId());
        if (i != null) {
            Project p = projectMapper.selectById(i.getProjectId());
            webhookDispatcher.dispatch("ISSUE_UPDATED", i.getProjectId(),
                    p == null ? null : p.getTeamId(), request);
        }
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/{id}")
    @AuditLog(targetType = "'ISSUE'", targetId = "#id", action = "'DELETE'", detail = "'删除任务#' + #id")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        Issue issue = issueMapper.selectById(id);
        issueService.delete(id, userId);
        if (issue != null) {
            Project p = projectMapper.selectById(issue.getProjectId());
            webhookDispatcher.dispatch("ISSUE_DELETED", issue.getProjectId(),
                    p == null ? null : p.getTeamId(),
                    java.util.Collections.singletonMap("issueId", id));
        }
        return Result.successMessage("删除成功");
    }

    @PostMapping("/query")
    public Result<List<IssueVO>> query(@RequestBody QueryRequest request) {
        return Result.success(issueService.query(request));
    }

    // ---- BUG status management ----

    @PutMapping("/{id}/bug-status")
    public Result<Void> updateBugStatus(@PathVariable Long id, @Valid @RequestBody BugStatusRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        request.setIssueId(id);
        issueService.updateBugStatus(request);
        return Result.successMessage("缺陷状态更新成功");
    }

    // ---- Acceptance Criteria management (STORY only) ----

    @PostMapping("/{id}/acceptance-criteria")
    public Result<List<AcceptanceCriterion>> addAcceptanceCriteria(
            @PathVariable Long id, @Valid @RequestBody AcceptanceCriteriaRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertProjectMember(id, userId);
        return Result.success(issueService.addAcceptanceCriteria(id, request));
    }

    @PutMapping("/{id}/acceptance-criteria/{criteriaId}")
    public Result<List<AcceptanceCriterion>> updateAcceptanceCriteria(
            @PathVariable Long id, @PathVariable String criteriaId, @RequestBody AcceptanceCriterion update, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertProjectMember(id, userId);
        return Result.success(issueService.updateAcceptanceCriteria(id, criteriaId, update));
    }

    @DeleteMapping("/{id}/acceptance-criteria/{criteriaId}")
    public Result<List<AcceptanceCriterion>> deleteAcceptanceCriteria(
            @PathVariable Long id, @PathVariable String criteriaId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertProjectMember(id, userId);
        return Result.success(issueService.deleteAcceptanceCriteria(id, criteriaId));
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    /** 校验当前用户是否属于该任务所在项目的团队成员 */
    private void assertProjectMember(Long issueId, Long userId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        Project project = projectMapper.selectById(issue.getProjectId());
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        if (!teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此任务");
        }
    }
}
