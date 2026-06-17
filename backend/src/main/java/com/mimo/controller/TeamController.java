package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.TeamDTO.*;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public Result<TeamVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(teamService.create(request, userId));
    }

    @GetMapping("/{id}")
    public Result<TeamVO> getById(@PathVariable Long id) {
        return Result.success(teamService.getById(id));
    }

    @GetMapping("/my")
    public Result<List<TeamVO>> myTeams(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(teamService.listByUser(userId));
    }

    @PostMapping("/invite")
    public Result<Void> inviteMember(@Valid @RequestBody InviteRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        teamService.inviteMember(request, userId);
        return Result.successMessage("邀请成功");
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> listMembers(@PathVariable Long id) {
        return Result.success(teamService.listMembers(id));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long teamId, @PathVariable Long userId, Authentication auth) {
        Long opId = getLongPrincipal(auth);
        teamService.removeMember(teamId, userId, opId);
        return Result.successMessage("移除成功");
    }

    @GetMapping("/{id}/role")
    public Result<String> getUserRole(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        String role = teamService.getUserRoleInTeam(id, userId);
        return Result.success(role);
    }

    /**
     * 退出团队（非群主可用）
     */
    @PostMapping("/{teamId}/leave")
    public Result<Void> leaveTeam(@PathVariable Long teamId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        teamService.leaveTeam(teamId, userId);
        return Result.successMessage("已退出团队");
    }

    /**
     * 转让群主（仅当前群主可用）
     */
    @PutMapping("/{teamId}/transfer-owner")
    public Result<Void> transferOwner(@PathVariable Long teamId, @RequestParam Long newOwnerId, Authentication auth) {
        Long currentUserId = getLongPrincipal(auth);
        teamService.transferOwner(teamId, currentUserId, newOwnerId);
        return Result.successMessage("群主转让成功");
    }

    /**
     * 指定管理员（仅群主可用）
     */
    @PutMapping("/{teamId}/members/{userId}/set-admin")
    public Result<Void> setAdmin(@PathVariable Long teamId, @PathVariable Long userId, Authentication auth) {
        Long operatorId = getLongPrincipal(auth);
        teamService.setMemberRole(teamId, userId, operatorId, "ROLE_ADMIN");
        return Result.successMessage("已设为管理员");
    }

    /**
     * 取消管理员身份（仅群主可用）
     */
    @PutMapping("/{teamId}/members/{userId}/unset-admin")
    public Result<Void> unsetAdmin(@PathVariable Long teamId, @PathVariable Long userId, Authentication auth) {
        Long operatorId = getLongPrincipal(auth);
        teamService.setMemberRole(teamId, userId, operatorId, "ROLE_MEMBER");
        return Result.successMessage("已取消管理员");
    }

    @DeleteMapping("/{teamId}")
    public Result<Void> deleteTeam(@PathVariable Long teamId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        teamService.deleteTeam(teamId, userId);
        return Result.successMessage("团队已删除");
    }

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
