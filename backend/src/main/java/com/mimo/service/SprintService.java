package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.SprintDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final BurndownSnapshotMapper snapshotMapper;
    private final ProjectMapper projectMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final UserMapper userMapper;

    public SprintVO create(CreateRequest request) {
        Sprint sprint = new Sprint();
        sprint.setProjectId(request.getProjectId());
        sprint.setName(request.getName());
        sprint.setGoal(request.getGoal());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setIsActive(0);
        sprint.setStatus("PLANNING");
        sprintMapper.insert(sprint);
        return toVO(sprint);
    }

    public List<SprintVO> listByProject(Long projectId) {
        return sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>().eq(Sprint::getProjectId, projectId)
                        .orderByDesc(Sprint::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public SprintVO getById(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);
        return toVO(sprint);
    }

    public void startSprint(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);
        // 将其他活跃 Sprint 置为完成
        sprintMapper.selectList(new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, sprint.getProjectId()).eq(Sprint::getIsActive, 1))
                .forEach(s -> { s.setIsActive(0); sprintMapper.updateById(s); });
        sprint.setIsActive(1);
        sprint.setStatus("ACTIVE");
        sprintMapper.updateById(sprint);
        // 生成首日快照
        generateSnapshot(sprintId);
    }

    public void completeSprint(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);
        sprint.setIsActive(0);
        sprint.setStatus("COMPLETED");
        sprintMapper.updateById(sprint);
    }

    public SprintVO createQuickSprint(Long projectId) {
        // Create 2-week sprint starting today
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);

        long count = sprintMapper.selectCount(
            new LambdaQueryWrapper<Sprint>().eq(Sprint::getProjectId, projectId));
        sprint.setName("Sprint " + (count + 1));
        sprint.setStartDate(LocalDate.now());
        sprint.setEndDate(LocalDate.now().plusDays(14));
        sprint.setIsActive(1);
        sprint.setStatus("ACTIVE");
        sprintMapper.insert(sprint);

        // Deactivate other active sprints
        sprintMapper.selectList(new LambdaQueryWrapper<Sprint>()
            .eq(Sprint::getProjectId, projectId)
            .eq(Sprint::getIsActive, 1)
            .ne(Sprint::getId, sprint.getId()))
            .forEach(s -> { s.setIsActive(0); sprintMapper.updateById(s); });

        // Find first (TODO) column
        List<BoardColumn> columns = boardColumnMapper.selectList(
            new LambdaQueryWrapper<BoardColumn>()
                .eq(BoardColumn::getProjectId, projectId)
                .orderByAsc(BoardColumn::getSortOrder)
        );

        if (!columns.isEmpty()) {
            Long todoColumnId = columns.get(0).getId();

            // Priority order: HIGHEST > HIGH > MEDIUM > LOW > LOWEST
            Map<String, Integer> priorityOrder = new LinkedHashMap<>();
            priorityOrder.put("HIGHEST", 0);
            priorityOrder.put("HIGH", 1);
            priorityOrder.put("MEDIUM", 2);
            priorityOrder.put("LOW", 3);
            priorityOrder.put("LOWEST", 4);

            List<Issue> todoIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, projectId)
                    .eq(Issue::getColumnId, todoColumnId)
                    .eq(Issue::getStatus, "TODO")
                    .isNull(Issue::getSprintId)
            );

            todoIssues.sort(Comparator.comparingInt(
                i -> priorityOrder.getOrDefault(i.getPriority(), 5)));

            int added = 0;
            for (Issue issue : todoIssues) {
                if (added >= 5) break;
                issue.setSprintId(sprint.getId());
                issueMapper.updateById(issue);
                added++;
            }
        }

        generateSnapshot(sprint.getId());
        return toVO(sprint);
    }

    public void completeSprintWithMigration(Long sprintId, Long targetSprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);

        List<Issue> undoneIssues = issueMapper.selectList(
            new LambdaQueryWrapper<Issue>()
                .eq(Issue::getSprintId, sprintId)
                .ne(Issue::getStatus, "DONE")
        );

        if (targetSprintId != null) {
            for (Issue issue : undoneIssues) {
                issue.setSprintId(targetSprintId);
                issueMapper.updateById(issue);
            }
        } else {
            BoardColumn firstCol = null;
            List<BoardColumn> columns = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>()
                    .eq(BoardColumn::getProjectId, sprint.getProjectId())
                    .orderByAsc(BoardColumn::getSortOrder)
            );
            if (!columns.isEmpty()) firstCol = columns.get(0);

            for (Issue issue : undoneIssues) {
                issue.setSprintId(null);
                if (firstCol != null) {
                    issue.setColumnId(firstCol.getId());
                    issue.setStatus("TODO");
                }
                issueMapper.updateById(issue);
            }
        }

        sprint.setIsActive(0);
        sprint.setStatus("COMPLETED");
        sprintMapper.updateById(sprint);
    }

    public void addIssueToSprint(Long issueId, Long sprintId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.NOT_FOUND);
        issue.setSprintId(sprintId);
        issueMapper.updateById(issue);
    }

    public BurndownVO getBurndown(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);

        // 获取或生成快照数据
        List<BurndownSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<BurndownSnapshot>().eq(BurndownSnapshot::getSprintId, sprintId)
                        .orderByAsc(BurndownSnapshot::getSnapshotDate));

        if (snapshots.isEmpty()) {
            generateSnapshot(sprintId);
            snapshots = snapshotMapper.selectList(
                    new LambdaQueryWrapper<BurndownSnapshot>().eq(BurndownSnapshot::getSprintId, sprintId)
                            .orderByAsc(BurndownSnapshot::getSnapshotDate));
        }

        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
        int totalPoints = snapshots.isEmpty() ? 0 : snapshots.get(0).getTotalPoints();

        List<BurndownPoint> points = new ArrayList<>();
        for (BurndownSnapshot snap : snapshots) {
            BurndownPoint pt = new BurndownPoint();
            pt.setDate(snap.getSnapshotDate().toString());
            pt.setIdealRemaining(snap.getIdealRemaining().doubleValue());
            pt.setActualRemaining(snap.getRemainingPoints().doubleValue());
            points.add(pt);
        }

        BurndownVO vo = new BurndownVO();
        vo.setSprintId(sprintId);
        vo.setSprintName(sprint.getName());
        vo.setStartDate(sprint.getStartDate());
        vo.setEndDate(sprint.getEndDate());
        vo.setTotalPoints(totalPoints);
        vo.setPoints(points);
        return vo;
    }

    public void generateSnapshot(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) return;

        // 统计 Sprint 中所有任务的故事点总数
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprintId));
        int totalPoints = issues.stream().filter(i -> i.getStoryPoints() != null)
                .mapToInt(Issue::getStoryPoints).sum();
        int donePoints = issues.stream()
                .filter(i -> i.getStoryPoints() != null && "DONE".equals(i.getStatus()))
                .mapToInt(Issue::getStoryPoints).sum();
        int remaining = totalPoints - donePoints;

        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
        long daysPassed = ChronoUnit.DAYS.between(sprint.getStartDate(), today) + 1;
        if (daysPassed < 1) daysPassed = 1;
        if (daysPassed > totalDays) daysPassed = totalDays;

        BigDecimal idealRemaining = BigDecimal.valueOf(totalPoints)
                .multiply(BigDecimal.valueOf(totalDays - daysPassed))
                .divide(BigDecimal.valueOf(Math.max(totalDays - 1, 1)), 2, RoundingMode.HALF_UP);
        if (idealRemaining.compareTo(BigDecimal.ZERO) < 0) idealRemaining = BigDecimal.ZERO;

        // Upsert
        BurndownSnapshot existing = snapshotMapper.selectOne(
                new LambdaQueryWrapper<BurndownSnapshot>()
                        .eq(BurndownSnapshot::getSprintId, sprintId)
                        .eq(BurndownSnapshot::getSnapshotDate, today));
        if (existing != null) {
            existing.setTotalPoints(totalPoints);
            existing.setRemainingPoints(remaining);
            existing.setCompletedPoints(donePoints);
            existing.setIdealRemaining(idealRemaining);
            snapshotMapper.updateById(existing);
        } else {
            BurndownSnapshot snap = new BurndownSnapshot();
            snap.setSprintId(sprintId);
            snap.setSnapshotDate(today);
            snap.setTotalPoints(totalPoints);
            snap.setRemainingPoints(remaining);
            snap.setCompletedPoints(donePoints);
            snap.setIdealRemaining(idealRemaining);
            snapshotMapper.insert(snap);
        }
    }

    public SprintStatsVO getSprintStats(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);

        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprintId));

        SprintStatsVO vo = new SprintStatsVO();
        vo.setSprintId(sprintId);
        vo.setSprintName(sprint.getName());
        vo.setTotalIssues(issues.size());
        int doneCount = (int) issues.stream().filter(i -> "DONE".equals(i.getStatus())).count();
        vo.setCompletedIssues(doneCount);
        vo.setOverallCompletionRate(issues.isEmpty() ? 0.0 :
                Math.round(doneCount * 10000.0 / issues.size()) / 100.0);

        // 超期：dueDate < today && status != DONE
        LocalDate today = LocalDate.now();
        int overdueCount = (int) issues.stream()
                .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(today) && !"DONE".equals(i.getStatus()))
                .count();
        vo.setOverallOverdueRate(issues.isEmpty() ? 0.0 :
                Math.round(overdueCount * 10000.0 / issues.size()) / 100.0);

        // Per-member stats
        Set<Long> assigneeIds = new HashSet<>();
        issues.forEach(i -> { if (i.getAssigneeId() != null) assigneeIds.add(i.getAssigneeId()); });
        Map<Long, User> userMap = assigneeIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(assigneeIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<Issue>> byAssignee = issues.stream()
                .filter(i -> i.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Issue::getAssigneeId));

        List<MemberStat> memberStats = new ArrayList<>();
        for (Map.Entry<Long, List<Issue>> entry : byAssignee.entrySet()) {
            List<Issue> userIssues = entry.getValue();
            int total = userIssues.size();
            int completed = (int) userIssues.stream().filter(i -> "DONE".equals(i.getStatus())).count();
            int overdue = (int) userIssues.stream()
                    .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(today) && !"DONE".equals(i.getStatus()))
                    .count();

            MemberStat stat = new MemberStat();
            stat.setAssigneeId(entry.getKey());
            User u = userMap.get(entry.getKey());
            stat.setUsername(u != null ? u.getUsername() : "未知");
            stat.setTotalAssigned(total);
            stat.setCompleted(completed);
            stat.setOverdue(overdue);
            stat.setCompletionRate(total == 0 ? 0.0 : Math.round(completed * 10000.0 / total) / 100.0);
            // Avg task age: 平均任务存在天数（反映任务处理效率）
            long nowMs = System.currentTimeMillis();
            double avgDays = userIssues.stream()
                    .mapToLong(i -> {
                        java.time.LocalDateTime createdAt = i.getCreatedAt();
                        if (createdAt == null) return 0;
                        return nowMs - createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    })
                    .average().orElse(0);
            stat.setAvgDaysInColumn(Math.round(avgDays / (1000.0 * 86400) * 10.0) / 10.0);
            memberStats.add(stat);
        }
        memberStats.sort(Comparator.comparing(MemberStat::getCompletionRate).reversed());
        vo.setMemberStats(memberStats);

        return vo;
    }

    private SprintVO toVO(Sprint sprint) {
        long total = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprint.getId()));
        long done = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprint.getId())
                        .eq(Issue::getStatus, "DONE"));
        SprintVO vo = new SprintVO();
        vo.setId(sprint.getId());
        vo.setName(sprint.getName());
        vo.setGoal(sprint.getGoal());
        vo.setStartDate(sprint.getStartDate());
        vo.setEndDate(sprint.getEndDate());
        vo.setIsActive(sprint.getIsActive() == 1);
        vo.setStatus(sprint.getStatus());
        vo.setTotalIssues((int) total);
        vo.setCompletedIssues((int) done);
        vo.setCreatedAt(sprint.getCreatedAt());
        return vo;
    }
}
