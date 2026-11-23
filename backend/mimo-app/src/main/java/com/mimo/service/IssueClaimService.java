package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueClaimResult;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 任务认领 — 把「谁来做」和「工作流走到哪一步」连起来。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>管理员 / 项目负责人认领：直接指派自己，并自动推进到「进行中」，开始计时。</li>
 *   <li>普通成员认领：生成 ISSUE_CLAIM 审批请求，管理员通过后由
 *       {@link ApprovalService} 回调 {@link #applyClaim}，同样自动推进。</li>
 *   <li>任务已被别人认领时拒绝，避免抢单。</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IssueClaimService {

    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ApprovalRequestMapper approvalRequestMapper;
    private final NotificationService notificationService;
    private final BoardService boardService;
    private final ProjectAccessService projectAccessService;
    private final ObjectMapper objectMapper;

    /** 认领任务（管理员直通，普通成员走审批） */
    @Transactional
    public IssueClaimResult claim(Long issueId, Long userId) {
        Issue issue = projectAccessService.requireIssue(issueId, userId);
        if (issue.getAssigneeId() != null) {
            if (issue.getAssigneeId().equals(userId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "你已经认领了这个任务");
            }
            User owner = userMapper.selectById(issue.getAssigneeId());
            throw new BusinessException(ResultCode.CONFLICT,
                    "该任务已被 " + (owner != null ? owner.getUsername() : "其他人") + " 认领");
        }

        Long targetColumnId = resolveInProgressColumn(issue);

        if (isProjectAdmin(issue.getProjectId(), userId)) {
            applyClaim(issue.getId(), userId, targetColumnId, userId);
            return IssueClaimResult.direct(issue.getId(), userId, targetColumnId);
        }

        // 已有待处理的同一认领申请 → 直接复用，避免重复刷审批
        ApprovalRequest pending = findPendingClaim(issue.getId(), userId);
        if (pending != null) {
            return IssueClaimResult.pending(issue.getId(), pending.getId());
        }

        Project project = projectMapper.selectById(issue.getProjectId());
        ApprovalRequest request = new ApprovalRequest();
        request.setTeamId(project != null ? project.getTeamId() : null);
        request.setProjectId(issue.getProjectId());
        request.setRequesterId(userId);
        request.setTargetType("ISSUE_CLAIM");
        request.setTitle("认领任务 " + issue.getIssueKey());
        request.setDescription("申请认领「" + issue.getTitle() + "」，通过后自动进入进行中");
        request.setDataJson(writeData(issue, userId, targetColumnId));
        request.setStatus("PENDING");
        approvalRequestMapper.insert(request);

        notifyAdmins(issue, userId, request.getId());
        return IssueClaimResult.pending(issue.getId(), request.getId());
    }

    /** 审批通过后真正落地认领：指派 + 推进到进行中 */
    @Transactional
    public void applyClaim(Long issueId, Long userId, Long toColumnId, Long operatorId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (issue.getAssigneeId() != null && !issue.getAssigneeId().equals(userId)) {
            throw new BusinessException(ResultCode.CONFLICT, "任务已被他人认领，无法执行");
        }

        // 导入或直接调 API 创建的任务可能没有列归属：先落到第一列，工作流才有「起点」
        if (issue.getColumnId() == null) {
            BoardColumn first = firstColumn(issue.getProjectId());
            if (first != null) {
                issue.setColumnId(first.getId());
            }
        }

        issue.setAssigneeId(userId);
        issueMapper.updateById(issue);

        if (toColumnId != null && !toColumnId.equals(issue.getColumnId())) {
            // 复用看板移动：工作流校验、计时、活动日志、WebSocket 广播一次到位
            boardService.moveIssue(issueId, toColumnId, null, operatorId != null ? operatorId : userId);
        }
    }

    private BoardColumn firstColumn(Long projectId) {
        return boardColumnMapper.selectOne(new LambdaQueryWrapper<BoardColumn>()
                .eq(BoardColumn::getProjectId, projectId)
                .orderByAsc(BoardColumn::getSortOrder)
                .last("limit 1"));
    }

    /** 找到「进行中」列；没有就返回 null（只指派、不移动） */
    public Long resolveInProgressColumn(Issue issue) {
        List<BoardColumn> columns = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>()
                        .eq(BoardColumn::getProjectId, issue.getProjectId())
                        .orderByAsc(BoardColumn::getSortOrder));
        BoardColumn current = columns.stream()
                .filter(c -> Objects.equals(c.getId(), issue.getColumnId()))
                .findFirst().orElse(null);
        for (BoardColumn column : columns) {
            if (!isInProgressColumn(column)) continue;
            // 已经在进行中或更后面的列时，不再回退
            if (current != null && column.getSortOrder() != null && current.getSortOrder() != null
                    && column.getSortOrder() <= current.getSortOrder()) {
                return null;
            }
            return column.getId();
        }
        return null;
    }

    private boolean isInProgressColumn(BoardColumn column) {
        String name = column.getName() == null ? "" : column.getName().toLowerCase(Locale.ROOT);
        return name.contains("进行") || name.contains("处理中") || name.contains("doing")
                || name.contains("in progress") || name.contains("in_progress");
    }

    private ApprovalRequest findPendingClaim(Long issueId, Long userId) {
        return approvalRequestMapper.selectOne(new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getTargetType, "ISSUE_CLAIM")
                .eq(ApprovalRequest::getRequesterId, userId)
                .eq(ApprovalRequest::getStatus, "PENDING")
                .like(ApprovalRequest::getDataJson, "\"issueId\":" + issueId)
                .last("limit 1"));
    }

    private String writeData(Issue issue, Long userId, Long toColumnId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("issueId", issue.getId());
        data.put("userId", userId);
        data.put("toColumnId", toColumnId);
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "认领数据序列化失败: " + e.getMessage());
        }
    }

    /** 通知项目负责人和团队管理员有新的认领申请 */
    private void notifyAdmins(Issue issue, Long requesterId, Long approvalId) {
        Project project = projectMapper.selectById(issue.getProjectId());
        if (project == null) return;
        Set<Long> adminIds = new LinkedHashSet<>();
        if (project.getOwnerId() != null) adminIds.add(project.getOwnerId());
        if (project.getTeamId() != null) {
            List<TeamMember> admins = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, project.getTeamId())
                    .eq(TeamMember::getRole, "ROLE_ADMIN"));
            admins.forEach(a -> adminIds.add(a.getUserId()));
        }
        if (adminIds.isEmpty()) return;
        User requester = userMapper.selectById(requesterId);
        String requesterName = requester != null ? requester.getUsername() : "成员";

        for (Long adminId : adminIds) {
            if (adminId.equals(requesterId)) continue;
            try {
                Notification notification = new Notification();
                notification.setUserId(adminId);
                notification.setType("APPROVAL_REQUEST");
                notification.setTitle("新的任务认领申请");
                notification.setContent(requesterName + " 申请认领「" + issue.getTitle() + "」");
                notification.setRelatedId(approvalId);
                notification.setRelatedType("APPROVAL_REQUEST");
                notification.setIsRead(0);
                notificationService.create(notification);
            } catch (Exception e) {
                log.warn("发送认领审批通知失败, adminId={}: {}", adminId, e.getMessage());
            }
        }
    }

    /** 是否项目管理员：系统管理员 / 项目负责人 / 团队管理员 */
    public boolean isProjectAdmin(Long projectId, Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null && "ROLE_ADMIN".equals(user.getRole())) return true;
        Project project = projectMapper.selectById(projectId);
        if (project == null) return false;
        if (Objects.equals(project.getOwnerId(), userId)) return true;
        if (project.getTeamId() == null) return false;
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, project.getTeamId())
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getRole, "ROLE_ADMIN")) != null;
    }
}
