package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueGovernanceDTO.ChangeRequestVO;
import com.mimo.dto.IssueGovernanceDTO.CreateChangeRequest;
import com.mimo.dto.IssueGovernanceDTO.FieldDiff;
import com.mimo.dto.IssueGovernanceDTO.ImpactVO;
import com.mimo.entity.ActivityLog;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueChangeRequest;
import com.mimo.entity.Notification;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.IssueChangeRequestMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueChangeRequestService {

    private static final Set<String> ALLOWED_CHANGE_FIELDS = Set.of(
            "title", "description", "priority", "dueDate", "storyPoints", "sprintId",
            "planStartDate", "planEndDate", "dependencies", "userRole", "userGoal",
            "businessValue", "acceptanceCriteria", "epic", "severity", "stepsToRepro",
            "environment", "expectedResult", "actualResult", "foundVersion", "fixedVersion"
    );

    private final IssueChangeRequestMapper issueChangeRequestMapper;
    private final ProjectAccessService projectAccessService;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;
    private final NotificationService notificationService;
    private final IssueVersionService issueVersionService;
    private final IssueService issueService;
    private final TeamService teamService;
    private final IssueImpactService issueImpactService;
    private final ObjectMapper objectMapper;
    private final AccessControlService accessControlService;

    @Transactional
    public ChangeRequestVO submit(Long issueId, CreateChangeRequest request, Long applicantId) {
        Issue issue = requireIssue(issueId);
        assertProjectMember(issue, applicantId);
        issueVersionService.ensureInitialSnapshot(issue, applicantId);
        int currentVersion = issueVersionService.latestVersionNo(issueId);

        if (!Objects.equals(request.getBaseVersion(), currentVersion)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "工作项当前版本为 v" + currentVersion + "，请刷新后重新提交");
        }
        if (issueChangeRequestMapper.exists(new LambdaQueryWrapper<IssueChangeRequest>()
                .eq(IssueChangeRequest::getIssueId, issueId)
                .eq(IssueChangeRequest::getStatus, "PENDING"))) {
            throw new BusinessException(ResultCode.CONFLICT, "该工作项已有待处理的变更申请");
        }
        if (request.getProposedChanges() == null || request.getProposedChanges().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "没有需要提交的字段变更");
        }

        Map<String, Object> proposed = new LinkedHashMap<>(request.getProposedChanges());
        List<String> invalidFields = proposed.keySet().stream()
                .filter(field -> !ALLOWED_CHANGE_FIELDS.contains(field))
                .toList();
        if (!invalidFields.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "以下字段不允许通过变更申请修改: " + String.join(", ", invalidFields));
        }

        List<FieldDiff> diffs = issueVersionService.diffWithProposed(
                issueVersionService.snapshot(issueId, request.getBaseVersion()), proposed);
        if (diffs.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "提交内容与当前版本一致");
        }

        IssueChangeRequest entity = new IssueChangeRequest();
        entity.setIssueId(issueId);
        entity.setBaseVersion(request.getBaseVersion());
        entity.setProposedChanges(writeJson(proposed));
        entity.setChangeReason(request.getChangeReason());
        entity.setStatus("PENDING");
        entity.setApplicantId(applicantId);
        issueChangeRequestMapper.insert(entity);
        return toVO(entity, issue);
    }

    public List<ChangeRequestVO> listByIssue(Long issueId, Long userId) {
        Issue issue = requireIssue(issueId);
        assertProjectMember(issue, userId);
        return issueChangeRequestMapper.selectList(new LambdaQueryWrapper<IssueChangeRequest>()
                        .eq(IssueChangeRequest::getIssueId, issueId)
                        .orderByDesc(IssueChangeRequest::getCreatedAt))
                .stream().map(row -> toVO(row, issue)).toList();
    }

    public List<ChangeRequestVO> listPendingForUser(Long userId) {
        List<IssueChangeRequest> pending = issueChangeRequestMapper.selectList(
                new LambdaQueryWrapper<IssueChangeRequest>()
                        .eq(IssueChangeRequest::getStatus, "PENDING")
                        .orderByDesc(IssueChangeRequest::getCreatedAt));
        List<ChangeRequestVO> result = new ArrayList<>();
        for (IssueChangeRequest row : pending) {
            Issue issue = issueMapper.selectById(row.getIssueId());
            if (issue == null) continue;
            if (isAdmin(issue, userId) || issue.getReporterId().equals(userId)) {
                result.add(toVO(row, issue));
            }
        }
        return result;
    }

    public ImpactVO impact(Long requestId, Long userId) {
        IssueChangeRequest request = issueChangeRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变更申请不存在");
        }
        Issue issue = requireIssue(request.getIssueId());
        assertProjectMember(issue, userId);
        return issueImpactService.analyze(request, issue);
    }

    @Transactional
    public ChangeRequestVO approve(Long requestId, Long reviewerId, String note) {
        IssueChangeRequest request = requirePending(requestId);
        if (Objects.equals(request.getApplicantId(), reviewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能审批自己提交的变更申请");
        }
        Issue issue = requireIssue(request.getIssueId());
        assertReviewer(issue, reviewerId);

        int currentVersion = issueVersionService.latestVersionNo(issue.getId());
        if (!Objects.equals(request.getBaseVersion(), currentVersion)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "变更申请基于 v" + request.getBaseVersion() + "，当前已是 v" + currentVersion
                            + "，请驳回后重新提交");
        }

        Map<String, Object> proposed = readMap(request.getProposedChanges());
        issueService.applyApprovedChange(issue.getId(), proposed, reviewerId, request.getChangeReason());

        request.setStatus("APPROVED");
        request.setReviewerId(reviewerId);
        request.setReviewNote(note);
        request.setReviewedAt(LocalDateTime.now());
        issueChangeRequestMapper.updateById(request);

        writeActivity(issue, reviewerId, "CHANGE_APPROVED",
                "通过了 " + issue.getIssueKey() + " 的变更申请");
        notifyApplicant(request, issue, true, note);
        return toVO(request, requireIssue(issue.getId()));
    }

    @Transactional
    public ChangeRequestVO reject(Long requestId, Long reviewerId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驳回时必须填写原因");
        }
        IssueChangeRequest request = requirePending(requestId);
        if (Objects.equals(request.getApplicantId(), reviewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能审批自己提交的变更申请");
        }
        Issue issue = requireIssue(request.getIssueId());
        assertReviewer(issue, reviewerId);

        request.setStatus("REJECTED");
        request.setReviewerId(reviewerId);
        request.setReviewNote(note);
        request.setReviewedAt(LocalDateTime.now());
        issueChangeRequestMapper.updateById(request);

        writeActivity(issue, reviewerId, "CHANGE_REJECTED",
                "驳回了 " + issue.getIssueKey() + " 的变更申请: " + note);
        notifyApplicant(request, issue, false, note);
        return toVO(request, issue);
    }

    @Transactional
    public void withdraw(Long requestId, Long applicantId) {
        IssueChangeRequest request = requirePending(requestId);
        if (!Objects.equals(request.getApplicantId(), applicantId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能撤回自己提交的变更申请");
        }
        request.setStatus("WITHDRAWN");
        request.setReviewNote("申请人主动撤回");
        request.setReviewedAt(LocalDateTime.now());
        issueChangeRequestMapper.updateById(request);
    }

    private IssueChangeRequest requirePending(Long requestId) {
        IssueChangeRequest request = issueChangeRequestMapper.selectById(requestId);
        if (request == null) throw new BusinessException(ResultCode.NOT_FOUND, "变更申请不存在");
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该变更申请已处理");
        }
        return request;
    }

    private Issue requireIssue(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        return issue;
    }

    private void assertProjectMember(Issue issue, Long userId) {
        // 与"读"同一个口径：系统管理员 / 团队成员 / 团队管理员都能操作（BUG-115）
        projectAccessService.requireProject(issue.getProjectId(), userId);
    }

    private void assertReviewer(Issue issue, Long userId) {
        assertProjectMember(issue, userId);
        if (!isAdmin(issue, userId) && !Objects.equals(issue.getReporterId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有项目管理员或报告人可以审批变更");
        }
    }

    private boolean isAdmin(Issue issue, Long userId) {
        if (accessControlService.isSystemAdmin(userId)) return true;
        Project project = projectMapper.selectById(issue.getProjectId());
        return project != null && (Objects.equals(project.getOwnerId(), userId)
                || teamService.isTeamAdmin(project.getTeamId(), userId));
    }

    private ChangeRequestVO toVO(IssueChangeRequest request, Issue issue) {
        ChangeRequestVO vo = new ChangeRequestVO();
        vo.setId(request.getId());
        vo.setIssueId(request.getIssueId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setIssueTitle(issue.getTitle());
        vo.setBaseVersion(request.getBaseVersion());
        vo.setCurrentVersion(issueVersionService.latestVersionNo(issue.getId()));
        vo.setVersionConflict(!Objects.equals(vo.getBaseVersion(), vo.getCurrentVersion()));
        vo.setProposedChanges(readMap(request.getProposedChanges()));
        vo.setDiffs(issueVersionService.diffWithProposed(
                issueVersionService.snapshot(issue.getId(), request.getBaseVersion()),
                vo.getProposedChanges()));
        vo.setChangeReason(request.getChangeReason());
        vo.setStatus(request.getStatus());
        vo.setApplicantId(request.getApplicantId());
        vo.setApplicantName(username(request.getApplicantId()));
        vo.setReviewerId(request.getReviewerId());
        vo.setReviewerName(username(request.getReviewerId()));
        vo.setReviewNote(request.getReviewNote());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setCreatedAt(request.getCreatedAt());
        return vo;
    }

    private String username(Long userId) {
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        return user == null ? "未知用户" : user.getUsername();
    }

    private void notifyApplicant(IssueChangeRequest request, Issue issue, boolean approved, String note) {
        Notification notification = new Notification();
        notification.setUserId(request.getApplicantId());
        notification.setType(approved ? "ISSUE_CHANGE_APPROVED" : "ISSUE_CHANGE_REJECTED");
        notification.setTitle(approved ? "工作项变更已通过" : "工作项变更被驳回");
        notification.setContent((approved ? "变更已应用到 " : "变更未应用，") + issue.getIssueKey()
                + (note == null || note.isBlank() ? "" : "，说明：" + note));
        notification.setRelatedId(issue.getId());
        notification.setRelatedType("ISSUE");
        notification.setIsRead(0);
        notificationService.create(notification);
    }

    private void writeActivity(Issue issue, Long userId, String action, String detail) {
        User user = userMapper.selectById(userId);
        ActivityLog log = new ActivityLog();
        log.setUserId(userId);
        log.setUsername(user == null ? "未知用户" : user.getUsername());
        log.setProjectId(issue.getProjectId());
        log.setTargetType("ISSUE");
        log.setTargetId(issue.getId());
        log.setAction(action);
        log.setDetail(detail);
        activityLogMapper.insert(log);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "变更数据序列化失败");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
