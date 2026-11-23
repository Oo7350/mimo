package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 管理员判定的**唯一入口**。
 *
 * <p>背景（计划书 §七 技术债「新旧权限判断并存」）：项目里同时存在两套权限模型 ——
 * v2.7 之前的 `users.role = ROLE_ADMIN` / `team_members.role = ROLE_ADMIN` 旧字段，
 * 和 v2.11 引入的多作用域 RBAC（45 个权限码 + User→Role→Permission）。
 * 过去十几个 Service/Controller 各自手写 {@code "ROLE_ADMIN".equals(user.getRole())}，
 * 结果是**同一个"是不是管理员"在不同接口上有不同口径**，个别接口因此绕过了 RBAC。
 *
 * <p>本类把口径统一成两个问题、两个答案：
 * <ul>
 *   <li>{@link #isSystemAdmin(Long)} —— 系统管理员：RBAC 权限码 {@value #PERM_SYSTEM_ADMIN}
 *       （V11 里只有 SUPER_ADMIN 拿到它），旧字段兜底。</li>
 *   <li>{@link #isTeamAdmin(Long, Long)} —— 团队管理员：系统管理员，或 RBAC 在 TEAM 作用域下的
 *       {@value #PERM_TEAM_ADMIN}（PROJECT_MANAGER 及以上），旧字段兜底。</li>
 * </ul>
 *
 * <p>迁移策略是**求并集**：旧字段成立或 RBAC 成立都算通过。这样不会因为本次改造把现有管理员
 * 挡在门外（回归零风险），同时让"只在 RBAC 里有角色、旧字段没同步"的账号也能生效 ——
 * 正是原记录里"个别接口可能绕过 RBAC"要解决的问题。
 * 旧字段这条路可以用 {@code mimo.access.legacy-role-fallback=false} 关掉，
 * 关掉后即为纯 RBAC 判定（等 V11 的角色分配补齐后即可切换）。
 *
 * <p>判定顺序是"旧字段优先短路"，不是随手写的：旧路径各一次查询，RBAC 路径要查角色 + 权限两张表，
 * 而 {@code ProjectAccessService#requireProject} 这类入口在每个请求上都会调用，
 * 让管理员/成员在第一次查询就命中，避免给所有读接口多加两次 SQL。
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    /** 旧字段里的管理员标识（带 Spring Security 前缀） */
    public static final String LEGACY_ROLE_ADMIN = "ROLE_ADMIN";

    /** 系统管理员口径的权限编码：V11 只授予 SUPER_ADMIN */
    public static final String PERM_SYSTEM_ADMIN = "role.manage";

    /** 团队管理员口径的权限编码：PROJECT_MANAGER 及以上 */
    public static final String PERM_TEAM_ADMIN = "team.manage";

    private static final String SCOPE_TEAM = "TEAM";

    private final RoleService roleService;
    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;

    /** 是否仍然承认旧字段（迁移开关，默认开） */
    @Value("${mimo.access.legacy-role-fallback:true}")
    private boolean legacyRoleFallback = true;

    /** 是否为系统管理员（全局） */
    public boolean isSystemAdmin(Long userId) {
        if (userId == null) return false;
        if (legacyRoleFallback && hasLegacySystemRole(userId)) return true;
        return roleService.hasPermission(userId, PERM_SYSTEM_ADMIN);
    }

    /** 是否为团队管理员（含系统管理员） */
    public boolean isTeamAdmin(Long userId, Long teamId) {
        if (userId == null) return false;
        if (isSystemAdmin(userId)) return true;
        if (teamId == null) return false;
        if (legacyRoleFallback && hasLegacyTeamAdminRole(userId, teamId)) return true;
        return roleService.hasPermission(userId, PERM_TEAM_ADMIN, SCOPE_TEAM, teamId);
    }

    /** 是否承认旧字段（供诊断/测试观察当前口径） */
    public boolean isLegacyRoleFallbackEnabled() {
        return legacyRoleFallback;
    }

    // ==================== 旧字段路径 ====================

    private boolean hasLegacySystemRole(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && LEGACY_ROLE_ADMIN.equals(user.getRole());
    }

    private boolean hasLegacyTeamAdminRole(Long userId, Long teamId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getRole, LEGACY_ROLE_ADMIN)) != null;
    }
}
