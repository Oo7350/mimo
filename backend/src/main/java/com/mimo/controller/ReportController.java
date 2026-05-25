package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.ReportDTO.*;
import com.mimo.dto.StatsDTO.*;
import com.mimo.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public Result<ReportVO> generateDraft(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(reportService.generateDraft(request, userId));
    }

    @PutMapping
    public Result<ReportVO> updateContent(@Valid @RequestBody UpdateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(reportService.updateContent(request, userId));
    }

    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        reportService.submit(id);
        return Result.successMessage("提交成功");
    }

    @GetMapping
    public Result<List<ReportVO>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String type,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(reportService.listByUser(userId, projectId, type));
    }

    @GetMapping("/{id}")
    public Result<ReportVO> getById(@PathVariable Long id) {
        return Result.success(reportService.getById(id));
    }

    @GetMapping("/stats/{projectId}")
    public Result<StatsVO> getStats(@PathVariable Long projectId) {
        return Result.success(reportService.getProjectStats(projectId));
    }
}
