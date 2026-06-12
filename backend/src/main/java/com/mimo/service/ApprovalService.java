package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ApprovalRequestDTO;
import com.mimo.dto.ApprovalRequestVO;
import com.mimo.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestMapper approvalRequestMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final ProjectMapper projectMapper;

    /**
     * 创建审批请求
     */
    @Transactional
    public ApprovalRequestVO create(ApprovalRequestDTO dto, Long requesterId) {
        // 验证用户是该团队成员
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, dto.getTeamId())
                .eq(TeamMember::getUserId, requesterId));
        if (member == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您不是该团队成员");
        }

        ApprovalRequest request = new ApprovalRequest();
        request.setTeamId(dto.getTeamId());
        request.setProjectId(dto.getProjectId());
        request.setRequesterId(requesterId);
        request.setTargetType(dto.getTargetType());
        request.setTitle(dto.getTitle());
        request.setDescription(dto.getDescription());
        request.setDataJson(dto.getDataJson());
        request.setStatus("PENDING");
        approvalRequestMapper.insert(request);

        return toVO(request);
    }

    /**
     * 获取团队待审批列表(管理员可见)
     */
    public List<ApprovalRequestVO> listPendingByTeam(Long teamId) {
        return approvalRequestMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRequest>()
                                .eq(ApprovalRequest::getTeamId, teamId)
                                .eq(ApprovalRequest::getStatus, "PENDING")
                                .orderByDesc(ApprovalRequest::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 获取我的审批请求
     */
    public List<ApprovalRequestVO> listMyRequests(Long userId) {
        return approvalRequestMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRequest>()
                                .eq(ApprovalRequest::getRequesterId, userId)
                                .orderByDesc(ApprovalRequest::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 审批通过
     */
    @Transactional
    public void approve(Long requestId, Long approverId) {
        ApprovalRequest request = approvalRequestMapper.selectById(requestId);
        if (request == null) throw new BusinessException(ResultCode.NOT_FOUND, "审批请求不存在");
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该请求已被处理");
        }

        // 验证审批人是团队管理员或系统admin
        if (!isAdmin(approverId, request.getTeamId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以审批");
        }

        request.setStatus("APPROVED");
        request.setApproverId(approverId);
        request.setApprovedAt(LocalDateTime.now());
        approvalRequestMapper.updateById(request);
    }

    /**
     * 审批拒绝
     */
    @Transactional
    public void reject(Long requestId, Long approverId, String reason) {
        ApprovalRequest request = approvalRequestMapper.selectById(requestId);
        if (request == null) throw new BusinessException(ResultCode.NOT_FOUND, "审批请求不存在");
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该请求已被处理");
        }

        // 验证审批人是团队管理员或系统admin
        if (!isAdmin(approverId, request.getTeamId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以审批");
        }

        request.setStatus("REJECTED");
        request.setApproverId(approverId);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectReason(reason);
        approvalRequestMapper.updateById(request);
    }

    /**
     * 检查是否为管理员(系统admin 或 团队admin)
     */
    private boolean isAdmin(Long userId, Long teamId) {
        // 系统admin
        User user = userMapper.selectById(userId);
        if (user != null && "ROLE_ADMIN".equals(user.getRole())) return true;

        // 团队admin
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getRole, "ROLE_ADMIN"));
        return member != null;
    }

    private ApprovalRequestVO toVO(ApprovalRequest entity) {
        ApprovalRequestVO vo = new ApprovalRequestVO();
        vo.setId(entity.getId());
        vo.setTeamId(entity.getTeamId());
        vo.setProjectId(entity.getProjectId());
        vo.setRequesterId(entity.getRequesterId());
        vo.setTargetType(entity.getTargetType());
        vo.setTitle(entity.getTitle());
        vo.setDescription(entity.getDescription());
        vo.setDataJson(entity.getDataJson());
        vo.setStatus(entity.getStatus());
        vo.setApproverId(entity.getApproverId());
        vo.setApprovedAt(entity.getApprovedAt());
        vo.setRejectReason(entity.getRejectReason());
        vo.setCreatedAt(entity.getCreatedAt());

        // 填充关联名称
        if (entity.getTeamId() != null) {
            Team team = teamMapper.selectById(entity.getTeamId());
            if (team != null) vo.setTeamName(team.getName());
        }
        if (entity.getProjectId() != null) {
            Project project = projectMapper.selectById(entity.getProjectId());
            if (project != null) vo.setProjectName(project.getName());
        }
        if (entity.getRequesterId() != null) {
            User user = userMapper.selectById(entity.getRequesterId());
            if (user != null) vo.setRequesterUsername(user.getUsername());
        }
        if (entity.getApproverId() != null) {
            User user = userMapper.selectById(entity.getApproverId());
            if (user != null) vo.setApproverUsername(user.getUsername());
        }
        return vo;
    }
}
