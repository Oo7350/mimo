package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ReportDTO.*;
import com.mimo.dto.StatsDTO.*;
import com.mimo.entity.Report;
import com.mimo.mapper.ReportMapper;
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
    private final ReportMapper reportMapper;

    @PostMapping("/generate")
    public Result<ReportVO> generateDraft(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(reportService.generateDraft(request, userId));
    }

    @PutMapping
    public Result<ReportVO> updateContent(@Valid @RequestBody UpdateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(reportService.updateContent(request, userId));
    }

    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertReportOwner(id, userId);
        reportService.submit(id);
        return Result.successMessage("提交成功");
    }

    @GetMapping
    public Result<List<ReportVO>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String type,
            Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(reportService.listByUser(userId, projectId, type));
    }

    @GetMapping("/{id}")
    public Result<ReportVO> getById(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        assertReportOwner(id, userId);
        return Result.success(reportService.getById(id));
    }

    @GetMapping("/stats/{projectId}")
    public Result<StatsVO> getStats(@PathVariable Long projectId) {
        return Result.success(reportService.getProjectStats(projectId));
    }

    // ---- helpers ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }

    private void assertReportOwner(Long reportId, Long userId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "报告不存在");
        }
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此报告");
        }
    }
}
