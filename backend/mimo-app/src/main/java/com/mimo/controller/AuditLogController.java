package com.mimo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.Result;
import com.mimo.entity.ActivityLog;
import com.mimo.mapper.ActivityLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作审计日志查看端点（v2.13.4）
 * <p>
 * 提供分页查询、按用户/对象类型/动作/时间范围筛选能力。
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {

    private final ActivityLogMapper activityLogMapper;

    /**
     * 分页查询审计日志
     */
    @GetMapping
    public Result<IPage<ActivityLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        size = Math.min(size, 100);
        QueryWrapper<ActivityLog> qw = new QueryWrapper<ActivityLog>().orderByDesc("created_at");
        if (userId != null) qw.eq("user_id", userId);
        if (StringUtils.hasText(targetType)) qw.eq("target_type", targetType);
        if (StringUtils.hasText(action)) qw.eq("action", action);
        if (targetId != null) qw.eq("target_id", targetId);
        if (StringUtils.hasText(requestId)) qw.eq("request_id", requestId);
        if (StringUtils.hasText(startTime)) qw.ge("created_at", startTime);
        if (StringUtils.hasText(endTime)) qw.le("created_at", endTime);
        IPage<ActivityLog> p = activityLogMapper.selectPage(new Page<>(page, size), qw);
        return Result.success(p);
    }

    /**
     * 查询指定对象的所有操作记录（用于任务详情页的"历史动态"侧边栏）
     */
    @GetMapping("/target")
    public Result<List<ActivityLog>> listByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        limit = Math.min(limit, 200);
        List<ActivityLog> list = activityLogMapper.selectList(
                new QueryWrapper<ActivityLog>()
                        .eq("target_type", targetType)
                        .eq("target_id", targetId)
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit));
        return Result.success(list);
    }
}
