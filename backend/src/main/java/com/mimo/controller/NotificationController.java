package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.NotificationDTO.*;
import com.mimo.entity.Notification;
import com.mimo.mapper.NotificationMapper;
import com.mimo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @PostMapping
    public Result<Void> create(@RequestBody Notification notification, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        notification.setUserId(userId);
        if (notification.getIsRead() == null) {
            notification.setIsRead(0);
        }
        notificationService.create(notification);
        return Result.successMessage("通知已创建");
    }

    @GetMapping
    public Result<List<NotificationVO>> list(@RequestParam(defaultValue = "20") int limit, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(notificationService.listByUser(userId, limit));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此通知");
        }
        notificationService.markRead(id);
        return Result.successMessage("已标记已读");
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(Authentication auth) {
        Long userId = getLongPrincipal(auth);
        notificationService.markAllRead(userId);
        return Result.successMessage("全部已读");
    }

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
