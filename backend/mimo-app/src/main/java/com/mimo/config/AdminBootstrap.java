package com.mimo.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.entity.Role;
import com.mimo.entity.User;
import com.mimo.entity.UserLevel;
import com.mimo.entity.UserRole;
import com.mimo.mapper.RoleMapper;
import com.mimo.mapper.UserLevelMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 空库引导：给"刚建好、一个人都没有"的库补一个管理员。
 *
 * <p>为什么需要它：迁移脚本只建表、只插权限与角色，**不插任何用户**（这是对的——
 * 生产环境不该埋默认口令）。但 CI 与任何新环境第一件事就是拿 {@code admin/123456} 登录跑 e2e，
 * 于是"照着文档跑一遍"在空库上必然红。KNOWN-024（CI 没在真实 runner 上跑过）
 * 底下压着的就是这个坑：配置写得再全，第一次跑也会倒在第一步。</p>
 *
 * <p>因此这里提供一个**显式开启**的引导：只有配了 {@code mimo.bootstrap.admin-username}
 * 才动作，且只在"该账号不存在"时创建（幂等，重启不会重置密码）。默认留空 = 不引导。</p>
 *
 * <p>权限怎么给：既写旧字段 {@code users.role = ROLE_ADMIN}（十几个地方还在用它兜底），
 * 也补一行 RBAC 的 {@code user_roles(SUPER_ADMIN, GLOBAL)}。只写一半的后果是
 * "能进后台、但某些按权限码判定的按钮点了 403"——这种半截管理员最难查。</p>
 *
 * @see MigrationRunner 建表与 RBAC 基础数据（每次启动重跑，幂等）
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /** 与迁移脚本、AccessControlService 保持同一口径的旧角色字段值 */
    private static final String LEGACY_ROLE_ADMIN = "ROLE_ADMIN";
    private static final String RBAC_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String SCOPE_GLOBAL = "GLOBAL";

    private final UserMapper userMapper;
    private final UserLevelMapper userLevelMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;

    public AdminBootstrap(UserMapper userMapper,
                          UserLevelMapper userLevelMapper,
                          RoleMapper roleMapper,
                          UserRoleMapper userRoleMapper,
                          PasswordEncoder passwordEncoder,
                          @Value("${mimo.bootstrap.admin-username:}") String adminUsername,
                          @Value("${mimo.bootstrap.admin-password:}") String adminPassword,
                          @Value("${mimo.bootstrap.admin-email:admin@mimo.local}") String adminEmail) {
        this.userMapper = userMapper;
        this.userLevelMapper = userLevelMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = trimToNull(adminUsername);
        if (username == null) {
            // 默认路径：什么都不做。生产环境不该有条默认口令的后门。
            log.info("[AdminBootstrap] 未配置 mimo.bootstrap.admin-username，跳过空库引导");
            return;
        }
        if (trimToNull(adminPassword) == null) {
            log.warn("[AdminBootstrap] 配了 admin-username 却没有 admin-password，跳过（不猜默认口令）");
            return;
        }
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("[AdminBootstrap] 账号「{}」已存在（id={}），不覆盖密码", username, existing.getId());
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(adminPassword));
        user.setEmail(trimToNull(adminEmail) == null ? username + "@mimo.local" : adminEmail);
        user.setRole(LEGACY_ROLE_ADMIN);
        userMapper.insert(user);

        // 铭牌：注册路径会给每个新用户发 L1，引导出来的管理员也补上，否则个人页会缺一块。
        // 注意先查后插：V5 迁移会给 1/2/3 号用户预埋铭牌（写死的 id），
        // 空库上引导出来的 admin 正好是 1 号，直接插会撞 user_levels 的唯一键
        // —— 空库第一次启动就这么崩过一次（v2.22.2）。
        if (!hasUserLevel(user.getId())) {
            UserLevel level = new UserLevel();
            level.setUserId(user.getId());
            level.setLevel(1);
            level.setLevelName("L1");
            level.setBadgeColor("#909399");
            userLevelMapper.insert(level);
        }

        grantSuperAdmin(user.getId());

        log.info("[AdminBootstrap] 已创建管理员「{}」（id={}），请在生产环境立即修改密码", username, user.getId());
    }

    /** 补 RBAC 的 SUPER_ADMIN 全局授权；角色不存在（迁移没跑到）时只留旧字段并警告 */
    private void grantSuperAdmin(Long userId) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, RBAC_SUPER_ADMIN)
                .last("LIMIT 1"));
        if (role == null) {
            log.warn("[AdminBootstrap] 找不到 {} 角色，仅写旧字段 users.role={}；"
                    + "按权限码判定的功能可能仍会被拒", RBAC_SUPER_ADMIN, LEGACY_ROLE_ADMIN);
            return;
        }
        // 同样先查后插：V11 迁移每次启动都会按 role 字段补授权，
        // 第二次启动时这里再插一条就撞 uk_user_role_scope（BUG-098 的老坑）
        boolean granted = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId())
                .eq(UserRole::getScopeType, SCOPE_GLOBAL)
                .isNull(UserRole::getScopeId)) > 0;
        if (granted) {
            return;
        }
        UserRole grant = new UserRole();
        grant.setUserId(userId);
        grant.setRoleId(role.getId());
        grant.setScopeType(SCOPE_GLOBAL);
        grant.setScopeId(null);
        userRoleMapper.insert(grant);
    }

    private boolean hasUserLevel(Long userId) {
        return userLevelMapper.selectCount(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getUserId, userId)) > 0;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
