package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.IssueDTO.*;
import com.mimo.service.IssueService;
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

    @PostMapping
    public Result<IssueVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(issueService.create(request, userId));
    }

    @GetMapping("/{id}")
    public Result<IssueVO> getById(@PathVariable Long id) {
        return Result.success(issueService.getById(id));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        issueService.update(request, userId);
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        issueService.delete(id, userId);
        return Result.successMessage("删除成功");
    }

    @PostMapping("/query")
    public Result<List<IssueVO>> query(@RequestBody QueryRequest request) {
        return Result.success(issueService.query(request));
    }

    // ---- BUG status management ----

    @PutMapping("/{id}/bug-status")
    public Result<Void> updateBugStatus(@PathVariable Long id, @Valid @RequestBody BugStatusRequest request) {
        request.setIssueId(id);
        issueService.updateBugStatus(request);
        return Result.successMessage("缺陷状态更新成功");
    }

    // ---- Acceptance Criteria management (STORY only) ----

    @PostMapping("/{id}/acceptance-criteria")
    public Result<List<AcceptanceCriterion>> addAcceptanceCriteria(
            @PathVariable Long id, @Valid @RequestBody AcceptanceCriteriaRequest request) {
        return Result.success(issueService.addAcceptanceCriteria(id, request));
    }

    @PutMapping("/{id}/acceptance-criteria/{criteriaId}")
    public Result<List<AcceptanceCriterion>> updateAcceptanceCriteria(
            @PathVariable Long id, @PathVariable String criteriaId, @RequestBody AcceptanceCriterion update) {
        return Result.success(issueService.updateAcceptanceCriteria(id, criteriaId, update));
    }

    @DeleteMapping("/{id}/acceptance-criteria/{criteriaId}")
    public Result<List<AcceptanceCriterion>> deleteAcceptanceCriteria(
            @PathVariable Long id, @PathVariable String criteriaId) {
        return Result.success(issueService.deleteAcceptanceCriteria(id, criteriaId));
    }
}
