package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.BoardDTO.*;
import com.mimo.dto.IssueDTO.MoveRequest;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Project;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.BoardService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final ProjectMapper projectMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final TeamService teamService;

    @GetMapping("/{projectId}")
    public Result<BoardVO> getBoard(@PathVariable Long projectId) {
        return Result.success(boardService.getBoard(projectId));
    }

    @PostMapping("/column")
    public Result<Long> createColumn(@Valid @RequestBody CreateColumnRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberByProject(request.getProjectId(), userId);
        return Result.success(boardService.createColumn(request).getId());
    }

    @PutMapping("/column")
    public Result<Void> updateColumn(@Valid @RequestBody UpdateColumnRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberByColumn(request.getId(), userId);
        boardService.updateColumn(request);
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/column/{id}")
    public Result<Void> deleteColumn(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertTeamMemberByColumn(id, userId);
        boardService.deleteColumn(id);
        return Result.successMessage("删除成功");
    }

    @PutMapping("/column/sort")
    public Result<Void> sortColumns(@Valid @RequestBody SortRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        // 排序列属于某个项目，取第一个列的projectId来校验
        if (request.getColumnIds() != null && !request.getColumnIds().isEmpty()) {
            assertTeamMemberByColumn(request.getColumnIds().get(0), userId);
        }
        boardService.sortColumns(request.getColumnIds());
        return Result.successMessage("排序成功");
    }

    @PutMapping("/issue/move")
    public Result<Void> moveIssue(@Valid @RequestBody MoveRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        boardService.moveIssue(request.getIssueId(), request.getTargetColumnId(), request.getSortOrder(), userId);
        return Result.successMessage("移动成功");
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    private void assertTeamMemberByProject(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        if (!teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此看板");
        }
    }

    private void assertTeamMemberByColumn(Long columnId, Long userId) {
        BoardColumn col = boardColumnMapper.selectById(columnId);
        if (col == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        assertTeamMemberByProject(col.getProjectId(), userId);
    }
}
