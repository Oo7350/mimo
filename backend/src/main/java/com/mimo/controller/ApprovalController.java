package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.ApprovalRequestDTO;
import com.mimo.dto.ApprovalRequestVO;
import com.mimo.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * 创建审批请求
     */
    @PostMapping
    public Result<ApprovalRequestVO> create(@Valid @RequestBody ApprovalRequestDTO dto, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(approvalService.create(dto, userId));
    }

    /**
     * 获取团队待审批列表
     */
    @GetMapping("/team/{teamId}/pending")
    public Result<List<ApprovalRequestVO>> listPending(@PathVariable Long teamId, Authentication auth) {
        // TODO: 验证操作者是该团队管理员
        return Result.success(approvalService.listPendingByTeam(teamId));
    }

    /**
     * 获取我的审批请求
     */
    @GetMapping("/my")
    public Result<List<ApprovalRequestVO>> myRequests(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(approvalService.listMyRequests(userId));
    }

    /**
     * 审批通过
     */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        approvalService.approve(id, userId);
        return Result.successMessage("审批通过");
    }

    /**
     * 审批拒绝
     */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        approvalService.reject(id, userId, reason);
        return Result.successMessage("已拒绝");
    }
}
