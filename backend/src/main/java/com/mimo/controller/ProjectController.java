package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ProjectDTO.*;
import com.mimo.entity.Project;
import com.mimo.mapper.ProjectMapper;
import com.mimo.service.ProjectService;
import com.mimo.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TeamService teamService;
    private final ProjectMapper projectMapper;

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 非管理员需要走审批流程，后端直接拒绝
        if (!teamService.isTeamAdmin(request.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以创建项目，请通过审批申请");
        }
        return Result.success(projectService.create(request, userId));
    }

    @GetMapping("/{id}")
    public Result<ProjectVO> getById(@PathVariable Long id) {
        return Result.success(projectService.getById(id));
    }

    @GetMapping("/team/{teamId}")
    public Result<List<ProjectVO>> listByTeam(@PathVariable Long teamId) {
        return Result.success(projectService.listByTeam(teamId));
    }

    @GetMapping("/my")
    public Result<List<ProjectVO>> myProjects(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(projectService.listByUser(userId));
    }

    @PostMapping("/{projectId}/members/{userId}")
    public Result<Void> addMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.addMember(projectId, userId);
        return Result.successMessage("添加成功");
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> deleteProject(@PathVariable Long projectId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 非管理员需要走审批流程，后端直接拒绝
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
        if (!teamService.isTeamAdmin(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以删除项目，请通过审批申请");
        }
        projectService.deleteProject(projectId, userId);
        return Result.successMessage("项目已删除");
    }
}
