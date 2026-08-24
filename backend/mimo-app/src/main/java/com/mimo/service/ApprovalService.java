package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ApprovalRequestDTO;
import com.mimo.dto.ApprovalRequestVO;
import com.mimo.dto.ProjectDTO.ProjectVO;
import com.mimo.entity.*;
import com.mimo.mapper.ApprovalRequestMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.NotificationMapper;
import com.mimo.entity.Notification;
import com.mimo.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestMapper approvalRequestMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

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
     * 获取所有待审批列表(仅系统admin)
     */
    public List<ApprovalRequestVO> listAllPending() {
        List<ApprovalRequest> requests = approvalRequestMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRequest>()
                                .eq(ApprovalRequest::getStatus, "PENDING")
                                .orderByDesc(ApprovalRequest::getCreatedAt));
        return toVOList(requests);
    }

    /**
     * 获取团队待审批列表(管理员可见)
     */
    public List<ApprovalRequestVO> listPendingByTeam(Long teamId) {
        List<ApprovalRequest> requests = approvalRequestMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRequest>()
                                .eq(ApprovalRequest::getTeamId, teamId)
                                .eq(ApprovalRequest::getStatus, "PENDING")
                                .orderByDesc(ApprovalRequest::getCreatedAt));
        return toVOList(requests);
    }

    /**
     * 获取我的审批请求
     */
    public List<ApprovalRequestVO> listMyRequests(Long userId) {
        List<ApprovalRequest> requests = approvalRequestMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRequest>()
                                .eq(ApprovalRequest::getRequesterId, userId)
                                .orderByDesc(ApprovalRequest::getCreatedAt));
        return toVOList(requests);
    }

    /**
     * 批量转VO，避免N+1查询
     */
    private List<ApprovalRequestVO> toVOList(List<ApprovalRequest> requests) {
        if (requests.isEmpty()) return List.of();
        // 批量预加载
        Set<Long> teamIds = requests.stream().map(ApprovalRequest::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> projectIds = requests.stream().map(ApprovalRequest::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> requesterIds = requests.stream().map(ApprovalRequest::getRequesterId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> approverIds = requests.stream().map(ApprovalRequest::getApproverId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Team> teamMap = teamIds.isEmpty() ? Collections.emptyMap() :
                teamMapper.selectBatchIds(teamIds).stream().collect(Collectors.toMap(Team::getId, t -> t));
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap() :
                projectMapper.selectBatchIds(projectIds).stream().collect(Collectors.toMap(Project::getId, p -> p));

        Set<Long> allUserIds = new HashSet<>(); allUserIds.addAll(requesterIds); allUserIds.addAll(approverIds);
        Map<Long, User> userMap = allUserIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(allUserIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        return requests.stream().map(r -> toVO(r, teamMap, projectMap, userMap)).collect(Collectors.toList());
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

        // 通知申请人审批已通过
        sendApprovalNotification(request, "APPROVED", null);

        // 执行实际操作
        executeApprovedAction(request);
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

        // 通知申请人审批被拒绝
        sendApprovalNotification(request, "REJECTED", reason);
    }

    /**
     * 撤回审批请求（仅请求者可操作）
     */
    @Transactional
    public void withdraw(Long requestId, Long requesterId) {
        ApprovalRequest request = approvalRequestMapper.selectById(requestId);
        if (request == null) throw new BusinessException(ResultCode.NOT_FOUND, "审批请求不存在");
        
        // 验证是请求者本人
        if (!request.getRequesterId().equals(requesterId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能撤回自己的审批请求");
        }
        
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能撤回待处理的请求");
        }
        
        request.setStatus("WITHDRAWN");
        request.setRejectReason("用户主动撤回");
        approvalRequestMapper.updateById(request);
    }

    /**
     * 清理超时的审批请求（超过指定天数未处理自动拒绝）
     */
    @Transactional
    public int cleanupExpiredRequests(int expireDays) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(expireDays);
        
        List<ApprovalRequest> expiredRequests = approvalRequestMapper.selectList(
                new LambdaQueryWrapper<ApprovalRequest>()
                        .eq(ApprovalRequest::getStatus, "PENDING")
                        .lt(ApprovalRequest::getCreatedAt, expireTime));
        
        int count = 0;
        for (ApprovalRequest request : expiredRequests) {
            request.setStatus("EXPIRED");
            request.setRejectReason("超时自动拒绝（" + expireDays + "天未处理）");
            approvalRequestMapper.updateById(request);
            count++;
        }
        
        return count;
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

    /**
     * 执行审批通过后的实际操作
     */
    @SuppressWarnings("unchecked")
    private void executeApprovedAction(ApprovalRequest request) {
        try {
            String targetType = request.getTargetType();
            String dataJson = request.getDataJson();

            if ("PROJECT_CREATE".equals(targetType) && dataJson != null) {
                // 创建项目
                Map<String, Object> projectData = objectMapper.readValue(dataJson, Map.class);
                com.mimo.dto.ProjectDTO.CreateRequest createReq = new com.mimo.dto.ProjectDTO.CreateRequest();
                createReq.setName((String) projectData.get("name"));
                createReq.setKey((String) projectData.get("key"));
                createReq.setTemplate((String) projectData.getOrDefault("template", "SCRUM"));
                createReq.setTeamId(request.getTeamId());
                
                ProjectVO createdProject = projectService.create(createReq, request.getRequesterId());
                // 更新审批请求的projectId
                request.setProjectId(createdProject.getId());
                approvalRequestMapper.updateById(request);
                
            } else if ("PROJECT_DELETE".equals(targetType) && dataJson != null) {
                // 删除项目
                Map<String, Object> deleteData = objectMapper.readValue(dataJson, Map.class);
                Number projectIdNum = (Number) deleteData.get("projectId");
                if (projectIdNum != null) {
                    projectService.deleteProject(projectIdNum.longValue(), request.getRequesterId());
                }
            }
            // 其他类型暂不处理，可后续扩展
            
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审批数据解析失败: " + e.getMessage());
        } catch (Exception e) {
            // 审批已通过，但执行失败 - 记录日志但不回滚审批状态
            // 实际生产环境应该有补偿机制或重试队列
            e.printStackTrace();
        }
    }

    private ApprovalRequestVO toVO(ApprovalRequest entity) {
        return toVO(entity,
                entity.getTeamId() != null ? Map.of(entity.getTeamId(), teamMapper.selectById(entity.getTeamId())) : Collections.emptyMap(),
                entity.getProjectId() != null ? Map.of(entity.getProjectId(), projectMapper.selectById(entity.getProjectId())) : Collections.emptyMap(),
                Stream.of(entity.getRequesterId(), entity.getApproverId()).filter(Objects::nonNull)
                        .collect(Collectors.toList()).isEmpty() ? Collections.emptyMap() :
                        userMapper.selectBatchIds(Stream.of(entity.getRequesterId(), entity.getApproverId()).filter(Objects::nonNull).collect(Collectors.toList()))
                                .stream().collect(Collectors.toMap(User::getId, u -> u))
        );
    }

    private ApprovalRequestVO toVO(ApprovalRequest entity, Map<Long, Team> teamMap, Map<Long, Project> projectMap, Map<Long, User> userMap) {
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
            Team team = teamMap.get(entity.getTeamId());
            if (team != null) vo.setTeamName(team.getName());
        }
        if (entity.getProjectId() != null) {
            Project project = projectMap.get(entity.getProjectId());
            if (project != null) vo.setProjectName(project.getName());
        }
        if (entity.getRequesterId() != null) {
            User user = userMap.get(entity.getRequesterId());
            if (user != null) vo.setRequesterUsername(user.getUsername());
        }
        if (entity.getApproverId() != null) {
            User user = userMap.get(entity.getApproverId());
            if (user != null) vo.setApproverUsername(user.getUsername());
        }
        return vo;
    }

    /**
     * 发送审批结果通知给申请人
     */
    private void sendApprovalNotification(ApprovalRequest request, String status, String reason) {
        try {
            Notification notification = new Notification();
            notification.setUserId(request.getRequesterId());
            notification.setType("APPROVAL_" + status);

            boolean approved = "APPROVED".equals(status);
            notification.setTitle(approved ? "审批已通过" : "审批被拒绝");
            StringBuilder content = new StringBuilder();
            content.append("您的审批请求「").append(request.getTitle()).append("」");
            content.append(approved ? "已被管理员通过" : "被管理员拒绝");
            if (!approved && reason != null && !reason.isEmpty()) {
                content.append("，原因：").append(reason);
            }
            notification.setContent(content.toString());
            notification.setRelatedId(request.getId());
            notification.setRelatedType("APPROVAL_REQUEST");
            notification.setIsRead(0);

            notificationService.create(notification);
        } catch (Exception e) {
            log.warn("发送审批通知失败: {}", e.getMessage());
        }
    }
}
