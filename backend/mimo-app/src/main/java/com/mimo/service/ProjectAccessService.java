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
    private final AccessControlService accessControlService;

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
        return accessControlService.isSystemAdmin(userId);
    }

    /**
     * 判定用（不抛异常）：**能读/写这个项目里的内容**吗。
     *
     * <p>口径 = 系统管理员 / 团队成员 / 团队管理员，与 {@link #requireProject} 完全一致。
     * 各 Controller/Service 需要自己写错误文案时用它，不要再去调
     * {@code teamService.isTeamMember(...)} —— 那样会把管理员挡在门外（BUG-115/117）。</p>
     */
    public boolean canAccessProject(Long projectId, Long userId) {
        if (projectId == null || userId == null) return false;
        Project project = projectMapper.selectById(projectId);
        if (project == null) return false;
        if (isSystemAdmin(userId)) return true;
        Long teamId = project.getTeamId();
        return teamService.isTeamMember(teamId, userId) || teamService.isTeamAdmin(teamId, userId);
    }

    /**
     * 判定用（不抛异常）：**能看这个团队**吗 —— 系统管理员 / 团队成员 / 团队管理员。
     *
     * <p>与 {@link #requireTeam} 同口径；给需要自定义错误文案的地方用。</p>
     */
    public boolean canAccessTeam(Long teamId, Long userId) {
        if (teamId == null || userId == null) return false;
        if (isSystemAdmin(userId)) return true;
        return teamService.isTeamMember(teamId, userId) || teamService.isTeamAdmin(teamId, userId);
    }

    /**
     * 判定用：**能管理这个项目**吗 —— 系统管理员 / 项目负责人 / 团队管理员。
     *
     * <p>比 {@link #canAccessProject} 严一档：不要求是本团队成员，但要够"管理"级别。
     * 用在"删除别人的数据""改项目配置"这类动作上。</p>
     */
    public boolean canManageProject(Long projectId, Long userId) {
        if (projectId == null || userId == null) return false;
        Project project = projectMapper.selectById(projectId);
        if (project == null) return false;
        if (isSystemAdmin(userId)) return true;
        if (userId.equals(project.getOwnerId())) return true;
        return teamService.isTeamAdmin(project.getTeamId(), userId);
    }
}
