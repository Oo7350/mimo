package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.CalendarEventDTO.*;
import com.mimo.service.CalendarEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarEventService calendarEventService;

    @PostMapping
    public Result<EventVO> create(@RequestBody CreateRequest request, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(calendarEventService.create(userId, request));
    }

    @PutMapping("/{id}")
    public Result<EventVO> update(@PathVariable Long id,
                                   @RequestBody UpdateRequest request,
                                   Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(calendarEventService.update(id, userId, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        calendarEventService.delete(id, userId);
        return Result.successMessage("事件已删除");
    }

    @GetMapping
    public Result<List<EventVO>> listEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String eventType,
            Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(calendarEventService.listByUserAndDateRange(userId, start, end, teamId, projectId, eventType));
    }

    @GetMapping("/{id}")
    public Result<EventVO> getById(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(calendarEventService.getById(id, userId));
    }

    // ---- helper ----

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new RuntimeException("未登录或登录状态异常");
    }
}
