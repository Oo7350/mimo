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
