package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.BoardDTO.*;
import com.mimo.dto.IssueDTO.MoveRequest;
import com.mimo.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/{projectId}")
    public Result<BoardVO> getBoard(@PathVariable Long projectId) {
        return Result.success(boardService.getBoard(projectId));
    }

    @PostMapping("/column")
    public Result<Long> createColumn(@Valid @RequestBody CreateColumnRequest request) {
        return Result.success(boardService.createColumn(request).getId());
    }

    @PutMapping("/column")
    public Result<Void> updateColumn(@Valid @RequestBody UpdateColumnRequest request) {
        boardService.updateColumn(request);
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/column/{id}")
    public Result<Void> deleteColumn(@PathVariable Long id) {
        boardService.deleteColumn(id);
        return Result.successMessage("删除成功");
    }

    @PutMapping("/column/sort")
    public Result<Void> sortColumns(@Valid @RequestBody SortRequest request) {
        boardService.sortColumns(request.getColumnIds());
        return Result.successMessage("排序成功");
    }

    @PutMapping("/issue/move")
    public Result<Void> moveIssue(@Valid @RequestBody MoveRequest request) {
        boardService.moveIssue(request.getIssueId(), request.getTargetColumnId(), request.getSortOrder());
        return Result.successMessage("移动成功");
    }
}
