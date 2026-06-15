package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.TeamDTO.*;
import com.mimo.entity.Team;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.entity.UserLevel;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.UserLevelMapper;
import com.mimo.entity.ActivityLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;
    private final UserLevelMapper userLevelMapper;

    public TeamVO create(CreateRequest request, Long ownerId) {
        if (teamMapper.exists(new LambdaQueryWrapper<Team>().eq(Team::getName, request.getName()))) {
            throw new BusinessException(ResultCode.TEAM_NAME_EXISTS);
        }
        Team team = new Team();
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setOwnerId(ownerId);
        teamMapper.insert(team);
        // 创建者自动成为团队管理员（群主）
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(ownerId);
        member.setRole("ROLE_ADMIN");
        teamMemberMapper.insert(member);
        return toVO(team);
    }

    public TeamVO getById(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) throw new BusinessException(ResultCode.TEAM_NOT_FOUND);
        return toVO(team);
    }

    public List<TeamVO> listByUser(Long userId) {
        List<Long> teamIds = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId))
                .stream().map(TeamMember::getTeamId).collect(Collectors.toList());
        if (teamIds.isEmpty()) return List.of();
        return teamMapper.selectBatchIds(teamIds).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public void inviteMember(InviteRequest request, Long operatorId) {
        Team team = teamMapper.selectById(request.getTeamId());
        if (team == null) throw new BusinessException(ResultCode.TEAM_NOT_FOUND);
        // 检查操作者是否为管理员（系统admin 或 团队admin）
        if (!isTeamAdmin(request.getTeamId(), operatorId)) {
            throw new BusinessException(ResultCode.NOT_TEAM_ADMIN);
        }
        if (teamMemberMapper.exists(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, request.getTeamId()).eq(TeamMember::getUserId, request.getUserId()))) {
            throw new BusinessException(ResultCode.ALREADY_IN_TEAM);
        }

        String role = request.getRole() != null ? request.getRole() : "ROLE_MEMBER";

        TeamMember member = new TeamMember();
        member.setTeamId(request.getTeamId());
        member.setUserId(request.getUserId());
        member.setRole(role);
        teamMemberMapper.insert(member);
    }

    public List<MemberVO> listMembers(Long teamId) {
        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        // 批量加载用户
        List<Long> userIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        return members.stream().map(m -> {
            User user = userMap.get(m.getUserId());
            MemberVO vo = new MemberVO();
            vo.setUserId(m.getUserId());
            vo.setUsername(user != null ? user.getUsername() : "");
            vo.setEmail(user != null ? user.getEmail() : "");
            vo.setAvatar(user != null ? user.getAvatar() : null);
            vo.setRole(m.getRole());
            vo.setJoinedAt(m.getJoinedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void removeMember(Long teamId, Long userId, Long operatorId) {
        // 检查操作者是否为管理员（系统admin 或 团队admin）
        if (!isTeamAdmin(teamId, operatorId)) {
            throw new BusinessException(ResultCode.NOT_TEAM_ADMIN);
        }
        // 不能移除自己
        if (userId.equals(operatorId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能移除自己，请使用退出团队功能");
        }
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
    }

    /**
     * 删除团队：仅团队所有者可操作，级联删除成员关系
     */
    @Transactional
    public void deleteTeam(Long teamId, Long operatorId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) throw new BusinessException(ResultCode.TEAM_NOT_FOUND);
        if (!team.getOwnerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅团队所有者可删除团队");
        }
        // 审计日志
        User operator = userMapper.selectById(operatorId);
        ActivityLog log = new ActivityLog();
        log.setUserId(operatorId);
        log.setUsername(operator != null ? operator.getUsername() : "未知");
        log.setTargetType("TEAM");
        log.setTargetId(teamId);
        log.setAction("DELETE");
        log.setDetail("删除团队: " + team.getName());
        activityLogMapper.insert(log);
        // 删除团队成员
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        // 删除团队（关联项目不删除，项目独立存在）
        teamMapper.deleteById(teamId);
    }

    /**
     * 获取当前用户在团队中的角色
     */
    public String getUserRoleInTeam(Long teamId, Long userId) {
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        if (member == null) return null;
        return member.getRole();
    }

    /**
     * 检查用户是否为团队管理员（包括系统admin）
     */
    public boolean isTeamAdmin(Long teamId, Long userId) {
        // 系统admin
        User user = userMapper.selectById(userId);
        if (user != null && "ROLE_ADMIN".equals(user.getRole())) return true;

        // 团队admin
        String role = getUserRoleInTeam(teamId, userId);
        return "ROLE_ADMIN".equals(role);
    }

    /**
     * 判断用户是否为团队成员（含管理员）
     */
    public boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)) != null;
    }

    /**
     * 检查用户是否为团队群主
     */
    public boolean isTeamOwner(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        return team != null && team.getOwnerId().equals(userId);
    }

    /**
     * 退出团队（非群主可用）
     */
    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        // 群主不能退出，只能转让或解散
        if (isTeamOwner(teamId, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "群主不能退出团队，请先转让群主或解散团队");
        }
        
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "您不在该团队中");
        }
        
        teamMemberMapper.deleteById(member.getId());
    }

    /**
     * 转让群主（仅当前群主可用）
     */
    @Transactional
    public void transferOwner(Long teamId, Long currentOwnerId, Long newOwnerId) {
        // 验证当前操作者是群主
        if (!isTeamOwner(teamId, currentOwnerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有群主可以转让群主身份");
        }
        
        // 验证新群主是团队成员
        TeamMember newOwnerMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, newOwnerId));
        if (newOwnerMember == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "新群主必须是团队成员");
        }
        
        // 更新团队的ownerId
        Team team = teamMapper.selectById(teamId);
        team.setOwnerId(newOwnerId);
        teamMapper.updateById(team);
        
        // 新群主设为管理员（如果还不是的话）
        if (!"ROLE_ADMIN".equals(newOwnerMember.getRole())) {
            newOwnerMember.setRole("ROLE_ADMIN");
            teamMemberMapper.updateById(newOwnerMember);
        }
    }

    /**
     * 设置成员角色（仅群主可用）
     */
    @Transactional
    public void setMemberRole(Long teamId, Long targetUserId, Long operatorId, String newRole) {
        // 验证操作者是群主
        if (!isTeamOwner(teamId, operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有群主可以设置管理员");
        }
        
        // 不能修改自己的角色
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改自己的角色");
        }
        
        // 验证目标成员存在
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, targetUserId));
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该用户不是团队成员");
        }
        
        member.setRole(newRole);
        teamMemberMapper.updateById(member);
    }

    private TeamVO toVO(Team team) {
        User owner = userMapper.selectById(team.getOwnerId());
        long count = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, team.getId()));
        TeamVO vo = new TeamVO();
        vo.setId(team.getId());
        vo.setName(team.getName());
        vo.setDescription(team.getDescription());
        vo.setOwnerId(team.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getUsername() : "");
        vo.setMemberCount((int) count);
        vo.setCreatedAt(team.getCreatedAt());
        return vo;
    }
}
