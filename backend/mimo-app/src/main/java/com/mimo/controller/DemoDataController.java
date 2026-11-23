package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示数据（KNOWN-020）。
 *
 * <p>空库只有 3 个用户 1 个团队，工作台 / 迭代 / 报表全是 0 —— 演示和试用都很难受。
 * 这里给管理员一个入口一键铺一套说得过去的数据。权限判断在 Service 里做
 * （要复用 {@code AccessControlService.isSystemAdmin}，包括旧字段兜底）。
 */
@RestController
@RequestMapping("/api/admin/demo-data")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoDataService demoDataService;

    /** 现在有没有演示数据 */
    @GetMapping
    public Result<Map<String, Object>> status() {
        return Result.success(demoDataService.status());
    }

    /** 生成演示数据（幂等：已经有就返回现状，不重复堆） */
    @PostMapping
    public Result<Map<String, Object>> generate(Authentication auth) {
        Object principal = auth.getPrincipal();
        Long userId = principal instanceof Number ? ((Number) principal).longValue() : null;
        return Result.success(demoDataService.generate(userId));
    }
}
