package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.dto.RoleDTO.AssignRoleRequest;
import com.mimo.entity.Permission;
import com.mimo.entity.Role;
import com.mimo.entity.UserRole;
import com.mimo.mapper.PermissionMapper;
import com.mimo.mapper.RoleMapper;
import com.mimo.mapper.RolePermissionMapper;
import com.mimo.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @Mock
    private UserRoleMapper userRoleMapper;

    @Test
    void shouldBypassPermissionChecksForSuperAdmin() {
        when(userRoleMapper.findByUser(1L)).thenReturn(List.of(userRole(1L, "GLOBAL", null)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role("SUPER_ADMIN")));

        boolean allowed = service().hasPermission(1L, "issue.delete", "PROJECT", 5L);

        assertThat(allowed).isTrue();
        verify(permissionMapper, never()).findByUserForProject(anyLong(), anyLong());
        verify(permissionMapper, never()).findByUserAndScope(anyLong(), anyLong());
    }

    @Test
    void shouldUseProjectScopedStackedPermissions() {
        when(userRoleMapper.findByUser(2L)).thenReturn(List.of(userRole(2L, "PROJECT", 5L)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role("DEVELOPER")));
        when(permissionMapper.findByUserForProject(2L, 5L)).thenReturn(List.of(permission("issue.move")));

        assertThat(service().hasPermission(2L, "issue.move", "PROJECT", 5L)).isTrue();
        verify(permissionMapper, never()).findByUserAndScope(anyLong(), anyLong());
    }

    @Test
    void shouldReturnFalseWhenScopedPermissionMissing() {
        when(userRoleMapper.findByUser(3L)).thenReturn(List.of(userRole(3L, "TEAM", 8L)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role("TESTER")));
        when(permissionMapper.findByUserAndScope(3L, 8L)).thenReturn(List.of(permission("issue.view")));

        assertThat(service().hasPermission(3L, "project.delete", "TEAM", 8L)).isFalse();
    }

    @Test
    void shouldFallBackToGlobalScopeWhenNoScopeGiven() {
        when(userRoleMapper.findByUser(4L)).thenReturn(List.of(userRole(4L, "GLOBAL", null)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role("VIEWER")));
        when(permissionMapper.findByUserAndScope(4L, 0L)).thenReturn(List.of(permission("project.view")));

        assertThat(service().hasPermission(4L, "project.view")).isTrue();
    }

    @Test
    void shouldRejectScopedAssignmentWithoutScopeId() {
        when(roleMapper.selectById(1)).thenReturn(role("DEVELOPER"));
        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(2L);
        request.setRoleId(1);
        request.setScopeType("TEAM");

        assertThatThrownBy(() -> service().assignRole(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scopeId");

        verify(userRoleMapper, never()).insert(any());
    }

    @Test
    void shouldSkipDuplicatedRoleAssignment() {
        when(roleMapper.selectById(1)).thenReturn(role("DEVELOPER"));
        when(userRoleMapper.selectOne(any())).thenReturn(userRole(2L, "GLOBAL", null));
        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(2L);
        request.setRoleId(1);
        request.setScopeType("GLOBAL");

        service().assignRole(request);

        verify(userRoleMapper, never()).insert(any());
    }

    private RoleService service() {
        return new RoleService(roleMapper, permissionMapper, rolePermissionMapper, userRoleMapper);
    }

    private Role role(String code) {
        Role role = new Role();
        role.setId(1);
        role.setName(code);
        role.setCode(code);
        role.setIsSystem(1);
        return role;
    }

    private UserRole userRole(Long userId, String scopeType, Long scopeId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(1);
        userRole.setScopeType(scopeType);
        userRole.setScopeId(scopeId);
        return userRole;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setId(1);
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }
}
