package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.UserDTO.*;
import com.mimo.entity.User;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    public Result<ProfileVO> profile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        ProfileVO vo = new ProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        return Result.success(vo);
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (request.getUsername() != null) {
            // 检查用户名是否已被他人使用
            User existing = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, request.getUsername())
                            .ne(User::getId, userId));
            if (existing != null) throw new BusinessException(ResultCode.USERNAME_EXISTS);
            user.setUsername(request.getUsername());
        }
        if (request.getEmail() != null) {
            User existing = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, request.getEmail())
                            .ne(User::getId, userId));
            if (existing != null) throw new BusinessException(ResultCode.EMAIL_EXISTS);
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        userMapper.updateById(user);
        return Result.successMessage("更新成功");
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        return Result.successMessage("密码修改成功");
    }
}
