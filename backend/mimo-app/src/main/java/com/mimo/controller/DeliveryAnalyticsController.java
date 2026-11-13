package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.DeliveryAnalyticsDTO.CfdVO;
import com.mimo.dto.DeliveryAnalyticsDTO.VelocityVO;
import com.mimo.entity.Project;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.DeliveryAnalyticsService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class DeliveryAnalyticsController {

    private final DeliveryAnalyticsService deliveryAnalyticsService;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;

    @GetMapping("/project/{projectId}/velocity")
    public Result<VelocityVO> velocity(@PathVariable Long projectId,
                                       @RequestParam(defaultValue = "6") Integer limit,
                                       Authentication auth) {
        assertMember(projectId, userId(auth));
        return Result.success(deliveryAnalyticsService.getVelocity(projectId, limit));
    }

    @GetMapping("/project/{projectId}/cfd")
    public Result<CfdVO> cfd(@PathVariable Long projectId,
                             @RequestParam(required = false) Long sprintId,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Authentication auth) {
        assertMember(projectId, userId(auth));
        return Result.success(deliveryAnalyticsService.getCfd(projectId, sprintId, from, to));
    }

    private void assertMember(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        if (!teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该项目的交付分析");
        }
    }

    private Long userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
