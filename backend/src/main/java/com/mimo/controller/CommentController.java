package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.CommentDTO.*;
import com.mimo.entity.Comment;
import com.mimo.mapper.CommentMapper;
import com.mimo.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentMapper commentMapper;

    @PostMapping
    public Result<CommentVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(commentService.create(request, userId));
    }

    @GetMapping("/issue/{issueId}")
    public Result<List<CommentVO>> listByIssue(@PathVariable Long issueId) {
        return Result.success(commentService.listByIssue(issueId));
    }

    /**
     * 删除评论（仅评论作者可删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己的评论");
        }
        commentMapper.deleteById(id);
        return Result.successMessage("删除成功");
    }

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
