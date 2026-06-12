package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.TeamDTO.*;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
        Long userId = (Long) auth.getPrincipal();
        return Result.success(teamService.create(request, userId));
    }

    @GetMapping("/{id}")
    public Result<TeamVO> getById(@PathVariable Long id) {
        return Result.success(teamService.getById(id));
    }

    @GetMapping("/my")
    public Result<List<TeamVO>> myTeams(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(teamService.listByUser(userId));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> inviteMember(@Valid @RequestBody InviteRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.inviteMember(request, userId);
        return Result.successMessage("邀请成功");
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> listMembers(@PathVariable Long id) {
        return Result.success(teamService.listMembers(id));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> removeMember(@PathVariable Long teamId, @PathVariable Long userId, Authentication auth) {
        Long opId = (Long) auth.getPrincipal();
        teamService.removeMember(teamId, userId, opId);
        return Result.successMessage("移除成功");
    }

    @GetMapping("/{id}/role")
    public Result<String> getUserRole(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String role = teamService.getUserRoleInTeam(id, userId);
        return Result.success(role);
    }

    @DeleteMapping("/{teamId}")
    public Result<Void> deleteTeam(@PathVariable Long teamId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.deleteTeam(teamId, userId);
        return Result.successMessage("团队已删除");
    }
}
