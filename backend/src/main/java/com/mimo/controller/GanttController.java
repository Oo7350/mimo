package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.IssueDTO;
import com.mimo.entity.Issue;
import com.mimo.mapper.IssueMapper;
import com.mimo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 甘特图端点（v2.9.3 新增）
 */
@RestController
@RequestMapping("/api/gantt")
@RequiredArgsConstructor
public class GanttController {

    private final IssueMapper issueMapper;
    private final ProjectService projectService;

    @GetMapping("/project/{projectId}")
    public Result<List<IssueDTO.IssueVO>> listByProject(@PathVariable Long projectId) {
        projectService.getById(projectId);
        List<Issue> issues = issueMapper.selectGanttByProject(projectId);
        // 字段映射由前端根据需要自行处理；返回轻量 VO 仅含甘特图必要字段
        List<IssueDTO.IssueVO> vos = new java.util.ArrayList<>(issues.size());
        for (Issue i : issues) {
            IssueDTO.IssueVO v = new IssueDTO.IssueVO();
            v.setId(i.getId());
            v.setTitle(i.getTitle());
            v.setIssueKey(null);
            v.setType(i.getType());
            v.setPriority(i.getPriority());
            v.setStatus(i.getStatus());
            v.setAssigneeId(i.getAssigneeId());
            v.setPlanStartDate(i.getPlanStartDate());
            v.setPlanEndDate(i.getPlanEndDate());
            v.setDependencies(i.getDependencies());
            v.setStoryPoints(i.getStoryPoints());
            vos.add(v);
        }
        return Result.success(vos);
    }
}
