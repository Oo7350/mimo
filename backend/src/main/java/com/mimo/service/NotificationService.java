package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.dto.NotificationDTO.*;
import com.mimo.entity.Notification;
import com.mimo.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void create(Notification notification) {
        notificationMapper.insert(notification);
    }

    public List<NotificationVO> listByUser(Long userId, int limit) {
        List<Notification> list = notificationMapper.selectList(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT " + limit)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    public UnreadCountVO getUnreadCount(Long userId) {
        Long count = notificationMapper.selectCount(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
        );
        UnreadCountVO vo = new UnreadCountVO();
        vo.setCount(count.intValue());
        return vo;
    }

    public void markRead(Long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    public void markAllRead(Long userId) {
        List<Notification> unread = notificationMapper.selectList(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
        );
        for (Notification n : unread) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setRelatedId(n.getRelatedId());
        vo.setRelatedType(n.getRelatedType());
        vo.setIsRead(n.getIsRead());
        vo.setCreatedAt(n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : null);
        return vo;
    }
}
