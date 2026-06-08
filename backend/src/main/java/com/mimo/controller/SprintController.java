package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.SprintDTO.*;
import com.mimo.service.SprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @PostMapping
    public Result<SprintVO> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(sprintService.create(request));
    }

    @GetMapping("/{id}")
    public Result<SprintVO> getById(@PathVariable Long id) {
        return Result.success(sprintService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public Result<List<SprintVO>> listByProject(@PathVariable Long projectId) {
        return Result.success(sprintService.listByProject(projectId));
    }

    @PutMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        sprintService.startSprint(id);
        return Result.successMessage("Sprint 已启动");
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        sprintService.completeSprint(id);
        return Result.successMessage("Sprint 已完成");
    }

    @GetMapping("/{id}/burndown")
    public Result<BurndownVO> burndown(@PathVariable Long id) {
        return Result.success(sprintService.getBurndown(id));
    }

    @PostMapping("/{id}/snapshot")
    public Result<Void> takeSnapshot(@PathVariable Long id) {
        sprintService.generateSnapshot(id);
        return Result.successMessage("快照已生成");
    }

    @PostMapping("/quick/{projectId}")
    public Result<SprintVO> quickStart(@PathVariable Long projectId) {
        return Result.success(sprintService.createQuickSprint(projectId));
    }

    @PutMapping("/{id}/complete-migration")
    public Result<Void> completeWithMigration(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetSprintId) {
        sprintService.completeSprintWithMigration(id, targetSprintId);
        return Result.successMessage("Sprint 已完成");
    }

    @PutMapping("/{issueId}/add-to-sprint/{sprintId}")
    public Result<Void> addIssueToSprint(
            @PathVariable Long issueId,
            @PathVariable Long sprintId) {
        sprintService.addIssueToSprint(issueId, sprintId);
        return Result.successMessage("任务已加入 Sprint");
    }

    @GetMapping("/{id}/stats")
    public Result<SprintStatsVO> getStats(@PathVariable Long id) {
        return Result.success(sprintService.getSprintStats(id));
    }
}
