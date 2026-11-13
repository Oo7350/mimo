package com.mimo.service;

import com.mimo.dto.DeliveryAnalyticsDTO.CfdVO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAnalyticsServiceTest {

    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private IssueReviewMapper issueReviewMapper;
    @Mock
    private BurndownSnapshotMapper burndownSnapshotMapper;
    @Mock
    private DeliveryDailySnapshotMapper deliverySnapshotMapper;

    @Test
    void shouldSeparateCommittedAndCompletedVelocity() {
        Sprint first = sprint(1L, "Sprint 1", "COMPLETED");
        Sprint second = sprint(2L, "Sprint 2", "COMPLETED");
        when(sprintMapper.selectList(any())).thenReturn(List.of(first, second));

        Issue firstDone = issue(11L, "DONE", 5, null, 1L);
        Issue firstOpen = issue(12L, "TODO", 3, null, 1L);
        Issue secondDone = issue(21L, "DONE", 3, null, 2L);
        Issue secondOpen = issue(22L, "IN_PROGRESS", 2, null, 2L);
        when(issueMapper.selectList(any()))
                .thenReturn(List.of(firstDone, firstOpen))
                .thenReturn(List.of(secondDone, secondOpen));

        BurndownSnapshot commitmentOne = new BurndownSnapshot();
        commitmentOne.setTotalPoints(8);
        BurndownSnapshot commitmentTwo = new BurndownSnapshot();
        commitmentTwo.setTotalPoints(5);
        when(burndownSnapshotMapper.selectOne(any()))
                .thenReturn(commitmentOne)
                .thenReturn(commitmentTwo);

        VelocityVO result = service().getVelocity(7L, 6);

        assertThat(result.getSprints()).hasSize(2);
        assertThat(result.getSprints().get(0).getCommittedPoints()).isEqualTo(8);
        assertThat(result.getSprints().get(0).getCompletedPoints()).isEqualTo(5);
        assertThat(result.getSprints().get(1).getCommittedPoints()).isEqualTo(5);
        assertThat(result.getSprints().get(1).getCompletedPoints()).isEqualTo(3);
        assertThat(result.getAverageCompletedPoints()).isEqualTo(4.0);
    }

    @Test
    void shouldReturnCfdPointsInDateOrderAndCountPendingReview() {
        Issue todo = issue(31L, "TODO", 2, null, 1L);
        Issue inProgress = issue(32L, "IN_PROGRESS", 3, null, 1L);
        Issue pending = issue(33L, "IN_PROGRESS", 2, null, 1L);
        Issue done = issue(34L, "DONE", 5, null, 1L);
        when(issueMapper.selectList(any())).thenReturn(List.of(todo, inProgress, pending, done));

        IssueReview review = new IssueReview();
        review.setIssueId(33L);
        review.setStatus("PENDING");
        when(issueReviewMapper.selectList(any())).thenReturn(List.of(review));

        LocalDate today = LocalDate.now();
        List<DeliveryDailySnapshot> stored = List.of(
                snapshot(today.minusDays(1), "DONE", 1),
                snapshot(today.minusDays(1), "TODO", 2),
                snapshot(today.minusDays(1), "IN_PROGRESS", 1),
                snapshot(today, "DONE", 1),
                snapshot(today, "TODO", 1),
                snapshot(today, "IN_PROGRESS", 1),
                snapshot(today, "PENDING_REVIEW", 1)
        );
        when(deliverySnapshotMapper.selectList(any())).thenReturn(stored);

        CfdVO result = service().getCfd(7L, null, today.minusDays(1), today);

        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getPoints().get(0).getDate()).isEqualTo(today.minusDays(1));
        assertThat(result.getPoints().get(0).getTotalCount()).isEqualTo(4);
        assertThat(result.getPoints().get(1).getDate()).isEqualTo(today);
        assertThat(result.getPoints().get(1).getPendingReviewCount()).isEqualTo(1);
        assertThat(result.getPoints().get(1).getTotalCount()).isEqualTo(4);
    }

    private DeliveryAnalyticsService service() {
        return new DeliveryAnalyticsService(sprintMapper, issueMapper, issueReviewMapper,
                burndownSnapshotMapper, deliverySnapshotMapper);
    }

    private Sprint sprint(Long id, String name, String status) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setProjectId(7L);
        sprint.setName(name);
        sprint.setStatus(status);
        sprint.setStartDate(LocalDate.now().minusDays(14));
        sprint.setEndDate(LocalDate.now().minusDays(1));
        return sprint;
    }

    private Issue issue(Long id, String status, Integer points,
                        Long sprintId, Long projectId) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(projectId);
        issue.setSprintId(sprintId);
        issue.setStatus(status);
        issue.setStoryPoints(points);
        return issue;
    }

    private DeliveryDailySnapshot snapshot(LocalDate date, String status, int count) {
        DeliveryDailySnapshot snapshot = new DeliveryDailySnapshot();
        snapshot.setSprintId(1L);
        snapshot.setSnapshotDate(date);
        snapshot.setStatusCategory(status);
        snapshot.setIssueCount(count);
        return snapshot;
    }
}
