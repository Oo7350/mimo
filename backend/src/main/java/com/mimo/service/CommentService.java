package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.dto.CommentDTO.*;
import com.mimo.entity.Comment;
import com.mimo.entity.User;
import com.mimo.mapper.CommentMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public CommentVO create(CreateRequest request, Long userId) {
        User user = userMapper.selectById(userId);

        Comment comment = new Comment();
        comment.setIssueId(request.getIssueId());
        comment.setUserId(userId);
        comment.setUsername(user != null ? user.getUsername() : "");
        comment.setContent(request.getContent());
        commentMapper.insert(comment);

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
