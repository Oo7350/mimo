package com.mimo.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mimo.entity.Role;
import com.mimo.entity.User;
import com.mimo.entity.UserLevel;
import com.mimo.entity.UserRole;
import com.mimo.mapper.RoleMapper;
import com.mimo.mapper.UserLevelMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 空库引导的护栏（v2.22.2 / BUG-123）。
 *
 * <p>这个类只在"空库 + 显式配置"时动手，但它一旦做错，代价是
 * **启动直接失败**（本机演练时先后栽过两次：
 * 一次是抢在建表前面查表，一次是重复插铭牌撞唯一键）。所以每条分支都要有用例。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserLevelMapper userLevelMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap(String username, String password) {
        return new AdminBootstrap(userMapper, userLevelMapper, roleMapper, userRoleMapper,
                passwordEncoder, username, password, "admin@mimo.local");
    }

    @Test
    void shouldDoNothingWhenNotConfigured() {
        bootstrap("", "123456").run(null);

        verifyNoInteractions(userMapper, userLevelMapper, roleMapper, userRoleMapper, passwordEncoder);
    }

    @Test
    void shouldNotGuessPasswordWhenOnlyUsernameConfigured() {
        // 只配用户名就当"半个开启"是最危险的做法：猜出来的默认口令会跟着上生产
        bootstrap("admin", "   ").run(null);

        verifyNoInteractions(userMapper, userLevelMapper, roleMapper, userRoleMapper);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldNotTouchExistingAccount() {
        User existing = new User();
        existing.setId(7L);
        existing.setUsername("admin");
        when(userMapper.selectOne(any())).thenReturn(existing);

        bootstrap(" admin ", "123456").run(null);

        verify(userMapper, never()).insert(any(User.class));
        verify(passwordEncoder, never()).encode(any());
        // 幂等：重启不该把用户改过的密码重置回默认口令
    }

    @Test
    void shouldCreateAdminWithBadgeAndGlobalSuperAdminGrant() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$hashed");
        Role superAdmin = new Role();
        superAdmin.setId(3);
        superAdmin.setCode("SUPER_ADMIN");
        when(roleMapper.selectOne(any())).thenReturn(superAdmin);
        when(userLevelMapper.selectCount(any())).thenReturn(0L);
        when(userRoleMapper.selectCount(any())).thenReturn(0L);

        bootstrap("admin", "123456").run(null);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("admin");
        assertThat(savedUser.getValue().getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(savedUser.getValue().getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(savedUser.getValue().getEmail()).isEqualTo("admin@mimo.local");

        ArgumentCaptor<UserLevel> badge = ArgumentCaptor.forClass(UserLevel.class);
        verify(userLevelMapper).insert(badge.capture());
        assertThat(badge.getValue().getLevelName()).isEqualTo("L1");

        ArgumentCaptor<UserRole> grant = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(grant.capture());
        assertThat(grant.getValue().getRoleId()).isEqualTo(3);
        assertThat(grant.getValue().getScopeType()).isEqualTo("GLOBAL");
        assertThat(grant.getValue().getScopeId()).isNull();
    }

    @Test
    void shouldSkipBadgeWhenUserAlreadyHasOne() {
        // V5 迁移会给 1/2/3 号用户预埋铭牌；空库引导出来的 admin 正好是 1 号，
        // 直接插会撞 user_levels 的唯一键，把整个启动搞崩（真踩过）
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        Role superAdmin = new Role();
        superAdmin.setId(3);
        when(roleMapper.selectOne(any())).thenReturn(superAdmin);
        when(userLevelMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(0L);

        bootstrap("admin", "123456").run(null);

        verify(userMapper).insert(any(User.class));
        verify(userLevelMapper, never()).insert(any(UserLevel.class));
    }

    @Test
    void shouldSkipGrantWhenAlreadyGranted() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        Role superAdmin = new Role();
        superAdmin.setId(3);
        when(roleMapper.selectOne(any())).thenReturn(superAdmin);
        when(userLevelMapper.selectCount(any())).thenReturn(1L);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        bootstrap("admin", "123456").run(null);

        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    void shouldStillCreateUserWhenRbacRoleMissing() {
        // 迁移没跑全（roles 表空）时：宁可能用一半，也要有账号能登录进去看问题
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(roleMapper.selectOne(any())).thenReturn(null);
        when(userLevelMapper.selectCount(any())).thenReturn(1L);

        bootstrap("admin", "123456").run(null);

        verify(userMapper).insert(any(User.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    void shouldRunAfterMigrationsAndMigrationsFirst() {
        // 顺序必须写死：空库上两个 ApplicationRunner 都没标 @Order 时，
        // 第一个查表的抢在建表前面，启动直接 "Table 'mimo.users' doesn't exist"
        Order migrationOrder = MigrationRunner.class.getAnnotation(Order.class);
        Order bootstrapOrder = AdminBootstrap.class.getAnnotation(Order.class);
        assertThat(migrationOrder).as("MigrationRunner 必须显式声明顺序").isNotNull();
        assertThat(bootstrapOrder).as("AdminBootstrap 必须显式声明顺序").isNotNull();
        assertThat(migrationOrder.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(bootstrapOrder.value()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
        assertThat(migrationOrder.value()).isLessThan(bootstrapOrder.value());
    }
}
