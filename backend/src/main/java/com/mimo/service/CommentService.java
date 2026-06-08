package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.dto.CommentDTO.*;
import com.mimo.entity.Comment;
import com.mimo.entity.Issue;
import com.mimo.entity.Notification;
import com.mimo.entity.User;
import com.mimo.mapper.CommentMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.service.NotificationService;
import com.mimo.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final IssueMapper issueMapper;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public CommentVO create(CreateRequest request, Long userId) {
        User user = userMapper.selectById(userId);

        Comment comment = new Comment();
        comment.setIssueId(request.getIssueId());
        comment.setUserId(userId);
        comment.setUsername(user != null ? user.getUsername() : "");
        comment.setContent(request.getContent());
        commentMapper.insert(comment);

        // 通知 issue 指派人
        Issue issue = issueMapper.selectById(request.getIssueId());
        if (issue != null && issue.getAssigneeId() != null && !issue.getAssigneeId().equals(userId)) {
            Notification n = new Notification();
            n.setUserId(issue.getAssigneeId());
            n.setType("STATUS_CHANGED");
            n.setTitle("新评论");
            n.setContent((user != null ? user.getUsername() : "有人") + " 评论了任务 " + issue.getIssueKey());
            n.setRelatedId(issue.getId());
            n.setRelatedType("ISSUE");
            n.setIsRead(0);
            notificationService.create(n);

            try {
                webSocketService.sendNotification(issue.getAssigneeId(), Map.of(
                    "type", "STATUS_CHANGED",
                    "title", n.getTitle(),
                    "content", n.getContent(),
                    "relatedId", issue.getId(),
                    "relatedType", "ISSUE"
                ));
            } catch (Exception ignored) {}
        }

        return toVO(comment);
    }

    public List<CommentVO> listByIssue(Long issueId) {
        List<Comment> comments = commentMapper.selectList(
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getIssueId, issueId)
                .orderByDesc(Comment::getCreatedAt)
        );
        return comments.stream().map(this::toVO).collect(Collectors.toList());
    }

    private CommentVO toVO(Comment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setIssueId(comment.getIssueId());
        vo.setUserId(comment.getUserId());
        vo.setUsername(comment.getUsername());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt() != null
            ? comment.getCreatedAt().format(FMT)
            : null);
        return vo;
    }
}
