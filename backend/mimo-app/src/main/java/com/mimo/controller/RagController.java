package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.RagDTO.*;
import com.mimo.entity.Project;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.RagService;
import com.mimo.service.ProjectAccessService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;
    private final ProjectAccessService projectAccessService;

    @PostMapping("/index/{projectId}")
    public Result<IndexResultVO> rebuildIndex(@PathVariable Long projectId,
                                              Authentication auth) {
        assertMember(projectId, userId(auth));
        return Result.success(ragService.rebuildIndex(projectId));
    }

    @PostMapping("/ask")
    public Result<AskVO> ask(@Valid @RequestBody AskRequest request,
                             Authentication auth) {
        Long userId = userId(auth);
        assertMember(request.getProjectId(), userId);
        return Result.success(ragService.ask(request.getProjectId(), request.getQuestion(),
                request.getTopK(), userId));
    }

    @PostMapping("/evaluate")
    public Result<EvaluationResultVO> evaluate(@Valid @RequestBody EvaluationRequest request,
                                               Authentication auth) {
        assertMember(request.getProjectId(), userId(auth));
        return Result.success(ragService.evaluate(request));
    }

    private void assertMember(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        // 口径与"看"一致：系统管理员 / 团队成员 / 团队管理员都能访问（BUG-117）
        if (!projectAccessService.canAccessProject(project.getId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该项目知识库");
        }
    }

    private Long userId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
