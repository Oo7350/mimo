package com.mimo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ProjectDTO;
import com.mimo.dto.ProjectDTO.ProjectVO;
import com.mimo.entity.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 审批通过后的「真正执行」环节（KNOWN-006）。
 *
 * <p>为什么要单独一个 Bean：执行必须跑在**独立事务**（{@code REQUIRES_NEW}）里。
 * 以前执行和审批状态更新在同一个事务里、异常又被 catch 掉，于是会出现两种坏情况：
 * 执行写到一半失败时，半截数据跟着审批状态一起提交；而执行彻底失败时，审批状态已是
 * APPROVED 却什么都没发生，且没有任何痕迹。
 *
 * <p>拆开之后：执行成功 → 内层事务提交；执行失败 → 内层事务整体回滚，外层只记录
 * FAILED + 失败原因，审批结果本身依然有效，可以走重试入口补执行。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalExecutionService {

    private final ProjectService projectService;
    private final IssueClaimService issueClaimService;
    private final ObjectMapper objectMapper;

    /**
     * 执行审批通过后的动作。失败时抛异常，由调用方记录到 exec_status。
     *
     * @param request 审批请求（执行成功后可能回填 projectId）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SuppressWarnings("unchecked")
    public void execute(ApprovalRequest request) {
        String targetType = request.getTargetType();
        String dataJson = request.getDataJson();
        if (targetType == null) {
            log.warn("审批请求缺少 targetType，跳过执行: approvalId={}", request.getId());
            return;
        }
        if (dataJson == null) {
            log.warn("审批请求缺少 dataJson，跳过执行: approvalId={}, targetType={}",
                    request.getId(), targetType);
            return;
        }

        Map<String, Object> data;
        try {
            data = objectMapper.readValue(dataJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审批数据解析失败: " + e.getOriginalMessage());
        }

        switch (targetType) {
            case "PROJECT_CREATE" -> createProject(request, data);
            case "PROJECT_DELETE" -> deleteProject(request, data);
            case "ISSUE_CLAIM" -> claimIssue(request, data);
            default -> log.warn("暂不支持的审批动作类型，跳过执行: approvalId={}, targetType={}",
                    request.getId(), targetType);
        }
    }

    /**
     * 认领任务：指派给申请人，并自动推进到工作流的「进行中」。
     */
    private void claimIssue(ApprovalRequest request, Map<String, Object> data) {
        Long issueId = toLong(data.get("issueId"));
        Long claimUserId = toLong(data.get("userId"));
        Long toColumnId = toLong(data.get("toColumnId"));
        if (issueId == null || claimUserId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "认领数据缺少 issueId / userId");
        }
        issueClaimService.applyClaim(issueId, claimUserId, toColumnId, request.getApproverId());
    }

    private Long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String && !((String) value).isEmpty()) return Long.parseLong((String) value);
        return null;
    }

    private void createProject(ApprovalRequest request, Map<String, Object> data) {
        ProjectDTO.CreateRequest createReq = new ProjectDTO.CreateRequest();
        createReq.setName((String) data.get("name"));
        createReq.setKey((String) data.get("key"));
        createReq.setTemplate((String) data.getOrDefault("template", "SCRUM"));
        createReq.setTeamId(request.getTeamId());

        ProjectVO created = projectService.create(createReq, request.getRequesterId());
        // 回填到内存对象，由外层统一持久化（内层只负责创建项目本身）
        request.setProjectId(created.getId());
    }

    private void deleteProject(ApprovalRequest request, Map<String, Object> data) {
        Number projectIdNum = (Number) data.get("projectId");
        if (projectIdNum == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审批数据缺少 projectId");
        }
        projectService.deleteProject(projectIdNum.longValue(), request.getRequesterId());
    }
}
