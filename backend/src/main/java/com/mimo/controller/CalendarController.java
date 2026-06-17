package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.dto.CalendarEventDTO.*;
import com.mimo.service.CalendarEventService;
import com.mimo.service.TeamService;
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
    private final TeamService teamService;

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
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) Long projectId,
            Authentication auth) {
        Long userId = getLongPrincipal(auth);
        return Result.success(calendarEventService.listByUserAndDateRange(userId, start, end, projectId));
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
