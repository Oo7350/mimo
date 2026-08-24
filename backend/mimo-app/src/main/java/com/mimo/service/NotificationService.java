package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    private final EmailNotificationService emailNotificationService;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Notification create(Notification notification) {
        notificationMapper.insert(notification);
        // v2.13.3：异步派发邮件通知（按用户偏好过滤，失败降级）
        try {
            String emailType = mapEmailType(notification);
            if (emailType != null) {
                emailNotificationService.dispatch(notification.getUserId(), emailType, notification);
            }
        } catch (Throwable t) {
            // 邮件派发异常绝对不能影响站内通知
        }
        return notification;
    }

    /**
     * 把站内通知 type/relatedType 映射到邮件通知偏好类型（与 user_email_accounts.notify_types 取值一致）：
     * - type=ASSIGNED → assignment
     * - relatedType=APPROVAL_REQUEST → approval
     * - type=STATUS_CHANGED + content 含"评论" → comment
     * - type=STATUS_CHANGED + 其他 → issue_status
     * 其余返回 null（不触发邮件）
     */
    private String mapEmailType(Notification n) {
        if (n == null || n.getUserId() == null) return null;
        String type = n.getType();
        String relatedType = n.getRelatedType();
        String content = n.getContent() == null ? "" : n.getContent();

        if ("APPROVAL_REQUEST".equalsIgnoreCase(relatedType)) return "approval";
        if ("ASSIGNED".equalsIgnoreCase(type)) return "assignment";
        if ("STATUS_CHANGED".equalsIgnoreCase(type)) {
            return content.contains("评论") ? "comment" : "issue_status";
        }
        return null;
    }

    public List<NotificationVO> listByUser(Long userId, int limit) {
        IPage<Notification> page = notificationMapper.selectPage(
            new Page<>(1, Math.max(limit, 1)),
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
        );
        return page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
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
        // 批量更新，避免逐条循环
        Notification batchUpdate = new Notification();
        batchUpdate.setIsRead(1);
        notificationMapper.update(batchUpdate,
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
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
