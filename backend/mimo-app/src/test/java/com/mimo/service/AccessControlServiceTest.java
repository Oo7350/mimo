package com.mimo.service;

import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技术债「新旧权限判断并存」的回归：管理员判定必须只有一套口径。
 *
 * <p>改造前每个 Service/Controller 各写各的 {@code "ROLE_ADMIN".equals(user.getRole())}，
 * 公告还从 JWT 的授权项里读，口径互不一致 —— 这正是"个别接口可能绕过 RBAC"的来源。
 * 这里锁住三条约定：
 * <ol>
 *   <li>旧字段成立 → 通过（保持既有行为，回归零风险）</li>
 *   <li>旧字段不成立但 RBAC 成立 → 也通过（只在 RBAC 里有角色的账号不再被挡）</li>
 *   <li>两套都不成立 → 拒绝；迁移开关关掉后旧字段不再有任何效力</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    private static final long USER = 9L;
    private static final long TEAM = 3L;

    @Mock private RoleService roleService;
    @Mock private UserMapper userMapper;
    @Mock private TeamMemberMapper teamMemberMapper;

    private AccessControlService service;

    @BeforeEach
    void setUp() {
        service = new AccessControlService(roleService, userMapper, teamMemberMapper);
    }

    private User user(String role) {
        User u = new User();
        u.setId(USER);
        u.setRole(role);
        return u;
    }

    // ==================== 系统管理员 ====================

    @Test
    void legacyAdminRoleShouldCountAndShortCircuitRbac() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_ADMIN"));

        assertThat(service.isSystemAdmin(USER)).isTrue();

        // 旧字段命中就直接返回，不该再查 RBAC（否则每个读接口都多两次 SQL）
        verify(roleService, never()).hasPermission(anyLong(), any(), any(), any());
    }

    @Test
    void rbacPermissionShouldGrantAdminEvenWithoutLegacyRole() {
        // 旧字段是 MEMBER，但 RBAC 里配了 role.manage —— 改造前这种情况会被误拒
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(true);

        assertThat(service.isSystemAdmin(USER)).isTrue();
    }

    @Test
    void plainMemberShouldBeRejectedByBothModels() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);

        assertThat(service.isSystemAdmin(USER)).isFalse();
    }

    @Test
    void missingUserShouldNotBeAdmin() {
        when(userMapper.selectById(USER)).thenReturn(null);
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);

        assertThat(service.isSystemAdmin(USER)).isFalse();
        assertThat(service.isSystemAdmin(null)).isFalse();
    }

    // ==================== 团队管理员 ====================

    @Test
    void legacyTeamAdminRoleShouldCount() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        when(teamMemberMapper.selectOne(any())).thenReturn(new TeamMember());

        assertThat(service.isTeamAdmin(USER, TEAM)).isTrue();
    }

    @Test
    void rbacTeamPermissionShouldGrantTeamAdmin() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(roleService.hasPermission(USER, AccessControlService.PERM_TEAM_ADMIN, "TEAM", TEAM)).thenReturn(true);

        assertThat(service.isTeamAdmin(USER, TEAM)).isTrue();
    }

    @Test
    void systemAdminShouldCountAsTeamAdmin() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_ADMIN"));

        assertThat(service.isTeamAdmin(USER, TEAM)).isTrue();
        // 系统管理员直接过，不该再去查团队关系
        verify(teamMemberMapper, never()).selectOne(any());
    }

    @Test
    void teamAdminShouldBeFalseWithoutTeamId() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);

        assertThat(service.isTeamAdmin(USER, null)).isFalse();
    }

    // ==================== 迁移开关 ====================

    @Test
    void turningOffLegacyFallbackShouldIgnoreLegacyRole() {
        ReflectionTestUtils.setField(service, "legacyRoleFallback", false);
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);

        // 开关关掉后，users.role = ROLE_ADMIN 不再有任何效力，连查都不查
        assertThat(service.isSystemAdmin(USER)).isFalse();
        verify(userMapper, never()).selectById(anyLong());
        assertThat(service.isLegacyRoleFallbackEnabled()).isFalse();
    }

    @Test
    void legacyFallbackShouldBeOnByDefault() {
        assertThat(service.isLegacyRoleFallbackEnabled()).isTrue();
    }

    @Test
    void teamPermissionCheckShouldUseTeamScope() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(roleService.hasPermission(USER, AccessControlService.PERM_TEAM_ADMIN, "TEAM", TEAM)).thenReturn(false);

        assertThat(service.isTeamAdmin(USER, TEAM)).isFalse();
        // 团队判定必须落在 TEAM 作用域上，不能用全局权限冒充
        verify(roleService).hasPermission(eq(USER), eq(AccessControlService.PERM_TEAM_ADMIN), eq("TEAM"), eq(TEAM));
    }

    // ==================== 管理范围（前端入口的唯一来源，BUG-113） ====================

    /**
     * 这是 BUG-113 的回归：前端曾自己用权限码算"是不是管理员"，
     * 漏掉了 `team_members.role = ROLE_ADMIN` 这条旧字段兜底，
     * 导致后端放行（scope=TEAM）而界面把入口藏了。
     * 现在结论由后端给，前端只读 —— 这里锁住三个取值。
     */
    @Test
    void adminScopeShouldBeSystemForSystemAdmin() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_ADMIN"));

        assertThat(service.resolveAdminScope(USER)).isEqualTo(AccessControlService.ADMIN_SCOPE_SYSTEM);
        // 系统管理员不必再查他属于哪些团队
        verify(teamMemberMapper, never()).selectList(any());
    }

    @Test
    void adminScopeShouldBeTeamForLegacyTeamAdmin() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        TeamMember membership = new TeamMember();
        membership.setTeamId(TEAM);
        membership.setUserId(USER);
        when(teamMemberMapper.selectList(any())).thenReturn(java.util.List.of(membership));
        when(teamMemberMapper.selectOne(any())).thenReturn(new TeamMember());

        assertThat(service.resolveAdminScope(USER)).isEqualTo(AccessControlService.ADMIN_SCOPE_TEAM);
    }

    @Test
    void adminScopeShouldBeNoneForPlainMember() {
        when(userMapper.selectById(USER)).thenReturn(user("ROLE_MEMBER"));
        when(roleService.hasPermission(USER, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        when(teamMemberMapper.selectList(any())).thenReturn(java.util.List.of());
        assertThat(service.resolveAdminScope(USER)).isEqualTo(AccessControlService.ADMIN_SCOPE_NONE);
        assertThat(service.resolveAdminScope(null)).isEqualTo(AccessControlService.ADMIN_SCOPE_NONE);
    }
}
