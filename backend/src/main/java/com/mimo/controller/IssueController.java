package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueDTO.*;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.IssueService;
import com.mimo.service.TeamService;
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

    @PostMapping
    public Result<IssueVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(issueService.create(request, userId));
    }

    @GetMapping("/{id}")
    public Result<IssueVO> getById(@PathVariable Long id) {
        return Result.success(issueService.getById(id));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        issueService.update(request, userId);
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        issueService.delete(id, userId);
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
