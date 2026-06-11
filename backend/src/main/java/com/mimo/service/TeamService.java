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
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.entity.ActivityLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;

    public TeamVO create(CreateRequest request, Long ownerId) {
        if (teamMapper.exists(new LambdaQueryWrapper<Team>().eq(Team::getName, request.getName()))) {
            throw new BusinessException(ResultCode.TEAM_NAME_EXISTS);
        }
        Team team = new Team();
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setOwnerId(ownerId);
        teamMapper.insert(team);
        // 创建者自动成为团队管理员
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
        // 检查操作者是否为管理员
        TeamMember opMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, request.getTeamId()).eq(TeamMember::getUserId, operatorId));
        if (opMember == null || !"ROLE_ADMIN".equals(opMember.getRole())) {
            throw new BusinessException(ResultCode.NOT_TEAM_ADMIN);
        }
        if (teamMemberMapper.exists(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, request.getTeamId()).eq(TeamMember::getUserId, request.getUserId()))) {
            throw new BusinessException(ResultCode.ALREADY_IN_TEAM);
        }
        TeamMember member = new TeamMember();
        member.setTeamId(request.getTeamId());
        member.setUserId(request.getUserId());
        member.setRole(request.getRole() != null ? request.getRole() : "ROLE_MEMBER");
        teamMemberMapper.insert(member);
    }

    public List<MemberVO> listMembers(Long teamId) {
        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        return members.stream().map(m -> {
            User user = userMapper.selectById(m.getUserId());
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
        TeamMember opMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, operatorId));
        if (opMember == null || !"ROLE_ADMIN".equals(opMember.getRole())) {
            throw new BusinessException(ResultCode.NOT_TEAM_ADMIN);
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
