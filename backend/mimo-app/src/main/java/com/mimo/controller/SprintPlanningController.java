package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.SprintPlanningDTO.ApplyPlanRequest;
import com.mimo.dto.SprintPlanningDTO.ApplyPlanVO;
import com.mimo.dto.SprintPlanningDTO.SuggestPlanRequest;
import com.mimo.dto.SprintPlanningDTO.SprintPlanVO;
import com.mimo.service.SprintPlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SprintPlanningController {

    private final SprintPlanningService sprintPlanningService;

    @PostMapping("/ai/suggest-sprint-plan")
    public Result<SprintPlanVO> suggestPlan(@Valid @RequestBody SuggestPlanRequest request,
                                            Authentication auth) {
        return Result.success(sprintPlanningService.suggestPlan(
                request.getProjectId(), request.getSprintId(),
                request.getCapacityPoints(), userId(auth)));
    }

    @PostMapping("/sprints/{sprintId}/apply-plan")
    public Result<ApplyPlanVO> applyPlan(@PathVariable Long sprintId,
                                         @Valid @RequestBody ApplyPlanRequest request,
                                         Authentication auth) {
        return Result.success(sprintPlanningService.applyPlan(
                sprintId, request.getIssueIds(), userId(auth)));
    }

    private Long userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
