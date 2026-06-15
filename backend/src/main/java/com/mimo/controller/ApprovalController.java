package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ApprovalRequestDTO;
import com.mimo.dto.ApprovalRequestVO;
import com.mimo.entity.User;
import com.mimo.mapper.UserMapper;
import com.mimo.service.ApprovalService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final TeamService teamService;

    @Value("${approval.expire-days:7}")
    private int expireDays;

    @PostMapping
    public Result<ApprovalRequestVO> create(@Valid @RequestBody ApprovalRequestDTO dto, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(approvalService.create(dto, userId));
    }

    /**
     * 获取所有待审批列表(仅系统admin)
     */
    @GetMapping("/pending")
    public Result<List<ApprovalRequestVO>> listAllPending(Authentication auth) {
        assertAdmin(auth);
        return Result.success(approvalService.listAllPending());
    }

    /**
     * 获取团队待审批列表(需团队管理员)
     */
    @GetMapping("/team/{teamId}/pending")
    public Result<List<ApprovalRequestVO>> listPending(@PathVariable Long teamId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        if (!teamService.isTeamAdmin(teamId, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅团队管理员可查看待审批请求");
        }
        return Result.success(approvalService.listPendingByTeam(teamId));
    }

    @GetMapping("/my")
    public Result<List<ApprovalRequestVO>> myRequests(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(approvalService.listMyRequests(userId));
    }

    /**
     * 审批通过(需管理员)
     */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        approvalService.approve(id, userId);
        return Result.successMessage("审批通过");
    }

    /**
     * 审批拒绝(需管理员)
     */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        approvalService.reject(id, userId, reason);
        return Result.successMessage("已拒绝");
    }

    /**
     * 撤回审批请求（仅请求者可操作）
     */
    @PutMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        approvalService.withdraw(id, userId);
        return Result.successMessage("已撤回");
    }

    /**
     * 清理超时的审批请求（7天未处理自动拒绝）
     */
    @PostMapping("/cleanup-expired")
    public Result<Integer> cleanupExpired(Authentication auth) {
        assertAdmin(auth);
        int count = approvalService.cleanupExpiredRequests(expireDays);
        return Result.success(count);
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    private Long assertAdmin(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!"ROLE_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可操作");
        }
        return userId;
    }
}
