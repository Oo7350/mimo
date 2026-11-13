package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.DeliveryAnalyticsDTO.CfdPoint;
import com.mimo.dto.DeliveryAnalyticsDTO.CfdVO;
import com.mimo.dto.DeliveryAnalyticsDTO.VelocitySprintVO;
import com.mimo.dto.DeliveryAnalyticsDTO.VelocityVO;
import com.mimo.entity.BurndownSnapshot;
import com.mimo.entity.DeliveryDailySnapshot;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Sprint;
import com.mimo.mapper.BurndownSnapshotMapper;
import com.mimo.mapper.DeliveryDailySnapshotMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryAnalyticsService {

    private static final List<String> STATUSES = List.of(
            "TODO", "IN_PROGRESS", "PENDING_REVIEW", "DONE");

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final IssueReviewMapper issueReviewMapper;
    private final BurndownSnapshotMapper burndownSnapshotMapper;
    private final DeliveryDailySnapshotMapper deliverySnapshotMapper;

    public VelocityVO getVelocity(Long projectId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 12));
        List<Sprint> allSprints = sprintMapper.selectList(new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .in(Sprint::getStatus, List.of("ACTIVE", "COMPLETED"))
                .orderByAsc(Sprint::getEndDate)
                .orderByAsc(Sprint::getId));
        List<Sprint> sprints = allSprints.subList(
                Math.max(0, allSprints.size() - safeLimit), allSprints.size());

        List<VelocitySprintVO> points = new ArrayList<>();
        double completedSum = 0;
        int averageCount = 0;
        for (Sprint sprint : sprints) {
            List<Issue> issues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getSprintId, sprint.getId()));
            int currentPoints = sumStoryPoints(issues);
            BurndownSnapshot firstSnapshot = burndownSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<BurndownSnapshot>()
                            .eq(BurndownSnapshot::getSprintId, sprint.getId())
                            .orderByAsc(BurndownSnapshot::getSnapshotDate)
                            .last("LIMIT 1"));
            int committed = firstSnapshot == null ? currentPoints
                    : firstSnapshot.getTotalPoints();
            int completed = sumStoryPoints(issues.stream()
                    .filter(this::isCompleted).toList());

            VelocitySprintVO point = new VelocitySprintVO();
            point.setSprintId(sprint.getId());
            point.setSprintName(sprint.getName());
            point.setStatus(sprint.getStatus());
            point.setStartDate(sprint.getStartDate());
            point.setEndDate(sprint.getEndDate());
            point.setCommittedPoints(committed);
            point.setCompletedPoints(completed);
            point.setCompletionRate(committed <= 0 ? 0.0
                    : Math.round(completed * 10000.0 / committed) / 100.0);
            points.add(point);
            if ("COMPLETED".equals(sprint.getStatus())
                    || (sprint.getEndDate() != null
                    && !sprint.getEndDate().isAfter(LocalDate.now()))) {
                completedSum += completed;
                averageCount++;
            }
        }

        VelocityVO vo = new VelocityVO();
        vo.setProjectId(projectId);
        vo.setSprints(points);
        vo.setAverageSprintCount(averageCount);
        vo.setAverageCompletedPoints(averageCount == 0 ? 0.0
                : Math.round(completedSum * 10.0 / averageCount) / 10.0);
        return vo;
    }

    @Transactional
    public CfdVO getCfd(Long projectId, Long sprintId, LocalDate from, LocalDate to) {
        Sprint sprint = null;
        if (sprintId != null) {
            sprint = sprintMapper.selectById(sprintId);
            if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);
            if (!Objects.equals(projectId, sprint.getProjectId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Sprint 不属于该项目");
            }
        }
        refreshTodaySnapshot(projectId);

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from
                : sprint != null ? sprint.getStartDate() : endDate.minusDays(13);
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (startDate.plusDays(366).isBefore(endDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "累积流图查询范围不能超过 366 天");
        }

        LambdaQueryWrapper<DeliveryDailySnapshot> query = new LambdaQueryWrapper<DeliveryDailySnapshot>()
                .eq(DeliveryDailySnapshot::getProjectId, projectId)
                .between(DeliveryDailySnapshot::getSnapshotDate, startDate, endDate)
                .orderByAsc(DeliveryDailySnapshot::getSnapshotDate);
        if (sprintId != null) query.eq(DeliveryDailySnapshot::getSprintId, sprintId);
        List<DeliveryDailySnapshot> snapshots = deliverySnapshotMapper.selectList(query);

        Map<LocalDate, int[]> counts = new TreeMap<>();
        for (DeliveryDailySnapshot snapshot : snapshots) {
            int[] row = counts.computeIfAbsent(snapshot.getSnapshotDate(), key -> new int[4]);
            int index = STATUSES.indexOf(snapshot.getStatusCategory());
            if (index >= 0) row[index] += snapshot.getIssueCount() == null
                    ? 0 : snapshot.getIssueCount();
        }

        List<CfdPoint> points = counts.entrySet().stream().map(entry -> {
            int[] row = entry.getValue();
            CfdPoint point = new CfdPoint();
            point.setDate(entry.getKey());
            point.setTodoCount(row[0]);
            point.setInProgressCount(row[1]);
            point.setPendingReviewCount(row[2]);
            point.setDoneCount(row[3]);
            point.setTotalCount(row[0] + row[1] + row[2] + row[3]);
            return point;
        }).toList();

        CfdVO vo = new CfdVO();
        vo.setProjectId(projectId);
        vo.setSprintId(sprintId);
        vo.setFromDate(startDate);
        vo.setToDate(endDate);
        vo.setPoints(points);
        return vo;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void refreshTodaySnapshot(Long projectId) {
        List<Issue> issues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId));
        Set<Long> pendingReviewIssueIds = loadPendingReviewIssueIds(issues);
        Map<Long, int[]> bySprint = new TreeMap<>();

        for (Issue issue : issues) {
            long sprintKey = issue.getSprintId() == null ? 0L : issue.getSprintId();
            int[] counts = bySprint.computeIfAbsent(sprintKey, key -> new int[4]);
            int index = STATUSES.indexOf(statusCategory(issue, pendingReviewIssueIds));
            if (index >= 0) counts[index]++;
        }
        if (bySprint.isEmpty()) {
            bySprint.put(0L, new int[4]);
        }

        LocalDate today = LocalDate.now();
        deliverySnapshotMapper.delete(new LambdaQueryWrapper<DeliveryDailySnapshot>()
                .eq(DeliveryDailySnapshot::getProjectId, projectId)
                .eq(DeliveryDailySnapshot::getSnapshotDate, today));
        for (Map.Entry<Long, int[]> entry : bySprint.entrySet()) {
            for (int i = 0; i < STATUSES.size(); i++) {
                DeliveryDailySnapshot snapshot = new DeliveryDailySnapshot();
                snapshot.setProjectId(projectId);
                snapshot.setSprintId(entry.getKey());
                snapshot.setSnapshotDate(today);
                snapshot.setStatusCategory(STATUSES.get(i));
                snapshot.setIssueCount(entry.getValue()[i]);
                deliverySnapshotMapper.insert(snapshot);
            }
        }
    }

    private Set<Long> loadPendingReviewIssueIds(List<Issue> issues) {
        if (issues.isEmpty()) return Collections.emptySet();
        Set<Long> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toSet());
        return issueReviewMapper.selectList(new LambdaQueryWrapper<IssueReview>()
                        .in(IssueReview::getIssueId, issueIds)
                        .eq(IssueReview::getStatus, "PENDING"))
                .stream().map(IssueReview::getIssueId).collect(Collectors.toSet());
    }

    private String statusCategory(Issue issue, Set<Long> pendingReviewIssueIds) {
        if (pendingReviewIssueIds.contains(issue.getId())) return "PENDING_REVIEW";
        if (isCompleted(issue)) return "DONE";
        if ("IN_PROGRESS".equals(issue.getStatus())) return "IN_PROGRESS";
        return "TODO";
    }

    private int sumStoryPoints(List<Issue> issues) {
        return issues.stream()
                .map(Issue::getStoryPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private boolean isCompleted(Issue issue) {
        return "DONE".equals(issue.getStatus())
                || "VERIFIED".equals(issue.getBugStatus())
                || "CLOSED".equals(issue.getBugStatus());
    }
}
