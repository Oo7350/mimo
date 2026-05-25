package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.CommentDTO.*;
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

    @PostMapping
    public Result<CommentVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(commentService.create(request, userId));
    }

    @GetMapping("/issue/{issueId}")
    public Result<List<CommentVO>> listByIssue(@PathVariable Long issueId) {
        return Result.success(commentService.listByIssue(issueId));
    }
}
