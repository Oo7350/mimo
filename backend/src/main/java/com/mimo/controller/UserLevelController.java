package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.UserLevelVO;
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

    /**
     * 获取当前用户等级
     */
    @GetMapping("/me")
    public Result<UserLevelVO> myLevel(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(userLevelService.getByUserId(userId));
    }

    /**
     * 获取指定用户等级(公开信息)
     */
    @GetMapping("/{userId}")
    public Result<UserLevelVO> getByUserId(@PathVariable Long userId) {
        return Result.success(userLevelService.getByUserId(userId));
    }

    /**
     * 获取所有用户等级列表(仅admin)
     */
    @GetMapping("/all")
    public Result<List<UserLevelVO>> listAll(Authentication auth) {
        return Result.success(userLevelService.listAll());
    }

    /**
     * Admin设置用户等级
     */
    @PutMapping("/{userId}")
    public Result<Void> setLevel(@PathVariable Long userId, @RequestBody UserLevelVO body, Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        userLevelService.setLevel(userId, body.getLevel(), adminId);
        return Result.successMessage("等级设置成功");
    }
}
