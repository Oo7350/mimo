package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.ai.AiService;
import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.SprintHealthDTO.AiSummaryVO;
import com.mimo.dto.SprintHealthDTO.SprintHealthVO;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintHealthService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final IssueReviewMapper issueReviewMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public SprintHealthVO getHealth(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);

        List<Issue> sprintIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprintId));
        List<Issue> projectIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, sprint.getProjectId()));

        LocalDate today = LocalDate.now();
        int totalDays = (int) Math.max(1, ChronoUnit.DAYS.between(
                sprint.getStartDate(), sprint.getEndDate()) + 1);
        int elapsedDays = calculateElapsedDays(sprint, today, totalDays);
        int remainingDays = (int) Math.max(0,
                ChronoUnit.DAYS.between(today, sprint.getEndDate()));
        double timeProgress = percent(elapsedDays, totalDays);

        int totalStoryPoints = sumStoryPoints(sprintIssues);
        int completedStoryPoints = sumStoryPoints(sprintIssues.stream()
                .filter(this::isCompleted).toList());
        int totalIssues = sprintIssues.size();
        int completedIssues = (int) sprintIssues.stream().filter(this::isCompleted).count();
        boolean useStoryPoints = totalStoryPoints > 0;
        int totalBasis = useStoryPoints ? totalStoryPoints : totalIssues;
        int completedBasis = useStoryPoints ? completedStoryPoints : completedIssues;
        int remainingBasis = Math.max(0, totalBasis - completedBasis);
        double workProgress = percent(completedBasis, totalBasis);
        double progressGap = round1(timeProgress - workProgress);

        int overdueCount = (int) sprintIssues.stream()
                .filter(issue -> !isCompleted(issue))
                .filter(issue -> issue.getDueDate() != null && issue.getDueDate().isBefore(today))
                .count();
        int blockedCount = countBlocked(sprintIssues, projectIssues);
        int pendingReviewCount = countPendingReviews(sprintIssues);
        int estimatedDelayDays = estimateDelayDays(sprint, today, elapsedDays, totalDays,
                completedBasis, remainingBasis);

        int healthScore = calculateScore(progressGap, overdueCount, blockedCount,
                pendingReviewCount, estimatedDelayDays, sprint, today, remainingBasis);
        String healthLevel = resolveHealthLevel(healthScore, progressGap, overdueCount,
                blockedCount, estimatedDelayDays, sprint, today, remainingBasis);
        List<String> riskReasons = buildRiskReasons(sprint, today, progressGap,
                overdueCount, blockedCount, pendingReviewCount, estimatedDelayDays,
                remainingBasis, useStoryPoints);
        List<String> suggestions = buildSuggestions(healthLevel, progressGap, overdueCount,
                blockedCount, pendingReviewCount, estimatedDelayDays, remainingDays,
                remainingBasis);

        SprintHealthVO vo = new SprintHealthVO();
        vo.setSprintId(sprintId);
        vo.setSprintName(sprint.getName());
        vo.setProjectId(sprint.getProjectId());
        Project project = projectMapper.selectById(sprint.getProjectId());
        vo.setProjectName(project == null ? "" : project.getName());
        vo.setSprintStatus(sprint.getStatus());
        vo.setStartDate(sprint.getStartDate());
        vo.setEndDate(sprint.getEndDate());
        vo.setCalculatedDate(today);
        vo.setTotalDays(totalDays);
        vo.setElapsedDays(elapsedDays);
        vo.setRemainingDays(remainingDays);
        vo.setTimeProgress(timeProgress);
        vo.setWorkProgress(workProgress);
        vo.setProgressGap(progressGap);
        vo.setTotalIssues(totalIssues);
        vo.setCompletedIssues(completedIssues);
        vo.setTotalStoryPoints(totalStoryPoints);
        vo.setCompletedStoryPoints(completedStoryPoints);
        vo.setRemainingStoryPoints(Math.max(0, totalStoryPoints - completedStoryPoints));
        vo.setTrackingBasis(useStoryPoints ? "STORY_POINTS" : "ISSUE_COUNT");
        vo.setOverdueCount(overdueCount);
        vo.setBlockedCount(blockedCount);
        vo.setPendingReviewCount(pendingReviewCount);
        vo.setEstimatedDelayDays(estimatedDelayDays);
        vo.setHealthScore(healthScore);
        vo.setHealthLevel(healthLevel);
        vo.setRiskReasons(riskReasons);
        vo.setSuggestions(suggestions);
        vo.setCalculatedAt(LocalDateTime.now());
        return vo;
    }

    public AiSummaryVO getAiSummary(Long sprintId, Long userId) {
        SprintHealthVO health = getHealth(sprintId);
        AiSummaryVO vo = new AiSummaryVO();
        vo.setProvider(aiService.getProviderName());
        vo.setDeterministicSuggestions(health.getSuggestions());
        try {
            String metrics = String.format(
                    "Sprint=%s，健康度=%s，分数=%d，时间进度=%.1f%%，工作进度=%.1f%%，"
                            + "总工作项=%d，已完成=%d，剩余故事点=%d，逾期=%d，阻塞=%d，"
                            + "待验收=%d，预计延期=%d天，风险=%s",
                    health.getSprintName(), health.getHealthLevel(), health.getHealthScore(),
                    health.getTimeProgress(), health.getWorkProgress(),
                    health.getTotalIssues(), health.getCompletedIssues(),
                    health.getRemainingStoryPoints(), health.getOverdueCount(),
                    health.getBlockedCount(), health.getPendingReviewCount(),
                    health.getEstimatedDelayDays(), String.join("；", health.getRiskReasons()));
            AiChatResponse response = aiService.chatWithQuota(userId, AiChatRequest.builder()
                    .systemMessage("你是资深 Scrum Master。只能依据给定指标做简短中文解读，"
                            + "不要虚构数据；给出 2-4 条可执行建议，总字数不超过 220 字。")
                    .userMessage(metrics)
                    .temperature(0.2)
                    .maxTokens(500)
                    .build());
            String content = response == null ? null : response.getContent();
            if (response == null || response.hasError() || content == null || content.isBlank()) {
                throw new IllegalStateException(content == null ? "AI 未返回内容" : content);
            }
            vo.setSummary(content.trim());
            vo.setAiAvailable(true);
        } catch (Exception e) {
            log.warn("Sprint 健康度 AI 解读不可用，返回确定性建议: sprintId={}, reason={}",
                    sprintId, e.getMessage());
            vo.setSummary("AI 解读暂不可用，以下为基于当前指标生成的确定性建议："
                    + String.join("；", health.getSuggestions()));
            vo.setAiAvailable(false);
        }
        return vo;
    }

    private int calculateElapsedDays(Sprint sprint, LocalDate today, int totalDays) {
        if (today.isBefore(sprint.getStartDate())) return 0;
        if (today.isAfter(sprint.getEndDate())) return totalDays;
        return (int) Math.min(totalDays,
                ChronoUnit.DAYS.between(sprint.getStartDate(), today) + 1);
    }

    private int estimateDelayDays(Sprint sprint, LocalDate today, int elapsedDays,
                                  int totalDays, int completedBasis, int remainingBasis) {
        if (remainingBasis <= 0 || sprint.getStatus() == null
                || "PLANNING".equals(sprint.getStatus()) || elapsedDays <= 1) {
            return 0;
        }
        if (today.isAfter(sprint.getEndDate())) {
            return (int) Math.max(1,
                    ChronoUnit.DAYS.between(sprint.getEndDate(), today));
        }
        if (completedBasis <= 0) {
            return Math.max(1, totalDays - elapsedDays);
        }
        double pace = completedBasis * 1.0 / elapsedDays;
        int requiredDays = (int) Math.ceil(remainingBasis / pace);
        int projectedFinishDay = elapsedDays + requiredDays;
        return Math.max(0, projectedFinishDay - totalDays);
    }

    private int calculateScore(double progressGap, int overdueCount, int blockedCount,
                               int pendingReviewCount, int delayDays, Sprint sprint,
                               LocalDate today, int remainingBasis) {
        double score = 100;
        score -= Math.max(0, progressGap) * 1.5;
        score -= overdueCount * 8;
        score -= blockedCount * 5;
        score -= pendingReviewCount * 2;
        score -= delayDays * 4;
        if (today.isAfter(sprint.getEndDate()) && remainingBasis > 0) {
            score -= 30;
        }
        return (int) Math.max(0, Math.round(score));
    }

    private String resolveHealthLevel(int score, double progressGap, int overdueCount,
                                      int blockedCount, int delayDays, Sprint sprint,
                                      LocalDate today, int remainingBasis) {
        boolean critical = (today.isAfter(sprint.getEndDate()) && remainingBasis > 0)
                || progressGap >= 30
                || overdueCount >= 2
                || blockedCount >= 3
                || delayDays >= 5;
        boolean watch = progressGap >= 10 || overdueCount > 0 || blockedCount > 0
                || delayDays > 0;
        if (critical || score < 55) return "AT_RISK";
        if (watch || score < 80) return "WATCH";
        return "HEALTHY";
    }

    private List<String> buildRiskReasons(Sprint sprint, LocalDate today,
                                          double progressGap, int overdueCount,
                                          int blockedCount, int pendingReviewCount,
                                          int delayDays, int remainingBasis,
                                          boolean useStoryPoints) {
        List<String> reasons = new ArrayList<>();
        if (!useStoryPoints && !"PLANNING".equals(sprint.getStatus())) {
            reasons.add("Sprint 工作项未填写故事点，工作进度按任务数量计算");
        }
        if (today.isBefore(sprint.getStartDate())) {
            reasons.add("Sprint 尚未开始，当前仅显示计划状态");
            return reasons;
        }
        if (progressGap > 0.5) {
            reasons.add(String.format("工作进度落后时间进度 %.1f 个百分点", progressGap));
        }
        if (overdueCount > 0) {
            reasons.add(overdueCount + " 个工作项已逾期");
        }
        if (blockedCount > 0) {
            reasons.add(blockedCount + " 个工作项因前置依赖未完成而阻塞");
        }
        if (pendingReviewCount > 0) {
            reasons.add(pendingReviewCount + " 个工作项等待验收");
        }
        if (delayDays > 0) {
            reasons.add("按当前完成速度预计延期 " + delayDays + " 天");
        }
        if (today.isAfter(sprint.getEndDate()) && remainingBasis > 0) {
            reasons.add("Sprint 已超过结束日期，仍有剩余工作量");
        }
        if (reasons.isEmpty()) {
            reasons.add("时间进度与工作进度匹配，未发现逾期或阻塞");
        }
        return reasons;
    }

    private List<String> buildSuggestions(String healthLevel, double progressGap,
                                          int overdueCount, int blockedCount,
                                          int pendingReviewCount, int delayDays,
                                          int remainingDays, int remainingBasis) {
        List<String> suggestions = new ArrayList<>();
        if (blockedCount > 0) {
            suggestions.add("优先处理阻塞项及其前置依赖，必要时调整负责人");
        }
        if (overdueCount > 0) {
            suggestions.add("拆解或重新分配逾期工作项，明确今日可交付范围");
        }
        if (pendingReviewCount > 0) {
            suggestions.add("尽快推进待验收事项，避免完成工作积压在验收环节");
        }
        if (progressGap >= 10 || delayDays > 0) {
            suggestions.add("评估缩减 Sprint 范围，或将非关键需求迁移到下一迭代");
        }
        if (remainingDays <= 2 && remainingBasis > 0) {
            suggestions.add("迭代即将结束，确认延期方案并同步剩余风险");
        }
        if (suggestions.isEmpty() && "HEALTHY".equals(healthLevel)) {
            suggestions.add("保持当前节奏，持续关注新出现的阻塞和范围变化");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("继续每日更新任务状态，确保健康度基于最新数据计算");
        }
        return suggestions;
    }

    private int countBlocked(List<Issue> sprintIssues, List<Issue> projectIssues) {
        Map<Long, Issue> issueById = projectIssues.stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity(), (a, b) -> a));
        int count = 0;
        for (Issue issue : sprintIssues) {
            boolean blocked = readDependencyIds(issue.getDependencies()).stream()
                    .map(issueById::get)
                    .filter(Objects::nonNull)
                    .anyMatch(dependency -> !isCompleted(dependency));
            if (blocked && !isCompleted(issue)) count++;
        }
        return count;
    }

    private int countPendingReviews(List<Issue> sprintIssues) {
        if (sprintIssues.isEmpty()) return 0;
        Set<Long> issueIds = sprintIssues.stream().map(Issue::getId).collect(Collectors.toSet());
        return issueReviewMapper.selectCount(new LambdaQueryWrapper<IssueReview>()
                .in(IssueReview::getIssueId, issueIds)
                .eq(IssueReview::getStatus, "PENDING")).intValue();
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

    private List<Long> readDependencyIds(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private double percent(int part, int total) {
        if (total <= 0) return 0;
        return round1(Math.min(100, Math.max(0, part * 100.0 / total)));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
