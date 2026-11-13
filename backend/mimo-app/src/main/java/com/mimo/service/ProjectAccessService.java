package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMapper projectMapper;
    private final IssueMapper issueMapper;
    private final UserMapper userMapper;
    private final TeamService teamService;

    public Project requireProject(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        if (!isSystemAdmin(userId)
                && !teamService.isTeamMember(project.getTeamId(), userId)
                && !teamService.isTeamAdmin(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该项目");
        }
        return project;
    }

    public void requireTeam(Long teamId, Long userId) {
        if (!isSystemAdmin(userId)
                && !teamService.isTeamMember(teamId, userId)
                && !teamService.isTeamAdmin(teamId, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该团队");
        }
    }

    public Issue requireIssue(Long issueId, Long userId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        requireProject(issue.getProjectId(), userId);
        return issue;
    }

    public boolean isSystemAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && "ROLE_ADMIN".equals(user.getRole());
    }
}
