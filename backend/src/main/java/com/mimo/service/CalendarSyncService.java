package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.entity.CalendarEvent;
import com.mimo.entity.Issue;
import com.mimo.mapper.CalendarEventMapper;
import com.mimo.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final CalendarEventMapper calendarEventMapper;
    private final IssueMapper issueMapper;

    /**
     * 当任务的截止日期变更时，自动同步到日历事件
     * 由 IssueService 在更新 issue 时调用
     */
    @Async
    public void syncIssueDeadline(Long issueId) {
        try {
            Issue issue = issueMapper.selectById(issueId);
            if (issue == null || issue.getDueDate() == null) {
                // No deadline -> remove existing sync event
                removeSyncedEvent(issueId);
                return;
            }

            // Find existing sync event for this issue
            LambdaQueryWrapper<CalendarEvent> qw = new LambdaQueryWrapper<CalendarEvent>()
                    .eq(CalendarEvent::getRelatedType, "ISSUE")
                    .eq(CalendarEvent::getRelatedId, issueId)
                    .eq(CalendarEvent::getEventType, "TASK_DEADLINE");
            CalendarEvent existing = calendarEventMapper.selectOne(qw);

            if (existing != null) {
                // Update existing event
                LocalDateTime newStart = LocalDateTime.of(issue.getDueDate(), LocalTime.of(9, 0));
                LocalDateTime newEnd = LocalDateTime.of(issue.getDueDate(), LocalTime.of(18, 0));
                existing.setStartTime(newStart);
                existing.setEndTime(newEnd);
                existing.setTitle("[" + (issue.getType() != null ? issue.getType() : "任务") + "] " + issue.getTitle());
                calendarEventMapper.updateById(existing);
                log.debug("Updated calendar sync event for issue-{}", issueId);
            } else if (issue.getAssigneeId() != null) {
                // Create new sync event for assignee
                LocalDateTime start = LocalDateTime.of(issue.getDueDate(), LocalTime.of(9, 0));
                LocalDateTime end = LocalDateTime.of(issue.getDueDate(), LocalTime.of(18, 0));

                CalendarEvent event = new CalendarEvent();
                event.setUserId(issue.getAssigneeId());
                event.setProjectId(issue.getProjectId());
                event.setTitle("[" + (issue.getType() != null ? issue.getType() : "任务") + "] " + issue.getTitle());
                event.setDescription("任务截止日期 - 自动同步");
                event.setStartTime(start);
                event.setEndTime(end);
                event.setAllDay(0);
                event.setEventType("TASK_DEADLINE");
                event.setRelatedId(issueId);
                event.setRelatedType("ISSUE");
                event.setColor("#F56C6C");  // Red for deadlines
                event.setReminderMinutes(60); // 1 hour before
                event.setCreatedBy(issue.getReporterId() != null ? issue.getReporterId() : issue.getAssigneeId());
                calendarEventMapper.insert(event);
                log.debug("Created calendar sync event for issue-{} user-{}", issueId, issue.getAssigneeId());
            }
        } catch (Exception e) {
            log.error("Failed to sync calendar for issue-{}", issueId, e);
        }
    }

    /**
     * 当 Sprint 开始/结束时同步到日历
     */
    @Async
    public void syncSprintDates(Long sprintId, LocalDate startDate, LocalDate endDate, Long... userIds) {
        try {
            // Remove old sync events
            calendarEventMapper.delete(new LambdaQueryWrapper<CalendarEvent>()
                    .eq(CalendarEvent::getRelatedType, "SPRINT")
                    .eq(CalendarEvent::getRelatedId, sprintId));

            if (userIds == null || userIds.length == 0) return;

            String title = "Sprint 周期";
            for (Long uid : userIds) {
                CalendarEvent event = new CalendarEvent();
                event.setUserId(uid);
                event.setTitle(title);
                event.setDescription("Sprint 时间范围: " + startDate + " ~ " + endDate);
                event.setStartTime(startDate.atStartOfDay());
                event.setEndTime(endDate.atTime(23, 59));
                event.setAllDay(1);
                event.setEventType("SPRINT");
                event.setRelatedId(sprintId);
                event.setRelatedType("SPRINT");
                event.setColor("#67C23A"); // Green for sprints
                event.setReminderMinutes(0);
                event.setCreatedBy(uid);
                calendarEventMapper.insert(event);
            }
            log.info("Synced sprint-{} dates to calendar for {} users", sprintId, userIds.length);
        } catch (Exception e) {
            log.error("Failed to sync calendar for sprint-{}", sprintId, e);
        }
    }

    /**
     * 删除已同步的日历事件（当 issue 被删除或取消截止日期时）
     */
    private void removeSyncedEvent(Long issueId) {
        calendarEventMapper.delete(new LambdaQueryWrapper<CalendarEvent>()
                .eq(CalendarEvent::getRelatedType, "ISSUE")
                .eq(CalendarEvent::getRelatedId, issueId)
                .eq(CalendarEvent::getEventType, "TASK_DEADLINE"));
    }
}
