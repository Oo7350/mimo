package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.ai.AiService;
import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.DeliveryAnalyticsDTO.VelocityVO;
import com.mimo.dto.SprintPlanningDTO.ApplyPlanVO;
import com.mimo.dto.SprintPlanningDTO.CandidateIssueVO;
import com.mimo.dto.SprintPlanningDTO.RecommendedTaskVO;
import com.mimo.dto.SprintPlanningDTO.SprintPlanVO;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintPlanningService {

    private static final Map<String, Integer> PRIORITY_RANK = Map.of(
            "HIGHEST", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3, "LOWEST", 4
    );

    private final ProjectMapper projectMapper;
    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final TeamService teamService;
    private final DeliveryAnalyticsService deliveryAnalyticsService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public SprintPlanVO suggestPlan(Long projectId, Long sprintId, Integer requestedCapacity,
                                    Long userId) {
        Project project = requireProject(projectId);
        assertMember(project, userId);
        Sprint sprint = null;
        if (sprintId != null) {
            sprint = requireSprint(sprintId);
            if (!Objects.equals(projectId, sprint.getProjectId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Sprint 不属于该项目");
            }
        }

        List<Issue> allIssues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId));
        Map<Long, Issue> issueById = allIssues.stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity(), (a, b) -> a));
        List<CandidateIssueVO> candidates = allIssues.stream()
                .filter(issue -> issue.getSprintId() == null)
                .filter(issue -> !isCompleted(issue))
                .map(issue -> toCandidate(issue, issueById))
                .sorted(Comparator
                        .comparing(CandidateIssueVO::getBlocked)
                        .thenComparingInt(item -> PRIORITY_RANK.getOrDefault(item.getPriority(), 9))
                        .thenComparing(CandidateIssueVO::getDueDate,
                                Comparator.nullsLast(LocalDate::compareTo)))
                .limit(100)
                .toList();

        VelocityVO velocity = deliveryAnalyticsService.getVelocity(projectId, 6);
        double historical = velocity.getAverageCompletedPoints() == null
                ? 0 : velocity.getAverageCompletedPoints();
        int capacity = requestedCapacity != null && requestedCapacity > 0
                ? requestedCapacity
                : historical > 0 ? (int) Math.round(historical) : 20;
        int availablePoints = candidates.stream()
                .map(CandidateIssueVO::getStoryPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        List<RecommendedTaskVO> recommended;
        boolean aiAvailable;
        String summary;
        List<String> risks = new ArrayList<>();
        if (candidates.isEmpty()) {
            recommended = List.of();
            aiAvailable = false;
            summary = "当前 Backlog 没有可加入 Sprint 的工作项。";
            risks.add("请先创建或拆分 Backlog 工作项");
        } else {
            AiSelection selection = requestAiSelection(candidates, capacity, historical, userId);
            if (selection == null) {
                selection = fallbackSelection(candidates, capacity);
                aiAvailable = false;
                summary = "AI 暂不可用，已根据优先级、截止日期、依赖就绪度和容量生成规则建议。";
                risks.add("当前结果由确定性规则生成，请人工确认后再加入 Sprint");
            } else {
                aiAvailable = true;
                summary = selection.summary;
                risks.addAll(selection.risks);
            }
            recommended = toRecommended(selection.issueIds, selection.reasons, candidates);
            if (recommended.isEmpty()) {
                selection = fallbackSelection(candidates, capacity);
                aiAvailable = false;
                summary = "AI 未返回有效候选，已回退为规则建议。";
                risks.add("当前结果由确定性规则生成，请人工确认后再加入 Sprint");
                recommended = toRecommended(selection.issueIds, selection.reasons, candidates);
            }
            int recommendedPoints = recommended.stream()
                    .map(RecommendedTaskVO::getStoryPoints)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).sum();
            if (recommendedPoints > capacity) {
                risks.add("推荐故事点 " + recommendedPoints + " 超过容量 " + capacity
                        + "，请手动减少任务");
            }
            long blockedSelected = recommended.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getBlocked())).count();
            if (blockedSelected > 0) {
                risks.add(blockedSelected + " 个推荐工作项仍有未完成前置依赖");
            }
        }

        SprintPlanVO vo = new SprintPlanVO();
        vo.setProjectId(projectId);
        vo.setSprintId(sprintId);
        vo.setSprintName(sprint == null ? null : sprint.getName());
        vo.setCapacityPoints(capacity);
        vo.setHistoricalVelocity(historical);
        vo.setAvailablePoints(availablePoints);
        vo.setTotalRecommendedPoints(recommended.stream()
                .map(RecommendedTaskVO::getStoryPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum());
        vo.setAiAvailable(aiAvailable);
        vo.setSummary(summary);
        vo.setRisks(risks);
        vo.setCandidates(candidates);
        vo.setRecommendedTasks(recommended);
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    @Transactional
    public ApplyPlanVO applyPlan(Long sprintId, List<Long> issueIds, Long userId) {
        Sprint sprint = requireSprint(sprintId);
        Project project = requireProject(sprint.getProjectId());
        assertMember(project, userId);
        if (!"PLANNING".equals(sprint.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "只有规划中的 Sprint 可以使用规划结果");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(issueIds));
        if (distinctIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请至少选择一个工作项");
        }

        List<Issue> issues = issueMapper.selectBatchIds(distinctIds);
        if (issues.size() != distinctIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "部分工作项不存在");
        }
        int points = 0;
        for (Issue issue : issues) {
            if (!Objects.equals(issue.getProjectId(), sprint.getProjectId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "工作项 " + issue.getIssueKey() + " 不属于该 Sprint 所在项目");
            }
            if (isCompleted(issue)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "已完成工作项 " + issue.getIssueKey() + " 不能加入规划");
            }
            if (issue.getSprintId() != null && !Objects.equals(issue.getSprintId(), sprintId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "工作项 " + issue.getIssueKey() + " 已在其他 Sprint 中");
            }
            issue.setSprintId(sprintId);
            issueMapper.updateById(issue);
            if (issue.getStoryPoints() != null) points += issue.getStoryPoints();
        }
        deliveryAnalyticsService.refreshTodaySnapshot(sprint.getProjectId());

        ApplyPlanVO vo = new ApplyPlanVO();
        vo.setSprintId(sprintId);
        vo.setAppliedCount(issues.size());
        vo.setTotalStoryPoints(points);
        vo.setAppliedIssueIds(distinctIds);
        return vo;
    }

    private AiSelection requestAiSelection(List<CandidateIssueVO> candidates, int capacity,
                                           double historical, Long userId) {
        try {
            String candidatesJson = objectMapper.writeValueAsString(candidates);
            String prompt = "历史平均 Velocity=" + historical
                    + "，本次容量故事点=" + capacity
                    + "。Backlog 候选如下：\n" + candidatesJson
                    + "\n请选择适合本 Sprint 的工作项。优先高优先级、近期截止、依赖已就绪的工作项；"
                    + "若选择有依赖的任务，应同时选择其未完成前置项。不得凭空创建 ID。"
                    + "只返回 JSON："
                    + "{\"summary\":\"简短说明\",\"recommendedIssueIds\":[1,2],"
                    + "\"taskReasons\":{\"1\":\"选择理由\"},\"risks\":[\"风险\"]}";
            AiChatResponse response = aiService.chatWithQuota(userId, AiChatRequest.builder()
                    .systemMessage("你是资深 Scrum Master。输出仅作为人工确认建议，"
                            + "不得自动写入业务数据。严格返回合法 JSON。")
                    .userMessage(prompt)
                    .temperature(0.2)
                    .maxTokens(2000)
                    .build());
            String content = response == null ? null : response.getContent();
            if (response == null || response.hasError() || content == null || content.isBlank()) {
                return null;
            }
            content = stripCodeFence(content);
            JsonNode root = objectMapper.readTree(content);
            Map<Long, String> reasons = new LinkedHashMap<>();
            JsonNode reasonNode = root.path("taskReasons");
            if (reasonNode.isObject()) {
                reasonNode.fields().forEachRemaining(entry -> {
                    try {
                        reasons.put(Long.valueOf(entry.getKey()), entry.getValue().asText());
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
            List<Long> ids = new ArrayList<>();
            JsonNode idsNode = root.path("recommendedIssueIds");
            if (idsNode.isArray()) {
                idsNode.forEach(node -> {
                    if (node.canConvertToLong()) ids.add(node.asLong());
                });
            }
            List<Long> distinctIds = ids.stream().distinct().toList();
            if (distinctIds.isEmpty()) return null;
            List<String> risks = new ArrayList<>();
            JsonNode riskNode = root.path("risks");
            if (riskNode.isArray()) {
                riskNode.forEach(node -> risks.add(node.asText()));
            }
            String summary = root.path("summary").asText("AI 已生成 Sprint 候选任务");
            return new AiSelection(distinctIds, reasons, summary, risks);
        } catch (Exception e) {
            log.warn("AI Sprint 规划不可用，使用规则回退: {}", e.getMessage());
            return null;
        }
    }

    private AiSelection fallbackSelection(List<CandidateIssueVO> candidates, int capacity) {
        List<Long> selected = new ArrayList<>();
        Map<Long, String> reasons = new LinkedHashMap<>();
        int used = 0;
        for (CandidateIssueVO candidate : candidates) {
            if (Boolean.TRUE.equals(candidate.getBlocked())) continue;
            int points = candidate.getStoryPoints() == null ? 1 : candidate.getStoryPoints();
            if (used + points <= capacity) {
                selected.add(candidate.getId());
                reasons.put(candidate.getId(), fallbackReason(candidate));
                used += points;
            }
            if (used >= capacity) break;
        }
        if (selected.isEmpty()) {
            for (CandidateIssueVO candidate : candidates) {
                int points = candidate.getStoryPoints() == null ? 1 : candidate.getStoryPoints();
                if (used + points <= capacity) {
                    selected.add(candidate.getId());
                    reasons.put(candidate.getId(),
                            "当前无依赖就绪项；" + fallbackReason(candidate)
                                    + "，需同时处理前置依赖");
                    used += points;
                }
                if (used >= capacity) break;
            }
        }
        return new AiSelection(selected, reasons,
                "已按优先级、截止日期和依赖就绪度生成容量内的规则建议。", List.of());
    }

    private List<RecommendedTaskVO> toRecommended(List<Long> ids, Map<Long, String> reasons,
                                                  List<CandidateIssueVO> candidates) {
        Map<Long, CandidateIssueVO> candidateById = candidates.stream()
                .collect(Collectors.toMap(CandidateIssueVO::getId, Function.identity()));
        List<RecommendedTaskVO> result = new ArrayList<>();
        for (Long id : ids) {
            CandidateIssueVO candidate = candidateById.get(id);
            if (candidate == null) continue;
            RecommendedTaskVO task = new RecommendedTaskVO();
            task.setId(candidate.getId());
            task.setIssueKey(candidate.getIssueKey());
            task.setTitle(candidate.getTitle());
            task.setType(candidate.getType());
            task.setPriority(candidate.getPriority());
            task.setStoryPoints(candidate.getStoryPoints());
            task.setDueDate(candidate.getDueDate());
            task.setBlocked(candidate.getBlocked());
            task.setReason(reasons.getOrDefault(id, fallbackReason(candidate)));
            result.add(task);
        }
        return result;
    }

    private CandidateIssueVO toCandidate(Issue issue, Map<Long, Issue> issueById) {
        List<Long> dependencyIds = readDependencyIds(issue.getDependencies());
        List<String> dependencyKeys = dependencyIds.stream()
                .map(issueById::get)
                .filter(Objects::nonNull)
                .map(Issue::getIssueKey)
                .toList();
        boolean blocked = dependencyIds.stream()
                .map(issueById::get)
                .filter(Objects::nonNull)
                .anyMatch(dependency -> !isCompleted(dependency));

        CandidateIssueVO vo = new CandidateIssueVO();
        vo.setId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setTitle(issue.getTitle());
        vo.setType(issue.getType());
        vo.setPriority(issue.getPriority());
        vo.setStatus(issue.getStatus());
        vo.setStoryPoints(issue.getStoryPoints());
        vo.setDueDate(issue.getDueDate());
        vo.setBlocked(blocked);
        vo.setDependencyIssueKeys(dependencyKeys);
        return vo;
    }

    private String fallbackReason(CandidateIssueVO candidate) {
        List<String> reasons = new ArrayList<>();
        reasons.add(priorityReason(candidate.getPriority()));
        if (candidate.getDueDate() != null) {
            reasons.add("截止 " + candidate.getDueDate());
        }
        reasons.add("依赖已就绪");
        return String.join("，", reasons);
    }

    private String priorityReason(String priority) {
        return switch (priority == null ? "" : priority) {
            case "HIGHEST" -> "最高优先级";
            case "HIGH" -> "高优先级";
            case "MEDIUM" -> "中优先级";
            case "LOW" -> "低优先级";
            default -> "最低优先级";
        };
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        return project;
    }

    private Sprint requireSprint(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new BusinessException(ResultCode.SPRINT_NOT_FOUND);
        return sprint;
    }

    private void assertMember(Project project, Long userId) {
        if (!teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权规划该项目的 Sprint");
        }
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

    private static class AiSelection {
        private final List<Long> issueIds;
        private final Map<Long, String> reasons;
        private final String summary;
        private final List<String> risks;

        private AiSelection(List<Long> issueIds, Map<Long, String> reasons,
                            String summary, List<String> risks) {
            this.issueIds = issueIds;
            this.reasons = reasons;
            this.summary = summary;
            this.risks = risks;
        }
    }
}
