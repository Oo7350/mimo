package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueGovernanceDTO.ReviewVO;
import com.mimo.dto.IssueGovernanceDTO.SubmitReviewRequest;
import com.mimo.dto.BoardSyncEvent;
import com.mimo.entity.ActivityLog;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Notification;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class IssueReviewService {

    private final IssueReviewMapper issueReviewMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final TeamService teamService;
    private final DeliveryAnalyticsService deliveryAnalyticsService;
    private final ObjectMapper objectMapper;
    private final AccessControlService accessControlService;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public ReviewVO submit(Long issueId, SubmitReviewRequest request, Long submitterId) {
        Issue issue = requireIssue(issueId);
        assertProjectMember(issue, submitterId);
        assertCanSubmit(issue, submitterId);
        if (existsPending(issueId)) {
            throw new BusinessException(ResultCode.CONFLICT, "该任务已有待处理的验收申请");
        }
        if ((request.getCompletionNote() == null || request.getCompletionNote().isBlank())
                && (request.getEvidenceUrls() == null || request.getEvidenceUrls().isEmpty())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写完成说明或提供验收证据");
        }

        IssueReview latest = latest(issueId);
        int roundNo = latest == null ? 1 : latest.getRoundNo() + 1;
        IssueReview review = new IssueReview();
        review.setIssueId(issueId);
        review.setRoundNo(roundNo);
        review.setSubmitterId(submitterId);
        review.setCompletionNote(request.getCompletionNote());
        review.setEvidenceUrls(writeJson(request.getEvidenceUrls()));
        review.setStatus("PENDING");
        review.setSubmittedAt(LocalDateTime.now());
        issueReviewMapper.insert(review);

        writeActivity(issue, submitterId, "REVIEW_SUBMIT", "提交了任务验收 r" + roundNo);
        refreshDeliverySnapshot(issue);
        notify(issue.getReporterId(), issue, "TASK_REVIEW_PENDING", "任务待验收",
                issue.getIssueKey() + " 已提交验收，请及时处理");
        return toVO(review);
    }

    public List<ReviewVO> list(Long issueId, Long userId) {
        Issue issue = requireIssue(issueId);
        assertProjectMember(issue, userId);
        return issueReviewMapper.selectList(new LambdaQueryWrapper<IssueReview>()
                        .eq(IssueReview::getIssueId, issueId)
                        .orderByDesc(IssueReview::getRoundNo))
                .stream().map(this::toVO).toList();
    }

    public List<ReviewVO> listPendingForUser(Long userId) {
        List<IssueReview> pending = issueReviewMapper.selectList(
                new LambdaQueryWrapper<IssueReview>()
                        .eq(IssueReview::getStatus, "PENDING")
                        .orderByDesc(IssueReview::getSubmittedAt));
        List<ReviewVO> result = new ArrayList<>();
        for (IssueReview review : pending) {
            Issue issue = issueMapper.selectById(review.getIssueId());
            if (issue == null || Objects.equals(review.getSubmitterId(), userId)) continue;
            Project project = projectMapper.selectById(issue.getProjectId());
            // 用同一把尺子：不这么写会把"不在成员表里、但确实是团队管理员"的人挡在待验收之外（BUG-117）
            if (project == null || !projectAccessService.canAccessProject(project.getId(), userId)) continue;
            if (isAdmin(issue, userId) || Objects.equals(issue.getReporterId(), userId)) {
                result.add(toVO(review));
            }
        }
        return result;
    }

    @Transactional
    public ReviewVO approve(Long reviewId, Long reviewerId, String note) {
        IssueReview review = requirePending(reviewId);
        Issue issue = requireIssue(review.getIssueId());
        assertReviewer(issue, review, reviewerId);
        review.setStatus("APPROVED");
        review.setReviewerId(reviewerId);
        review.setReviewNote(note);
        review.setReviewedAt(LocalDateTime.now());
        issueReviewMapper.updateById(review);

        writeActivity(issue, reviewerId, "REVIEW_APPROVED", "通过了任务验收 r" + review.getRoundNo());
        refreshDeliverySnapshot(issue);
        notify(review.getSubmitterId(), issue, "TASK_REVIEW_APPROVED", "任务验收通过",
                issue.getIssueKey() + " 的验收已通过");
        return toVO(review);
    }

    @Transactional
    public ReviewVO reject(Long reviewId, Long reviewerId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "打回时必须填写原因");
        }
        IssueReview review = requirePending(reviewId);
        Issue issue = requireIssue(review.getIssueId());
        assertReviewer(issue, review, reviewerId);
        review.setStatus("REJECTED");
        review.setReviewerId(reviewerId);
        review.setReviewNote(note);
        review.setReviewedAt(LocalDateTime.now());
        issueReviewMapper.updateById(review);
        returnToInProgress(issue);

        writeActivity(issue, reviewerId, "REVIEW_REJECTED", "打回了任务验收 r" + review.getRoundNo());
        refreshDeliverySnapshot(issue);
        notify(review.getSubmitterId(), issue, "TASK_REVIEW_REJECTED", "任务验收被打回",
                issue.getIssueKey() + " 的验收被驳回：" + note);
        return toVO(review);
    }

    private void returnToInProgress(Issue issue) {
        issue.setStatus("IN_PROGRESS");
        if ("BUG".equals(issue.getType())) {
            issue.setBugStatus("IN_PROGRESS");
        }
        BoardColumn target = boardColumnMapper.selectList(new LambdaQueryWrapper<BoardColumn>()
                        .eq(BoardColumn::getProjectId, issue.getProjectId())
                        .orderByAsc(BoardColumn::getSortOrder))
                .stream()
                .filter(column -> {
                    String name = column.getName() == null ? "" : column.getName().toLowerCase();
                    return name.contains("进行") || name.contains("progress") || name.contains("doing");
                })
                .findFirst()
                .orElse(null);
        if (target != null) {
            issue.setColumnId(target.getId());
        }
        issueMapper.updateById(issue);
        try {
            webSocketService.sendBoardUpdate(issue.getProjectId(), BoardSyncEvent.moved(
                    issue.getProjectId(), issue.getId(), issue.getColumnId(), issue.getSortOrder()));
        } catch (Exception ignored) {
            // Notification/audit remain the source of truth if a live board session is unavailable.
        }
    }

    public IssueReview latest(Long issueId) {
        return issueReviewMapper.selectOne(new LambdaQueryWrapper<IssueReview>()
                .eq(IssueReview::getIssueId, issueId)
                .orderByDesc(IssueReview::getRoundNo)
                .last("LIMIT 1"));
    }

    private boolean existsPending(Long issueId) {
        return issueReviewMapper.exists(new LambdaQueryWrapper<IssueReview>()
                .eq(IssueReview::getIssueId, issueId)
                .eq(IssueReview::getStatus, "PENDING"));
    }

    private IssueReview requirePending(Long reviewId) {
        IssueReview review = issueReviewMapper.selectById(reviewId);
        if (review == null) throw new BusinessException(ResultCode.NOT_FOUND, "验收记录不存在");
        if (!"PENDING".equals(review.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该验收记录已处理");
        }
        return review;
    }

    private Issue requireIssue(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        return issue;
    }

    /** 包级可见：单测要锁"写门槛与读门槛同一个口径" */
    void assertProjectMember(Issue issue, Long userId) {
        // 口径必须与"读"一致：系统管理员 / 团队成员 / 团队管理员都能操作
        // （以前这里只认 team_members 行，系统管理员从全员任务流点进来能看却一操作就被拦）
        projectAccessService.requireProject(issue.getProjectId(), userId);
    }

    /**
     * 能不能提交验收：任务负责人，或管理员代为提交（成员休假/离职时得有人能把任务推完）。
     *
     * <p>包级可见是为了单测直接锁口径 —— 这条规则以前没有管理员逃生口，
     * 而同一个类里的"验收"分支却有，两套口径并存就是踩坑的源头（BUG-115）。</p>
     */
    void assertCanSubmit(Issue issue, Long submitterId) {
        if (issue.getAssigneeId() != null && !Objects.equals(issue.getAssigneeId(), submitterId)
                && !isAdmin(issue, submitterId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有任务负责人或管理员可以提交验收");
        }
        if (issue.getAssigneeId() == null && !Objects.equals(issue.getReporterId(), submitterId)
                && !isAdmin(issue, submitterId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "未指派任务只能由报告人或管理员提交验收");
        }
    }

    private void assertReviewer(Issue issue, IssueReview review, Long userId) {
        assertProjectMember(issue, userId);
        if (Objects.equals(review.getSubmitterId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能验收自己提交的结果");
        }
        boolean reporter = Objects.equals(issue.getReporterId(), userId);
        if (!isAdmin(issue, userId) && !reporter) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有项目管理员或报告人可以验收");
        }
    }

    private boolean isAdmin(Issue issue, Long userId) {
        if (accessControlService.isSystemAdmin(userId)) return true;
        Project project = projectMapper.selectById(issue.getProjectId());
        return project != null && (Objects.equals(project.getOwnerId(), userId)
                || teamService.isTeamAdmin(project.getTeamId(), userId));
    }

    private ReviewVO toVO(IssueReview review) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setIssueId(review.getIssueId());
        vo.setRoundNo(review.getRoundNo());
        vo.setSubmitterId(review.getSubmitterId());
        vo.setSubmitterName(username(review.getSubmitterId()));
        vo.setCompletionNote(review.getCompletionNote());
        vo.setEvidenceUrls(readList(review.getEvidenceUrls()));
        vo.setStatus(review.getStatus());
        vo.setReviewerId(review.getReviewerId());
        vo.setReviewerName(username(review.getReviewerId()));
        vo.setReviewNote(review.getReviewNote());
        vo.setSubmittedAt(review.getSubmittedAt());
        vo.setReviewedAt(review.getReviewedAt());
        return vo;
    }

    private String username(Long userId) {
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        return user == null ? "未知用户" : user.getUsername();
    }

    private void notify(Long userId, Issue issue, String type, String title, String content) {
        if (userId == null) return;
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
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

    private void refreshDeliverySnapshot(Issue issue) {
        try {
            deliveryAnalyticsService.refreshTodaySnapshot(issue.getProjectId());
        } catch (Exception e) {
            log.warn("刷新交付分析快照失败, projectId={}", issue.getProjectId(), e);
        }
    }

    private String writeJson(List<String> urls) {
        try {
            return objectMapper.writeValueAsString(urls == null ? List.of() : urls);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
