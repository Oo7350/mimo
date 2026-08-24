package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.CalendarEventDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventMapper calendarEventMapper;
    private final ProjectMapper projectMapper;
    private final IssueMapper issueMapper;
    private final SprintMapper sprintMapper;
    private final UserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final ObjectMapper objectMapper;

    public EventVO create(Long userId, CreateRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime(), request.getAllDay());

        // Note: team/project access is not strictly enforced on creation
        // to allow managers to create schedules for any team/project
        // If projectId but no teamId, auto-fill teamId from project
        if (request.getProjectId() != null && request.getTeamId() == null) {
            Project p = projectMapper.selectById(request.getProjectId());
            if (p != null && p.getTeamId() != null) {
                request.setTeamId(p.getTeamId());
            }
        }

        CalendarEvent event = new CalendarEvent();
        event.setUserId(userId);
        event.setTeamId(request.getTeamId());
        event.setProjectId(request.getProjectId());
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setAllDay(Boolean.TRUE.equals(request.getAllDay()) ? 1 : 0);
        event.setEventType(request.getEventType() != null ? request.getEventType() : "CUSTOM");
        event.setRelatedId(request.getRelatedId());
        event.setRelatedType(request.getRelatedType());
        event.setColor(resolveColor(event.getEventType(), request.getColor()));
        event.setLocation(request.getLocation());
        event.setParticipants(participantsToJson(request.getParticipants()));
        event.setReminderMinutes(request.getReminderMinutes() != null ? request.getReminderMinutes() : 15);
        event.setCreatedBy(userId);
        calendarEventMapper.insert(event);

        return toVO(event, userId);
    }

    public EventVO update(Long id, Long userId, UpdateRequest request) {
        CalendarEvent event = calendarEventMapper.selectById(id);
        if (event == null) throw new BusinessException(ResultCode.NOT_FOUND, "事件不存在");
        if (!event.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改此事件");
        }
        if ("TASK_DEADLINE".equals(event.getEventType()) && "ISSUE".equals(event.getRelatedType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "自动同步的截止日期事件不可编辑，请修改任务截止日期");
        }

        // Note: team/project access not strictly enforced on update either

        if (request.getTeamId() != null) event.setTeamId(request.getTeamId());
        if (request.getProjectId() != null) event.setProjectId(request.getProjectId());
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());
        if (request.getAllDay() != null) event.setAllDay(request.getAllDay() ? 1 : 0);
        if (request.getEventType() != null) {
            event.setEventType(request.getEventType());
            event.setColor(resolveColor(request.getEventType(), request.getColor()));
        } else if (request.getColor() != null) {
            event.setColor(request.getColor());
        }
        if (request.getRelatedId() != null) event.setRelatedId(request.getRelatedId());
        if (request.getRelatedType() != null) event.setRelatedType(request.getRelatedType());
        if (request.getLocation() != null) event.setLocation(request.getLocation());
        if (request.getParticipants() != null) event.setParticipants(participantsToJson(request.getParticipants()));
        if (request.getReminderMinutes() != null) event.setReminderMinutes(request.getReminderMinutes());

        validateTimeRange(event.getStartTime(), event.getEndTime(), event.getAllDay() == 1);
        calendarEventMapper.updateById(event);
        return toVO(calendarEventMapper.selectById(id), userId);
    }

    public void delete(Long id, Long userId) {
        CalendarEvent event = calendarEventMapper.selectById(id);
        if (event == null) throw new BusinessException(ResultCode.NOT_FOUND, "事件不存在");
        if (!event.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此事件");
        }
        calendarEventMapper.deleteById(id);
    }

    /**
     * 核心查询：返回当前用户可见的日程
     * 可见规则：
     * 1. 用户自己创建的日程（userId = currentUserId）
     * 2. 同团队成员创建的日程（team_id IN 用户所在团队）
     * 3. 不关联团队的日程只有创建者可见
     */
    public List<EventVO> listByUserAndDateRange(
            Long userId, LocalDateTime start, LocalDateTime end,
            Long teamId, Long projectId, String eventType) {

        // Get all team IDs the user belongs to
        List<Long> userTeamIds = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId)
        ).stream().map(TeamMember::getTeamId).collect(Collectors.toList());

        LambdaQueryWrapper<CalendarEvent> qw = new LambdaQueryWrapper<CalendarEvent>()
                .ge(CalendarEvent::getStartTime, start)
                .le(CalendarEvent::getEndTime, end)
                .orderByAsc(CalendarEvent::getStartTime);

        // Visibility: own events OR events in user's teams
        if (!userTeamIds.isEmpty()) {
            qw.and(w -> w
                    .eq(CalendarEvent::getUserId, userId)
                    .or()
                    .in(CalendarEvent::getTeamId, userTeamIds)
            );
        } else {
            qw.eq(CalendarEvent::getUserId, userId);
        }

        // Optional filters
        if (teamId != null) {
            qw.eq(CalendarEvent::getTeamId, teamId);
        }
        if (projectId != null) {
            qw.eq(CalendarEvent::getProjectId, projectId);
        }
        if (eventType != null && !eventType.isEmpty()) {
            qw.eq(CalendarEvent::getEventType, eventType);
        }

        List<CalendarEvent> events = calendarEventMapper.selectList(qw);
        return events.stream().map(e -> toVO(e, userId)).collect(Collectors.toList());
    }

    public EventVO getById(Long id, Long userId) {
        CalendarEvent event = calendarEventMapper.selectById(id);
        if (event == null) throw new BusinessException(ResultCode.NOT_FOUND, "事件不存在");
        // Check visibility
        if (!isVisible(event, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此事件");
        }
        return toVO(event, userId);
    }

    // ---- helpers ----

    private boolean isVisible(CalendarEvent event, Long userId) {
        // Owner always sees
        if (event.getUserId().equals(userId)) return true;
        // Team members see team events
        if (event.getTeamId() != null) {
            return teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, event.getTeamId())
                            .eq(TeamMember::getUserId, userId)) > 0;
        }
        return false;
    }

    private void assertTeamMember(Long teamId, Long userId) {
        long count = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        if (count == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "你不是该团队的成员");
        }
    }

    private String resolveColor(String eventType, String customColor) {
        if (customColor != null && !customColor.isEmpty()) return customColor;
        switch (eventType == null ? "" : eventType) {
            case "TASK_DEADLINE": return "#F56C6C";
            case "MEETING":      return "#409EFF";
            case "SPRINT":       return "#67C23A";
            case "REMINDER":     return "#E6A23C";
            default:             return "#909399";
        }
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end, Boolean allDay) {
        if (start == null || end == null) return;
        if (end.isBefore(start)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
    }

    private void assertProjectAccess(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        boolean isMember = projectMemberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)) > 0;
        if (!isMember) {
            throw new BusinessException(ResultCode.FORBIDDEN, "你不是该项目的成员");
        }
    }

    private String participantsToJson(List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(participantIds);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<Long> jsonToParticipants(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            Long[] arr = objectMapper.readValue(json, Long[].class);
            return Arrays.asList(arr);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private EventVO toVO(CalendarEvent event, Long currentUserId) {
        EventVO vo = EventVO.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .teamId(event.getTeamId())
                .projectId(event.getProjectId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .allDay(event.getAllDay() == 1)
                .eventType(event.getEventType())
                .relatedId(event.getRelatedId())
                .relatedType(event.getRelatedType())
                .color(event.getColor())
                .location(event.getLocation())
                .participants(jsonToParticipants(event.getParticipants()))
                .reminderMinutes(event.getReminderMinutes())
                .createdAt(event.getCreatedAt())
                .build();

        // Team name
        if (event.getTeamId() != null) {
            Team t = teamMapper.selectById(event.getTeamId());
            vo.setTeamName(t != null ? t.getName() : "");
        }

        // Project name
        if (event.getProjectId() != null) {
            Project p = projectMapper.selectById(event.getProjectId());
            vo.setProjectName(p != null ? p.getName() : "");
        }

        // Related title
        if ("ISSUE".equals(event.getRelatedType()) && event.getRelatedId() != null) {
            Issue issue = issueMapper.selectById(event.getRelatedId());
            vo.setRelatedTitle(issue != null ? issue.getTitle() : "");
        } else if ("SPRINT".equals(event.getRelatedType()) && event.getRelatedId() != null) {
            Sprint sprint = sprintMapper.selectById(event.getRelatedId());
            vo.setRelatedTitle(sprint != null ? sprint.getName() : "");
        }

        // Participant names
        if (vo.getParticipants() != null && !vo.getParticipants().isEmpty()) {
            Map<Long, User> userMap = userMapper.selectBatchIds(vo.getParticipants()).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            vo.setParticipantNames(vo.getParticipants().stream()
                    .map(id -> {
                        User u = userMap.get(id);
                        return u != null ? u.getUsername() : "未知";
                    }).collect(Collectors.toList()));
        }

        // Readonly flag: auto-synced deadline events
        vo.setReadonly("TASK_DEADLINE".equals(event.getEventType())
                && "ISSUE".equals(event.getRelatedType()));

        return vo;
    }
}
