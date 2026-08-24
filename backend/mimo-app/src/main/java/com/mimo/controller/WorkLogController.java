package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkLogDTO.*;
import com.mimo.service.WorkLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/worklogs")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogService workLogService;

    @PostMapping
    public Result<WorkLogVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(workLogService.create(request, userId));
    }

    @GetMapping("/issue/{issueId}")
    public Result<List<WorkLogVO>> listByIssue(@PathVariable Long issueId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(workLogService.listByIssue(issueId, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        workLogService.delete(id, userId);
        return Result.successMessage("工时记录已删除");
    }

    @GetMapping("/issue/{issueId}/sum")
    public Result<java.math.BigDecimal> sumByIssue(@PathVariable Long issueId) {
        return Result.success(workLogService.sumByIssue(issueId));
    }

    @GetMapping("/sprint/{sprintId}/summary")
    public Result<SprintWorkloadVO> sprintSummary(@PathVariable Long sprintId, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(workLogService.getSprintWorkload(sprintId, userId));
    }

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
