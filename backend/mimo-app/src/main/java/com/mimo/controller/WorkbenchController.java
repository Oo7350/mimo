package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkbenchDTO.WorkbenchVO;
import com.mimo.service.WorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    @GetMapping("/my")
    public Result<WorkbenchVO> getMyWorkbench(Authentication auth) {
        return Result.success(workbenchService.getMyWorkbench(userId(auth)));
    }

    private Long userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
