package com.mimo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueGovernanceDTO.ChangeRequestVO;
import com.mimo.dto.IssueGovernanceDTO.CreateChangeRequest;
import com.mimo.dto.IssueGovernanceDTO.FieldDiff;
import com.mimo.dto.IssueGovernanceDTO.ImpactVO;
import com.mimo.dto.IssueGovernanceDTO.IssueVersionVO;
import com.mimo.dto.IssueGovernanceDTO.ReviewChangeRequest;
import com.mimo.dto.IssueGovernanceDTO.ReviewVO;
import com.mimo.dto.IssueGovernanceDTO.SubmitReviewRequest;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.IssueChangeRequestService;
import com.mimo.service.IssueReviewService;
import com.mimo.service.IssueVersionService;
import com.mimo.service.ProjectAccessService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IssueGovernanceController {

    private final IssueVersionService issueVersionService;
    private final IssueChangeRequestService issueChangeRequestService;
    private final IssueReviewService issueReviewService;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/issues/{issueId}/versions")
    public Result<List<IssueVersionVO>> versions(@PathVariable Long issueId, Authentication auth) {
        Long userId = userId(auth);
        Issue issue = requireIssueAndMember(issueId, userId);
        return Result.success(issueVersionService.list(issueId, issue));
    }

    @GetMapping("/issues/{issueId}/versions/{versionNo}")
    public Result<JsonNode> version(@PathVariable Long issueId, @PathVariable Integer versionNo,
                                    Authentication auth) {
        requireIssueAndMember(issueId, userId(auth));
        return Result.success(issueVersionService.snapshot(issueId, versionNo));
    }

    @GetMapping("/issues/{issueId}/versions/diff")
    public Result<List<FieldDiff>> versionDiff(@PathVariable Long issueId,
                                               @RequestParam Integer from,
                                               @RequestParam Integer to,
                                               Authentication auth) {
        requireIssueAndMember(issueId, userId(auth));
        return Result.success(issueVersionService.diff(
                issueVersionService.snapshot(issueId, from),
                issueVersionService.snapshot(issueId, to)));
    }

    @GetMapping("/issues/{issueId}/change-requests")
    public Result<List<ChangeRequestVO>> changeRequests(@PathVariable Long issueId, Authentication auth) {
        return Result.success(issueChangeRequestService.listByIssue(issueId, userId(auth)));
    }

    @PostMapping("/issues/{issueId}/change-requests")
    public Result<ChangeRequestVO> submitChangeRequest(@PathVariable Long issueId,
                                                       @Valid @RequestBody CreateChangeRequest request,
                                                       Authentication auth) {
        return Result.success(issueChangeRequestService.submit(issueId, request, userId(auth)));
    }

    @GetMapping("/issue-change-requests/pending")
    public Result<List<ChangeRequestVO>> pendingChangeRequests(Authentication auth) {
        return Result.success(issueChangeRequestService.listPendingForUser(userId(auth)));
    }

    @GetMapping("/issue-change-requests/{id}/impact")
    public Result<ImpactVO> changeRequestImpact(@PathVariable Long id, Authentication auth) {
        return Result.success(issueChangeRequestService.impact(id, userId(auth)));
    }

    @PutMapping("/issue-change-requests/{id}/approve")
    public Result<ChangeRequestVO> approveChangeRequest(@PathVariable Long id,
                                                        @RequestBody(required = false) ReviewChangeRequest request,
                                                        Authentication auth) {
        String note = request == null ? null : request.getNote();
        return Result.success(issueChangeRequestService.approve(id, userId(auth), note));
    }

    @PutMapping("/issue-change-requests/{id}/reject")
    public Result<ChangeRequestVO> rejectChangeRequest(@PathVariable Long id,
                                                       @RequestBody ReviewChangeRequest request,
                                                       Authentication auth) {
        return Result.success(issueChangeRequestService.reject(id, userId(auth), request.getNote()));
    }

    @PutMapping("/issue-change-requests/{id}/withdraw")
    public Result<Void> withdrawChangeRequest(@PathVariable Long id, Authentication auth) {
        issueChangeRequestService.withdraw(id, userId(auth));
        return Result.successMessage("已撤回");
    }

    @GetMapping("/issues/{issueId}/reviews")
    public Result<List<ReviewVO>> reviews(@PathVariable Long issueId, Authentication auth) {
        return Result.success(issueReviewService.list(issueId, userId(auth)));
    }

    @PostMapping("/issues/{issueId}/reviews")
    public Result<ReviewVO> submitReview(@PathVariable Long issueId,
                                         @RequestBody SubmitReviewRequest request,
                                         Authentication auth) {
        return Result.success(issueReviewService.submit(issueId, request, userId(auth)));
    }

    @PutMapping("/issue-reviews/{id}/approve")
    public Result<ReviewVO> approveReview(@PathVariable Long id,
                                          @RequestBody(required = false) ReviewChangeRequest request,
                                          Authentication auth) {
        String note = request == null ? null : request.getNote();
        return Result.success(issueReviewService.approve(id, userId(auth), note));
    }

    @PutMapping("/issue-reviews/{id}/reject")
    public Result<ReviewVO> rejectReview(@PathVariable Long id,
                                         @RequestBody ReviewChangeRequest request,
                                         Authentication auth) {
        return Result.success(issueReviewService.reject(id, userId(auth), request.getNote()));
    }

    private Issue requireIssueAndMember(Long issueId, Long userId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        Project project = projectMapper.selectById(issue.getProjectId());
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        // 口径与"看"一致：系统管理员 / 团队成员 / 团队管理员都能操作（BUG-117）
        if (!projectAccessService.canAccessProject(project.getId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该项目的工作项");
        }
        return issue;
    }

    private Long userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
