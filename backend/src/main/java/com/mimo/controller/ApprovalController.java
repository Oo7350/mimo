package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ApprovalRequestDTO;
import com.mimo.dto.ApprovalRequestVO;
import com.mimo.entity.User;
import com.mimo.mapper.UserMapper;
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
    private final UserMapper userMapper;

    /**
     * 创建审批请求
     */
    @PostMapping
    public Result<ApprovalRequestVO> create(@Valid @RequestBody ApprovalRequestDTO dto, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(approvalService.create(dto, userId));
    }

    /**
     * 获取所有待审批列表(仅系统admin)
     */
    @GetMapping("/pending")
    public Result<List<ApprovalRequestVO>> listAllPending(Authentication auth) {
        Object principal = auth.getPrincipal();
        Long userId = null;
        if (principal instanceof Number) {
            userId = ((Number) principal).longValue();
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!"ROLE_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可查看所有待审批请求");
        }
        return Result.success(approvalService.listAllPending());
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

    /**
     * 撤回审批请求（仅请求者可操作）
     */
    @PutMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        approvalService.withdraw(id, userId);
        return Result.successMessage("已撤回");
    }

    /**
     * 清理超时的审批请求（7天未处理自动拒绝）
     */
    @PostMapping("/cleanup-expired")
    public Result<Integer> cleanupExpired(Authentication auth) {
        Object principal = auth.getPrincipal();
        Long userId = null;
        if (principal instanceof Number) {
            userId = ((Number) principal).longValue();
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !"ROLE_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可清理超时请求");
        }
        int count = approvalService.cleanupExpiredRequests(7); // 7天超时
        return Result.success(count);
    }
}
