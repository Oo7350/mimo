package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.ProjectDTO.*;
import com.mimo.service.ProjectService;
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

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
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
        projectService.deleteProject(projectId, userId);
        return Result.successMessage("项目已删除");
    }
}
