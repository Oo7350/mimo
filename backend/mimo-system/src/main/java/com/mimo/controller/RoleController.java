package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.RequirePermission;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.RoleDTO;
import com.mimo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** 列出所有角色（带权限编码） */
    @GetMapping
    public Result<List<RoleDTO.RoleVO>> list() {
        return Result.success(roleService.listRoles());
    }

    @GetMapping("/{id}")
    public Result<RoleDTO.RoleVO> get(@PathVariable Integer id) {
        return Result.success(roleService.getRole(id));
    }

    /** 列出所有权限点 */
    @GetMapping("/permissions")
    public Result<List<RoleDTO.PermissionVO>> listPermissions() {
        return Result.success(roleService.listPermissions());
    }

    /** 新建/更新角色 — 仅 SUPER_ADMIN 可操作 */
    @PostMapping
    @RequirePermission("role.manage")
    public Result<RoleDTO.RoleVO> save(@RequestBody RoleDTO.SaveRoleRequest request) {
        return Result.success(roleService.saveRole(request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("role.manage")
    public Result<Void> delete(@PathVariable Integer id) {
        roleService.deleteRole(id);
        return Result.success(null);
    }

    /** 给用户分配角色 */
    @PostMapping("/assign")
    @RequirePermission("role.manage")
    public Result<Void> assign(@RequestBody RoleDTO.AssignRoleRequest request) {
        roleService.assignRole(request);
        return Result.success(null);
    }

    /** 取消用户角色 */
    @DeleteMapping("/assign")
    @RequirePermission("role.manage")
    public Result<Void> revoke(@RequestParam Long userId,
                                @RequestParam Integer roleId,
                                @RequestParam(defaultValue = "GLOBAL") String scopeType,
                                @RequestParam(required = false) Long scopeId) {
        roleService.revokeRole(userId, roleId, scopeType, scopeId);
        return Result.success(null);
    }

    /** 查询用户拥有的角色列表（仅 role 维度） */
    @GetMapping("/user/{userId}")
    public Result<List<RoleDTO.RoleVO>> userRoles(@PathVariable Long userId) {
        return Result.success(roleService.listUserRoles(userId));
    }

    /** 查询用户的所有角色分配记录（含 scope 信息，用于 UI 展示与精准撤销） */
    @GetMapping("/user/{userId}/assignments")
    public Result<List<RoleDTO.UserRoleAssignmentVO>> userAssignments(@PathVariable Long userId) {
        return Result.success(roleService.listUserAssignments(userId));
    }

    /** 查询当前登录用户的权限编码列表（前端用于按钮控制） */
    @GetMapping("/me/permissions")
    public Result<List<String>> myPermissions(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        // 列出 GLOBAL 范围的全部权限
        var roles = roleService.listUserRoles(userId);
        if (roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()))) {
            return Result.success(roleService.listPermissions().stream()
                    .map(RoleDTO.PermissionVO::getCode).toList());
        }
        return Result.success(roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .distinct()
                .toList());
    }

    private Long getLongPrincipal(Authentication auth) {
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof Long l) return l;
        if (p instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
