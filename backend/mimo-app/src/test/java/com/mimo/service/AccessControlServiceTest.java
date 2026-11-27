package com.mimo.service;

import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /**
     * 批量判定用 LambdaQueryWrapper 拼条件，纯 Mockito 环境没有 MyBatis 上下文，
     * 得手动灌一次实体元数据，否则抛 "can not find lambda cache for this entity"
     * （同 WorkbenchServiceTest / RoleServiceTest 的做法）。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, TeamMember.class);
    }

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

    // ==================== 批量判定（v2.22.4 的 N+1 修复） ====================

    /**
     * 这批用例守的是同一件事：**批量版和逐人版必须给出同一个答案**。
     *
     * <p>为什么需要批量版：`/workbench/team` 给每个成员算"是不是管理员"，
     * 单人判定 6 条 SELECT。开发库 3 个人时一次请求几十毫秒，看不出问题；
     * 造了 100 个成员之后实测 606 条 SELECT / 433ms，而且越加人越慢。
     * 批量版把条数压到与人数无关的常数条 —— 但如果结论变了，这个"优化"就是 bug。
     */
    @Test
    void batchShouldAgreeWithSingleUserCheck() {
        long systemAdmin = 4L;   // 旧字段 users.role = ROLE_ADMIN
        long rbacAdmin = 3L;     // RBAC 全局权限（SUPER_ADMIN 等价）
        long teamAdmin = 1L;     // RBAC 团队作用域的 team.manage
        long plain = 2L;
        List<Long> ids = List.of(1L, 2L, 3L, 4L);

        // 批量侧
        when(userMapper.selectList(any())).thenReturn(List.of(userOf(systemAdmin, "ROLE_ADMIN")));
        when(roleService.usersWithPermission(ids, AccessControlService.PERM_SYSTEM_ADMIN))
                .thenReturn(Set.of(rbacAdmin));
        when(teamMemberMapper.selectList(any())).thenReturn(List.of());
        when(roleService.usersWithPermissionInScope(ids, AccessControlService.PERM_TEAM_ADMIN, "TEAM", TEAM))
                .thenReturn(Set.of(teamAdmin));

        // 逐人侧：同样的世界，一个一个问
        when(userMapper.selectById(systemAdmin)).thenReturn(userOf(systemAdmin, "ROLE_ADMIN"));
        when(userMapper.selectById(rbacAdmin)).thenReturn(userOf(rbacAdmin, "ROLE_MEMBER"));
        when(userMapper.selectById(teamAdmin)).thenReturn(userOf(teamAdmin, "ROLE_MEMBER"));
        when(userMapper.selectById(plain)).thenReturn(userOf(plain, "ROLE_MEMBER"));
        when(roleService.hasPermission(rbacAdmin, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(true);
        when(roleService.hasPermission(teamAdmin, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        when(roleService.hasPermission(plain, AccessControlService.PERM_SYSTEM_ADMIN)).thenReturn(false);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        // 只有需要走 RBAC 团队作用域的两个人会被问到（另外两人在上面就短路了）
        when(roleService.hasPermission(teamAdmin, AccessControlService.PERM_TEAM_ADMIN, "TEAM", TEAM)).thenReturn(true);
        when(roleService.hasPermission(plain, AccessControlService.PERM_TEAM_ADMIN, "TEAM", TEAM)).thenReturn(false);

        Set<Long> batched = service.filterTeamAdmins(ids, List.of(TEAM));
        Set<Long> oneByOne = new java.util.HashSet<>();
        for (Long id : ids) {
            if (service.isTeamAdmin(id, TEAM)) oneByOne.add(id);
        }

        assertThat(batched).isEqualTo(oneByOne);
        assertThat(batched).containsExactlyInAnyOrder(systemAdmin, rbacAdmin, teamAdmin);
    }

    @Test
    void batchShouldQueryOnceInsteadOfOncePerMember() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(roleService.usersWithPermission(any(), any())).thenReturn(Set.of());
        when(teamMemberMapper.selectList(any())).thenReturn(List.of());

        service.filterTeamAdmins(ids, List.of(TEAM));

        // 100 个成员：旧实现是 600 条，这里是常数条
        verify(userMapper, times(1)).selectList(any());
        verify(teamMemberMapper, times(1)).selectList(any());
    }

    @Test
    void batchShouldCountLegacyTeamAdminFromMembershipRow() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(roleService.usersWithPermission(any(), any())).thenReturn(Set.of());
        TeamMember legacyAdmin = new TeamMember();
        legacyAdmin.setUserId(USER);
        legacyAdmin.setTeamId(TEAM);
        legacyAdmin.setRole(AccessControlService.LEGACY_ROLE_ADMIN);
        when(teamMemberMapper.selectList(any())).thenReturn(List.of(legacyAdmin));

        assertThat(service.filterTeamAdmins(List.of(USER), List.of(TEAM))).containsExactly(USER);
    }

    @Test
    void batchShouldIgnoreLegacyFieldsWhenFallbackIsOff() {
        ReflectionTestUtils.setField(service, "legacyRoleFallback", false);
        when(roleService.usersWithPermission(any(), any())).thenReturn(Set.of());

        assertThat(service.filterTeamAdmins(List.of(USER), List.of(TEAM))).isEmpty();
        // 开关关掉后连旧字段表都不查（纯 RBAC）—— 两条 stub 都不该存在，所以这里也不 stub 它们
        verify(userMapper, never()).selectList(any());
        verify(teamMemberMapper, never()).selectList(any());
    }

    @Test
    void batchShouldShortCircuitOnEmptyInput() {
        assertThat(service.filterTeamAdmins(null, List.of(TEAM))).isEmpty();
        assertThat(service.filterTeamAdmins(List.of(), List.of(TEAM))).isEmpty();
        verify(roleService, never()).usersWithPermission(any(), any());
        verify(userMapper, never()).selectList(any());
    }

    private User userOf(long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }
}
