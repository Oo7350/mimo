package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.NotificationDTO.*;
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

    @GetMapping
    public Result<List<NotificationVO>> list(@RequestParam(defaultValue = "20") int limit, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(notificationService.listByUser(userId, limit));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.successMessage("已标记已读");
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAllRead(userId);
        return Result.successMessage("全部已读");
    }
}
