package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.ProjectDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final IssueMapper issueMapper;
    private final ReportMapper reportMapper;
    private final SprintMapper sprintMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;

    @Transactional
    public ProjectVO create(CreateRequest request, Long ownerId) {
        // 检查key唯一性
        if (projectMapper.exists(new LambdaQueryWrapper<Project>().eq(Project::getKey, request.getKey()))) {
            throw new BusinessException(ResultCode.CONFLICT, "项目Key已存在");
        }
        Project project = new Project();
        project.setName(request.getName());
        project.setKey(request.getKey().toUpperCase());
        project.setDescription(request.getDescription());
        project.setTemplate(request.getTemplate());
        project.setTeamId(request.getTeamId());
        project.setOwnerId(ownerId);
        projectMapper.insert(project);

        // 创建者加入项目
        ProjectMember pm = new ProjectMember();
        pm.setProjectId(project.getId());
        pm.setUserId(ownerId);
        pm.setRole("ROLE_ADMIN");
        projectMemberMapper.insert(pm);

        // 创建默认看板列
        createDefaultColumns(project.getId(), request.getTemplate());

        return toVO(project);
    }

    private void createDefaultColumns(Long projectId, String template) {
        String[][] defaultCols = {
                {"待办", "#909399"},
                {"进行中", "#409EFF"},
                {"已完成", "#67C23A"}
        };
        for (int i = 0; i < defaultCols.length; i++) {
            BoardColumn col = new BoardColumn();
            col.setProjectId(projectId);
            col.setName(defaultCols[i][0]);
            col.setColor(defaultCols[i][1]);
            col.setSortOrder(i);
            boardColumnMapper.insert(col);
        }
    }

    public ProjectVO getById(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        return toVO(project);
    }

    public List<ProjectVO> listByTeam(Long teamId) {
        return projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getTeamId, teamId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<ProjectVO> listByUser(Long userId) {
        List<Long> projectIds = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).collect(Collectors.toList());
        if (projectIds.isEmpty()) return List.of();
        return projectMapper.selectBatchIds(projectIds).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public void addMember(Long projectId, Long userId) {
        if (!projectMemberMapper.exists(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId))) {
            ProjectMember pm = new ProjectMember();
            pm.setProjectId(projectId);
            pm.setUserId(userId);
            pm.setRole("ROLE_MEMBER");
            projectMemberMapper.insert(pm);
        }
    }

    /**
     * 删除项目：级联删除看板列、任务、成员关系、报告等
     */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        // 级联删除关联数据
        boardColumnMapper.delete(new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId));
        issueMapper.delete(new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, projectId));
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        reportMapper.delete(new LambdaQueryWrapper<Report>().eq(Report::getProjectId, projectId));
        sprintMapper.delete(new LambdaQueryWrapper<Sprint>().eq(Sprint::getProjectId, projectId));
        // 删除项目
        projectMapper.deleteById(projectId);
    }

    private ProjectVO toVO(Project project) {
        User owner = userMapper.selectById(project.getOwnerId());
        Team team = teamMapper.selectById(project.getTeamId());
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setKey(project.getKey());
        vo.setDescription(project.getDescription());
        vo.setTemplate(project.getTemplate());
        vo.setTeamId(project.getTeamId());
        vo.setTeamName(team != null ? team.getName() : "");
        vo.setOwnerId(project.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getUsername() : "");
        vo.setCreatedAt(project.getCreatedAt());
        return vo;
    }
}
