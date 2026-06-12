package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.entity.UserLevel;
import com.mimo.mapper.UserLevelMapper;
import com.mimo.dto.LoginRequest;
import com.mimo.dto.LoginResponse;
import com.mimo.dto.RegisterRequest;
import com.mimo.entity.User;
import com.mimo.mapper.UserMapper;
import com.mimo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final UserLevelMapper userLevelMapper;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       UserLevelMapper userLevelMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userLevelMapper = userLevelMapper;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 单设备登录：Redis 可用时将新 Token 存入 Redis
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(
                    "mimo:token:" + user.getId(),
                    token,
                    24, TimeUnit.HOURS);
        }

        LoginResponse.UserInfo info = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();

        return LoginResponse.builder()
                .token(token)
                .userInfo(info)
                .build();
    }

    public void register(RegisterRequest request) {
        // 检查用户名是否存在
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()))) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        // 检查邮箱是否存在
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()))) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("ROLE_MEMBER");
        userMapper.insert(user);

        // 自动创建默认 L1 等级
        UserLevel level = new UserLevel();
        level.setUserId(user.getId());
        level.setLevel(1);
        level.setLevelName("L1");
        level.setBadgeColor("#909399");
        level.setBadgeIcon(null);
        userLevelMapper.insert(level);
    }
}
