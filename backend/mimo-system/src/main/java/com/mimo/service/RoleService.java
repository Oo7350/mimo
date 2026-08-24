package com.mimo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.RoleDTO;
import com.mimo.entity.Permission;
import com.mimo.entity.Role;
import com.mimo.entity.RolePermission;
import com.mimo.entity.UserRole;
import com.mimo.mapper.PermissionMapper;
import com.mimo.mapper.RoleMapper;
import com.mimo.mapper.RolePermissionMapper;
import com.mimo.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    // ==================== 角色管理 ====================

    public List<RoleDTO.RoleVO> listRoles() {
        return roleMapper.findAllOrdered().stream().map(this::toVO).toList();
    }

    public RoleDTO.RoleVO getRole(Integer id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        return toVO(roleMapper.selectById(id));
    }

    @Transactional
    public RoleDTO.RoleVO saveRole(RoleDTO.SaveRoleRequest req) {
        if (req.getId() == null) {
            // 新建
            Role r = new Role();
            r.setName(req.getName());
            r.setCode(req.getCode());
            r.setIsSystem(0);
            r.setDescription(req.getDescription());
            r.setSortOrder(req.getSortOrder() == null ? 99 : req.getSortOrder());
            roleMapper.insert(r);
            replaceRolePermissions(r.getId(), req.getPermissions());
            return getRole(r.getId());
        }
        // 更新
        Role r = roleMapper.selectById(req.getId());
        if (r == null) throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        if (r.getIsSystem() == 1 && req.getCode() != null && !req.getCode().equals(r.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统内置角色编码不可修改");
        }
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        if (r.getIsSystem() == 0) {
            r.setCode(req.getCode());
        }
        r.setSortOrder(req.getSortOrder() == null ? r.getSortOrder() : req.getSortOrder());
        roleMapper.updateById(r);
        replaceRolePermissions(r.getId(), req.getPermissions());
        return getRole(r.getId());
    }

    @Transactional
    public void deleteRole(Integer id) {
        Role r = roleMapper.selectById(id);
        if (r == null) return;
        if (r.getIsSystem() == 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统内置角色不可删除");
        }
        // 检查是否有关联用户
        List<UserRole> userRoles = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        if (!userRoles.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "角色仍关联 " + userRoles.size() + " 个用户，请先解除");
        }
        rolePermissionMapper.delete(
                Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    // ==================== 权限查询 ====================

    public List<RoleDTO.PermissionVO> listPermissions() {
        return permissionMapper.selectList(null).stream()
                .sorted(Comparator.comparingInt(Permission::getSortOrder))
                .map(this::toPermVO).toList();
    }

    public List<RoleDTO.PermissionVO> listPermissionsByModule() {
        return listPermissions();
    }

    // ==================== 用户-角色分配 ====================

    @Transactional
    public void assignRole(RoleDTO.AssignRoleRequest req) {
        Role r = roleMapper.selectById(req.getRoleId());
        if (r == null) throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        if (!"GLOBAL".equals(req.getScopeType()) && req.getScopeId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "非 GLOBAL scope 必须提供 scopeId");
        }
        // 幂等：已存在则跳过
        UserRole exist = userRoleMapper.selectOne(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, req.getUserId())
                .eq(UserRole::getRoleId, req.getRoleId())
                .eq(UserRole::getScopeType, req.getScopeType())
                .eq(req.getScopeId() == null, UserRole::getScopeId, null));
        if (exist != null) return; // 已存在，幂等
        UserRole ur = new UserRole();
        ur.setUserId(req.getUserId());
        ur.setRoleId(req.getRoleId());
        ur.setScopeType(req.getScopeType());
        ur.setScopeId(req.getScopeId());
        userRoleMapper.insert(ur);
    }

    @Transactional
    public void revokeRole(Long userId, Integer roleId, String scopeType, Long scopeId) {
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId)
                .eq(UserRole::getScopeType, scopeType)
                .eq(scopeId != null, UserRole::getScopeId, scopeId));
    }

    public List<RoleDTO.RoleVO> listUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.findByUser(userId);
        if (userRoles.isEmpty()) return Collections.emptyList();
        Set<Integer> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        return roleMapper.selectBatchIds(roleIds).stream().map(this::toVO).toList();
    }

    /**
     * 查询用户的所有角色分配记录（含 scope 信息），用于 UI 展示与精准撤销
     */
    public List<RoleDTO.UserRoleAssignmentVO> listUserAssignments(Long userId) {
        List<UserRole> userRoles = userRoleMapper.findByUser(userId);
        if (userRoles.isEmpty()) return Collections.emptyList();
        Set<Integer> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        Map<Integer, Role> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        return userRoles.stream()
                .map(ur -> {
                    Role r = roleMap.get(ur.getRoleId());
                    if (r == null) return null;
                    RoleDTO.UserRoleAssignmentVO vo = new RoleDTO.UserRoleAssignmentVO();
                    vo.setUserRoleId(ur.getId().intValue());
                    vo.setUserId(ur.getUserId());
                    vo.setId(r.getId());
                    vo.setName(r.getName());
                    vo.setCode(r.getCode());
                    vo.setIsSystem(r.getIsSystem());
                    vo.setDescription(r.getDescription());
                    vo.setScopeType(ur.getScopeType());
                    vo.setScopeId(ur.getScopeId());
                    vo.setAssignedAt(ur.getCreatedAt());
                    return vo;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ==================== 权限校验核心 ====================

    /**
     * 判断用户在某 scope 下是否有权限
     *
     * @param userId   用户 ID
     * @param code     权限编码
     * @param scopeType 'GLOBAL'/'TEAM'/'PROJECT'
     * @param scopeId  scope 对应 ID，GLOBAL 时传 null
     *                  - PROJECT scope 时为 projectId，会自动叠加项目所属 team 的权限
     *                  - TEAM scope 时为 teamId，叠加 GLOBAL
     */
    public boolean hasPermission(Long userId, String code, String scopeType, Long scopeId) {
        // SUPER_ADMIN 直通（查找 GLOBAL 角色中是否有 SUPER_ADMIN）
        List<RoleDTO.RoleVO> userRoles = listUserRoles(userId);
        boolean isSuper = userRoles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
        if (isSuper) return true;

        List<Permission> perms;
        if ("PROJECT".equals(scopeType) && scopeId != null) {
            // 项目级校验：三层叠加 GLOBAL + 项目所属 TEAM + PROJECT
            perms = permissionMapper.findByUserForProject(userId, scopeId);
        } else if ("TEAM".equals(scopeType) && scopeId != null) {
            // 团队级校验：叠加 GLOBAL + TEAM
            perms = permissionMapper.findByUserAndScope(userId, scopeId);
        } else {
            // GLOBAL scope：仅查全局角色权限
            perms = permissionMapper.findByUserAndScope(userId, 0L);
        }
        return perms.stream().anyMatch(p -> p.getCode().equals(code));
    }

    /**
     * 简化版：仅校验 GLOBAL + 指定 scope
     */
    public boolean hasPermission(Long userId, String code) {
        return hasPermission(userId, code, "GLOBAL", null);
    }

    // ==================== 辅助方法 ====================

    private void replaceRolePermissions(Integer roleId, List<String> permCodes) {
        rolePermissionMapper.delete(
                Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, roleId));
        if (permCodes == null || permCodes.isEmpty()) return;
        List<Permission> all = permissionMapper.selectList(null);
        Map<String, Integer> codeToId = all.stream()
                .collect(Collectors.toMap(Permission::getCode, Permission::getId));
        for (String code : permCodes) {
            Integer pid = codeToId.get(code);
            if (pid == null) {
                log.warn("跳过未知权限编码: {}", code);
                continue;
            }
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }

    private RoleDTO.RoleVO toVO(Role r) {
        RoleDTO.RoleVO vo = new RoleDTO.RoleVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setCode(r.getCode());
        vo.setIsSystem(r.getIsSystem());
        vo.setDescription(r.getDescription());
        vo.setSortOrder(r.getSortOrder());
        vo.setCreatedAt(r.getCreatedAt());
        List<Permission> perms = permissionMapper.findByRoleId(r.getId());
        vo.setPermissions(perms.stream().map(Permission::getCode).toList());
        return vo;
    }

    private RoleDTO.PermissionVO toPermVO(Permission p) {
        RoleDTO.PermissionVO vo = new RoleDTO.PermissionVO();
        vo.setId(p.getId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setModule(p.getModule());
        vo.setDescription(p.getDescription());
        vo.setSortOrder(p.getSortOrder());
        return vo;
    }
}
