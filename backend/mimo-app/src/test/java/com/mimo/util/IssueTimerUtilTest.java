package com.mimo.util;

import com.mimo.entity.Issue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IssueTimerUtilTest {

    private Issue issue(String status) {
        Issue issue = new Issue();
        issue.setId(1L);
        issue.setStatus(status);
        issue.setInProgressSeconds(0L);
        return issue;
    }

    @Test
    void shouldStartTimerWhenEnteringInProgress() {
        Issue issue = issue("IN_PROGRESS");

        IssueTimerUtil.apply(issue, "TODO");

        assertThat(issue.getInProgressStartedAt()).isNotNull();
        assertThat(issue.getInProgressSeconds()).isZero();
    }

    @Test
    void shouldAccumulateAndClearWhenLeavingInProgress() {
        Issue issue = issue("DONE");
        issue.setInProgressSeconds(120L);
        issue.setInProgressStartedAt(LocalDateTime.now().minusSeconds(90));

        IssueTimerUtil.apply(issue, "IN_PROGRESS");

        assertThat(issue.getInProgressStartedAt()).isNull();
        // 120 + 90 左右，允许几秒误差
        assertThat(issue.getInProgressSeconds()).isBetween(200L, 215L);
    }

    @Test
    void shouldKeepAccumulatedTimeWhenReenteringInProgress() {
        Issue issue = issue("IN_PROGRESS");
        issue.setInProgressSeconds(300L);

        IssueTimerUtil.apply(issue, "TODO");

        assertThat(issue.getInProgressStartedAt()).isNotNull();
        assertThat(issue.getInProgressSeconds()).isEqualTo(300L);
    }

    @Test
    void shouldDoNothingWhenStatusUnchanged() {
        Issue issue = issue("IN_PROGRESS");
        IssueTimerUtil.apply(issue, "IN_PROGRESS");
        assertThat(issue.getInProgressStartedAt()).isNull();

        Issue done = issue("DONE");
        done.setInProgressSeconds(60L);
        IssueTimerUtil.apply(done, "DONE");
        assertThat(done.getInProgressSeconds()).isEqualTo(60L);
    }

    @Test
    void totalSecondsShouldIncludeRunningSegment() {
        Issue issue = issue("IN_PROGRESS");
        issue.setInProgressSeconds(60L);
        issue.setInProgressStartedAt(LocalDateTime.now().minusSeconds(30));

        assertThat(IssueTimerUtil.totalSeconds(issue)).isBetween(85L, 95L);
        // 90 秒 ≈ 1 分钟，验证分钟换算可用
        assertThat(Duration.ofSeconds(IssueTimerUtil.totalSeconds(issue)).toMinutes()).isEqualTo(1L);
    }
}
