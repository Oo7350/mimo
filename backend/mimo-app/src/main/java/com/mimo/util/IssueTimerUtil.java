package com.mimo.util;

import com.mimo.entity.Issue;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 进行中自动计时（v2.16.0）
 *
 * 规则：任务进入 IN_PROGRESS 时记录开始时间；离开 IN_PROGRESS（完成 / 回退 / 关闭）时
 * 把本轮时长累加进累计秒数并清空开始时间。状态未变化（例如同列内排序）不做任何处理。
 *
 * 不用定时任务：数据库只存「本轮开始时间 + 已累计秒数」，前端用 startedAt 做本地 tick，
 * 刷新、换设备、多人同时查看都不会丢时长，也不产生额外写入。
 */
public final class IssueTimerUtil {

    private IssueTimerUtil() {
    }

    public static void apply(Issue issue, String oldStatus) {
        if (issue == null) return;
        boolean wasRunning = "IN_PROGRESS".equals(oldStatus);
        boolean nowRunning = "IN_PROGRESS".equals(issue.getStatus());
        if (wasRunning == nowRunning) return;

        if (nowRunning) {
            issue.setInProgressStartedAt(LocalDateTime.now());
        } else {
            if (issue.getInProgressStartedAt() != null) {
                long elapsed = Duration.between(issue.getInProgressStartedAt(), LocalDateTime.now()).getSeconds();
                if (elapsed > 0) {
                    long base = issue.getInProgressSeconds() == null ? 0L : issue.getInProgressSeconds();
                    issue.setInProgressSeconds(base + elapsed);
                }
            }
            issue.setInProgressStartedAt(null);
        }
    }

    /** 已累计秒数（含正在计时的那一段），用于后端做统计时取值。 */
    public static long totalSeconds(Issue issue) {
        long base = issue.getInProgressSeconds() == null ? 0L : issue.getInProgressSeconds();
        if (issue.getInProgressStartedAt() == null) return base;
        long running = Duration.between(issue.getInProgressStartedAt(), LocalDateTime.now()).getSeconds();
        return base + Math.max(running, 0);
    }
}
