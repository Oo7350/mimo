package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkbenchDTO.TeamWorkbenchVO;
import com.mimo.dto.WorkbenchDTO.WorkbenchVO;
import com.mimo.service.WorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 管理员视角的全员任务流（v2.19.8；v2.20.1 起支持分页与趋势）。
     *
     * <p>权限判断放在 Service 里，不放这里：判断依据是"是不是某个团队的管理员"，
     * 需要先查团队再逐个判权，注解表达式写不下这个逻辑（写下了也会多查一次库）。
     *
     * <p>非管理员拿到的是 **响应体 `code=403` + 一句说明**（本项目统一用响应体 code 表达业务错误，
     * HTTP 状态仍是 200，见 `GlobalExceptionHandler#handleBusinessException`）。
     * 这里刻意不返回空列表——空列表会被读成"大家都没任务"，而不是"你没权限看"。
     *
     * <p>分页与筛选参数：`memberPage`（从 1 开始）、`pageSize`（默认 8，上限 30）、
     * `taskLimit`（每个成员最多带几条任务，默认 6，上限 20）、
     * `keyword`（成员名或任务标题）、`only`（all / overdue / doing / idle）、
     * `sort`（load / name / overdue）。数字越界不报错而是夹住上下限，字符串不认识就按默认处理
     * —— 这些参数来自界面控件，不该因为一次拼错就 400 给用户看。
     */
    @GetMapping("/team")
    public Result<TeamWorkbenchVO> getTeamWorkbench(
            Authentication auth,
            @RequestParam(defaultValue = "1") int memberPage,
            @RequestParam(defaultValue = "8") int pageSize,
            @RequestParam(defaultValue = "6") int taskLimit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String only,
            @RequestParam(required = false) String sort) {
        return Result.success(workbenchService.getTeamWorkbench(
                userId(auth), memberPage, pageSize, taskLimit, keyword, only, sort));
    }

    private Long userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
