package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.UserLevelVO;
import com.mimo.entity.User;
import com.mimo.mapper.UserMapper;
import com.mimo.service.UserLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user-levels")
@RequiredArgsConstructor
public class UserLevelController {

    private final UserLevelService userLevelService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public Result<UserLevelVO> myLevel(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(userLevelService.getByUserId(userId));
    }

    @GetMapping("/{userId}")
    public Result<UserLevelVO> getByUserId(@PathVariable Long userId) {
        return Result.success(userLevelService.getByUserId(userId));
    }

    /**
     * 获取所有用户等级列表(仅admin)
     */
    @GetMapping("/all")
    public Result<List<UserLevelVO>> listAll(Authentication auth) {
        assertAdmin(auth);
        return Result.success(userLevelService.listAll());
    }

    /**
     * Admin设置用户等级
     */
    @PutMapping("/{userId}")
    public Result<Void> setLevel(@PathVariable Long userId, @RequestBody UserLevelVO body, Authentication auth) {
        Long adminId = assertAdmin(auth);
        userLevelService.setLevel(userId, body.getLevel(), adminId);
        return Result.successMessage("等级设置成功");
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    private Long assertAdmin(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!"ROLE_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可操作");
        }
        return userId;
    }
}
