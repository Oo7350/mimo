package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.UserLevelVO;
import com.mimo.entity.User;
import com.mimo.entity.UserLevel;
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
     * 获取所有用户等级列表(仅admin可见)
     */
    public List<UserLevelVO> listAll() {
        List<UserLevel> levels = userLevelMapper.selectList(
                new LambdaQueryWrapper<UserLevel>().orderByAsc(UserLevel::getLevel));
        return levels.stream().map(this::toVO).collect(Collectors.toList());
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
}
