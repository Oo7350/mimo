package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.UserLevelVO;
import com.mimo.entity.User;
import com.mimo.entity.UserLevel;
import com.mimo.mapper.UserLevelMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.TeamMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserLevelService {

    private final UserLevelMapper userLevelMapper;
    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;

    /**
     * 获取用户等级
     */
    public UserLevelVO getByUserId(Long userId) {
        UserLevel level = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getUserId, userId));
        if (level == null) {
            // 默认返回L1
            return createDefaultVO(userId);
        }
        return toVO(level);
    }

    /**
     * 获取所有用户等级列表(仅admin可见) — 包含所有注册用户，无等级记录的默认L1
     */
    public List<UserLevelVO> listAll() {
        // 查询所有用户（未软删除）
        List<User> allUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getId));

        return allUsers.stream().map(user -> {
            UserLevel level = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                    .eq(UserLevel::getUserId, user.getId()));
            if (level != null) {
                return toVO(level);
            }
            // 无等级记录的用户返回默认L1
            UserLevelVO vo = new UserLevelVO();
            vo.setUserId(user.getId());
            vo.setLevel(1);
            vo.setLevelName("L1");
            vo.setBadgeColor("#909399");
            vo.setBadgeIcon(null);
            vo.setUsername(user.getUsername());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * Admin设置用户等级
     */
    @Transactional
    public void setLevel(Long targetUserId, Integer newLevel, Long adminId) {
        // 验证操作者是admin
        User admin = userMapper.selectById(adminId);
        if (admin == null || !"ROLE_ADMIN".equals(admin.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以设置用户等级");
        }
        // 验证目标用户存在
        User target = userMapper.selectById(targetUserId);
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        // 验证等级范围
        if (newLevel < 1 || newLevel > 4) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "等级必须在 1-4 范围内");
        }

        UserLevel level = userLevelMapper.selectOne(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getUserId, targetUserId));

        String[] levelNames = {"", "L1", "L2", "L3", "L4"};
        String[] badgeColors = {"", "#909399", "#67C23A", "#409EFF", "#E6A23C"};
        String[] badgeIcons = {"", null, "star", "medal", "crown"};

        if (level == null) {
            level = new UserLevel();
            level.setUserId(targetUserId);
            level.setLevel(newLevel);
            level.setLevelName(levelNames[newLevel]);
            level.setBadgeColor(badgeColors[newLevel]);
            level.setBadgeIcon(badgeIcons[newLevel]);
            level.setUpdatedBy(adminId);
            userLevelMapper.insert(level);
        } else {
            level.setLevel(newLevel);
            level.setLevelName(levelNames[newLevel]);
            level.setBadgeColor(badgeColors[newLevel]);
            level.setBadgeIcon(badgeIcons[newLevel]);
            level.setUpdatedBy(adminId);
            userLevelMapper.updateById(level);
        }

        // L3及以上自动升级为团队管理员
        if (newLevel >= 3) {
            syncTeamAdminRole(targetUserId);
        }
    }

    private UserLevelVO toVO(UserLevel entity) {
        UserLevelVO vo = new UserLevelVO();
        vo.setUserId(entity.getUserId());
        vo.setLevel(entity.getLevel());
        vo.setLevelName(entity.getLevelName());
        vo.setBadgeColor(entity.getBadgeColor());
        vo.setBadgeIcon(entity.getBadgeIcon());
        vo.setUpdatedAt(entity.getUpdatedAt());

        User user = userMapper.selectById(entity.getUserId());
        if (user != null) vo.setUsername(user.getUsername());
        return vo;
    }

    private UserLevelVO createDefaultVO(Long userId) {
        UserLevelVO vo = new UserLevelVO();
        vo.setUserId(userId);
        vo.setLevel(1);
        vo.setLevelName("L1");
        vo.setBadgeColor("#909399");
        vo.setBadgeIcon(null);

        User user = userMapper.selectById(userId);
        if (user != null) vo.setUsername(user.getUsername());
        return vo;
    }

    /**
     * 同步用户在所有团队中的管理员角色
     * L3及以上用户自动成为所有所在团队的管理员
     */
    private void syncTeamAdminRole(Long userId) {
        // 查找用户所在的所有团队
        List<com.mimo.entity.TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<com.mimo.entity.TeamMember>()
                        .eq(com.mimo.entity.TeamMember::getUserId, userId));
        
        // 将所有非管理员身份升级为管理员
        for (com.mimo.entity.TeamMember member : memberships) {
            if (!"ROLE_ADMIN".equals(member.getRole())) {
                member.setRole("ROLE_ADMIN");
                teamMemberMapper.updateById(member);
            }
        }
    }
}
