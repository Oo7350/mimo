package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkLogDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkLogService {

    private final WorkLogMapper workLogMapper;
    private final IssueMapper issueMapper;
    private final SprintMapper sprintMapper;
    private final ProjectMapper projectMapper;
    private final TeamService teamService;
    private final UserMapper userMapper;

    /** 创建工时记录 */
    @Transactional
    public WorkLogVO create(CreateRequest request, Long userId) {
        Issue issue = issueMapper.selectById(request.getIssueId());
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);

        Project project = projectMapper.selectById(issue.getProjectId());
        if (project == null || !teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }

        if (request.getHours() == null || request.getHours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工时必须大于 0");
        }
        if (request.getHours().compareTo(new BigDecimal("24")) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次工时不能超过 24 小时");
        }

        WorkLog log = new WorkLog();
        log.setIssueId(request.getIssueId());
        log.setUserId(userId);
        log.setWorkDate(request.getWorkDate());
        log.setHours(request.getHours().setScale(2, RoundingMode.HALF_UP));
        log.setDescription(request.getDescription());
        workLogMapper.insert(log);
        return toVO(log, issue, userMapper.selectById(userId));
    }

    /** 列出某 Issue 的所有工时（按日期降序） */
    public List<WorkLogVO> listByIssue(Long issueId, Long currentUserId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        // 权限校验：项目成员可查看
        Project project = projectMapper.selectById(issue.getProjectId());
        if (project == null || !teamService.isTeamMember(project.getTeamId(), currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看");
        }
        List<WorkLog> logs = workLogMapper.selectList(
                new LambdaQueryWrapper<WorkLog>()
                        .eq(WorkLog::getIssueId, issueId)
                        .orderByDesc(WorkLog::getWorkDate)
                        .orderByDesc(WorkLog::getCreatedAt));
        // 批量拉取 user 减少 N+1
        Set<Long> userIds = logs.stream().map(WorkLog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        return logs.stream().map(l -> toVO(l, issue, userMap.get(l.getUserId()))).collect(Collectors.toList());
    }

    /** 删除工时（创建人或团队 admin） */
    @Transactional
    public void delete(Long workLogId, Long currentUserId) {
        WorkLog log = workLogMapper.selectById(workLogId);
        if (log == null) throw new BusinessException(ResultCode.BAD_REQUEST, "工时记录不存在");
        if (!log.getUserId().equals(currentUserId)) {
            // 非创建人：检查是否团队 admin
            Issue issue = issueMapper.selectById(log.getIssueId());
            Project project = issue != null ? projectMapper.selectById(issue.getProjectId()) : null;
            if (project == null || !teamService.isTeamAdmin(project.getTeamId(), currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅创建人或团队管理员可删除");
            }
        }
        workLogMapper.deleteById(workLogId);
    }

    /** Issue 累计工时（小时） */
    public BigDecimal sumByIssue(Long issueId) {
        List<WorkLog> logs = workLogMapper.selectList(
                new LambdaQueryWrapper<WorkLog>().eq(WorkLog::getIssueId, issueId));
        return logs.stream()
                .map(WorkLog::getHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Sprint 工时汇总：成员维度 */
    public SprintWorkloadVO getSprintWorkload(Long sprintId, Long currentUserId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);
        Project project = projectMapper.selectById(sprint.getProjectId());
        if (project == null || !teamService.isTeamMember(project.getTeamId(), currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看");
        }

        // 取出 Sprint 下所有 Issue 的工时
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprintId));
        if (issues.isEmpty()) {
            SprintWorkloadVO vo = new SprintWorkloadVO();
            vo.setSprintId(sprintId);
            vo.setSprintName(sprint.getName());
            vo.setTotalHours(BigDecimal.ZERO);
            vo.setMemberCount(0);
            vo.setMembers(List.of());
            return vo;
        }
        Set<Long> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toSet());
        Map<Long, Issue> issueMap = issues.stream().collect(Collectors.toMap(Issue::getId, i -> i));

        List<WorkLog> logs = workLogMapper.selectList(
                new LambdaQueryWrapper<WorkLog>().in(WorkLog::getIssueId, issueIds));
        Set<Long> userIds = logs.stream().map(WorkLog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        // 按 user 聚合
        Map<Long, MemberWorkloadVO> agg = new HashMap<>();
        for (WorkLog l : logs) {
            MemberWorkloadVO m = agg.computeIfAbsent(l.getUserId(), uid -> {
                MemberWorkloadVO x = new MemberWorkloadVO();
                x.setUserId(uid);
                User u = userMap.get(uid);
                if (u != null) {
                    x.setUsername(u.getUsername());
                    x.setAvatar(u.getAvatar());
                }
                x.setTotalHours(BigDecimal.ZERO);
                x.setLogCount(0);
                x.setIssueCount(0);
                return x;
            });
            m.setTotalHours(m.getTotalHours().add(l.getHours()));
            m.setLogCount(m.getLogCount() + 1);
        }
        // 统计涉及 issue 数
        for (WorkLog l : logs) {
            MemberWorkloadVO m = agg.get(l.getUserId());
            Issue i = issueMap.get(l.getIssueId());
            if (i != null && m != null) m.setIssueCount(m.getIssueCount() + 1);
        }
        List<MemberWorkloadVO> members = agg.values().stream()
                .sorted((a, b) -> b.getTotalHours().compareTo(a.getTotalHours()))
                .collect(Collectors.toList());
        for (MemberWorkloadVO m : members) {
            m.setTotalHours(m.getTotalHours().setScale(2, RoundingMode.HALF_UP));
        }

        SprintWorkloadVO vo = new SprintWorkloadVO();
        vo.setSprintId(sprintId);
        vo.setSprintName(sprint.getName());
        vo.setTotalHours(members.stream().map(MemberWorkloadVO::getTotalHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        vo.setMemberCount(members.size());
        vo.setMembers(members);
        return vo;
    }

    private WorkLogVO toVO(WorkLog log, Issue issue, User user) {
        WorkLogVO vo = new WorkLogVO();
        vo.setId(log.getId());
        vo.setIssueId(log.getIssueId());
        if (issue != null) {
            vo.setIssueKey(issue.getIssueKey());
            vo.setIssueTitle(issue.getTitle());
        }
        vo.setUserId(log.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }
        vo.setWorkDate(log.getWorkDate());
        vo.setHours(log.getHours());
        vo.setDescription(log.getDescription());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
