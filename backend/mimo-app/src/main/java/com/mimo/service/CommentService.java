package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
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
        Long notifiedAssignee = null;
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
            notifiedAssignee = issue.getAssigneeId();

            try {
                webSocketService.sendNotification(issue.getAssigneeId(), Map.of(
                    "type", "STATUS_CHANGED",
                    "title", n.getTitle(),
                    "content", n.getContent(),
                    "relatedId", issue.getId(),
                    "relatedType", "ISSUE"
                ));
            } catch (Exception e) { log.error("WebSocket发送评论通知失败, userId={}", issue.getAssigneeId(), e); }
        }

        // 通知评论里被 @ 到的人（v2.18.2：以前只通知指派人，@谁都不会有反应）
        notifyMentions(issue, request.getContent(), userId, user, notifiedAssignee);

        return toVO(comment);
    }

    /**
     * 从评论正文里解析出被 @ 的用户名。
     *
     * <p>和前端 {@code TeamDetail.vue#renderMention} 的高亮同一个约定（{@code @xxx}），
     * 但这里收窄了字符集：前端用 {@code \S+} 会把后面的中文标点一起吃掉，
     * 后端按"用户名可能出现的字符"匹配，{@code @张三，谢谢} 才能解析出「张三」而不是「张三，谢谢」。
     * 包可见是为了让单测能直接盯住这条解析规则，不用绕一整条评论链路。
     */
    static java.util.Set<String> parseMentions(String content) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (content == null || content.isEmpty()) return names;
        java.util.regex.Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    private static final java.util.regex.Pattern MENTION_PATTERN =
            java.util.regex.Pattern.compile("(?:^|[^\\p{IsHan}A-Za-z0-9_.-])@([\\p{IsHan}A-Za-z0-9_.-]+)");

    /**
     * 给评论里 @ 到的人发站内通知 + WebSocket 实时推送。
     *
     * @param issue            被评论的任务（为空说明数据异常，直接不发）
     * @param content          评论原文
     * @param actorId          评论作者，不给自己发
     * @param actor            评论作者，用于文案
     * @param notifiedAssignee 刚刚已经收到「新评论」的指派人，避免同一件事打扰两次
     */
    private void notifyMentions(Issue issue, String content, Long actorId, User actor, Long notifiedAssignee) {
        if (issue == null || content == null || content.isEmpty()) return;

        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) names.add(matcher.group(1));

        for (String name : names) {
            User target = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, name));
            if (target == null || target.getId() == null) continue;
            if (target.getId().equals(actorId)) continue;
            if (target.getId().equals(notifiedAssignee)) continue;

            Notification n = new Notification();
            n.setUserId(target.getId());
            n.setType("MENTION");
            n.setTitle("有人在评论里提到了你");
            n.setContent((actor != null ? actor.getUsername() : "有人")
                    + " 在任务 " + issue.getIssueKey() + " 的评论里 @了你");
            n.setRelatedId(issue.getId());
            n.setRelatedType("ISSUE");
            n.setIsRead(0);
            notificationService.create(n);

            try {
                webSocketService.sendNotification(target.getId(), Map.of(
                    "type", "MENTION",
                    "title", n.getTitle(),
                    "content", n.getContent(),
                    "relatedId", issue.getId(),
                    "relatedType", "ISSUE"
                ));
            } catch (Exception e) {
                log.error("WebSocket发送@提及通知失败, userId={}", target.getId(), e);
            }
        }
    }

    /**
     * 修改评论内容（KNOWN-009：以前评论发出去只能删，不能改）
     *
     * <p>只有作者本人可以改，管理员也不例外——评论是审计痕迹，不做「管理员代改」。
     */
    public CommentVO update(Long commentId, UpdateRequest request, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己的评论");
        }
        comment.setContent(request.getContent());
        commentMapper.updateById(comment);
        return toVO(comment);
    }

    public List<CommentVO> listByIssue(Long issueId) {        List<Comment> comments = commentMapper.selectList(
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
