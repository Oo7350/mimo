package com.mimo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.Result;
import com.mimo.entity.EmailSendLog;
import com.mimo.mapper.EmailSendLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮件发送日志查询端点（v2.13.6）
 * <p>
 * 用于排查邮件通知失败，按用户 / 类型 / 结果 / 时间范围筛选。
 */
@RestController
@RequestMapping("/api/email-send-logs")
@RequiredArgsConstructor
@Slf4j
public class EmailSendLogController {

    private final EmailSendLogMapper emailSendLogMapper;

    @GetMapping
    public Result<IPage<EmailSendLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String notifyType,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        size = Math.min(size, 100);
        QueryWrapper<EmailSendLog> qw = new QueryWrapper<EmailSendLog>().orderByDesc("created_at");
        if (userId != null) qw.eq("user_id", userId);
        if (StringUtils.hasText(notifyType)) qw.eq("notify_type", notifyType);
        if (success != null) qw.eq("success", success);
        if (StringUtils.hasText(startTime)) qw.ge("created_at", startTime);
        if (StringUtils.hasText(endTime)) qw.le("created_at", endTime);
        IPage<EmailSendLog> p = emailSendLogMapper.selectPage(new Page<>(page, size), qw);
        return Result.success(p);
    }

    /** 简单统计：总数 / 成功 / 失败 / 平均耗时 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        QueryWrapper<EmailSendLog> qw = new QueryWrapper<>();
        if (StringUtils.hasText(startTime)) qw.ge("created_at", startTime);
        if (StringUtils.hasText(endTime)) qw.le("created_at", endTime);

        long total = emailSendLogMapper.selectCount(qw);
        QueryWrapper<EmailSendLog> okQw = qw.clone();
        okQw.eq("success", 1);
        long ok = emailSendLogMapper.selectCount(okQw);
        long failed = total - ok;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("success", ok);
        stats.put("failed", failed);
        stats.put("deliveryRate", total > 0 ? (ok * 100.0 / total) : 0.0);
        return Result.success(stats);
    }
}
