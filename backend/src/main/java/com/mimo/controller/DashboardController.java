package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.DashboardDTO.DashboardVO;
import com.mimo.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardVO> getDashboard(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(dashboardService.getDashboard(userId));
    }
}
