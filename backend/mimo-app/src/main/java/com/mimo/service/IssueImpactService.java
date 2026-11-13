package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.dto.IssueGovernanceDTO.AffectedIssueVO;
import com.mimo.dto.IssueGovernanceDTO.AffectedSprintVO;
import com.mimo.dto.IssueGovernanceDTO.ImpactVO;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueChangeRequest;
import com.mimo.entity.Sprint;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueImpactService {

    private static final Set<String> DELIVERY_FIELDS = Set.of(
            "title", "description", "priority", "dueDate", "storyPoints", "sprintId",
            "planStartDate", "planEndDate", "dependencies", "acceptanceCriteria",
            "userRole", "userGoal", "businessValue", "severity"
    );
    private static final Set<String> SCOPE_FIELDS = Set.of(
            "priority", "dueDate", "storyPoints", "sprintId", "planStartDate",
            "planEndDate", "dependencies", "acceptanceCriteria", "severity"
    );

    private final IssueMapper issueMapper;
    private final SprintMapper sprintMapper;
    private final ObjectMapper objectMapper;

    public ImpactVO analyze(IssueChangeRequest request, Issue issue) {
        Map<String, Object> proposed = readMap(request.getProposedChanges());
        List<Issue> projectIssues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, issue.getProjectId()));
        Map<Long, Issue> issueById = projectIssues.stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity(), (left, right) -> left));

        Map<Long, String> relationById = collectAffectedIssues(issue, projectIssues, proposed);
        List<Issue> affectedIssues = relationById.keySet().stream()
                .map(issueById::get)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Sprint> sprints = loadRelevantSprints(issue, proposed, affectedIssues);
        Map<Long, String> sprintNames = sprints.values().stream()
                .collect(Collectors.toMap(Sprint::getId, Sprint::getName, (left, right) -> left));

        Long currentSprintId = issue.getSprintId();
        Long proposedSprintId = proposed.containsKey("sprintId")
                ? toLong(proposed.get("sprintId")) : currentSprintId;
        boolean sprintScopeChanged = !Objects.equals(currentSprintId, proposedSprintId);
        boolean activeSprintAffected = sprints.values().stream().anyMatch(this::isActive);

        int currentStoryPoints = safeInt(issue.getStoryPoints());
        int proposedStoryPoints = proposed.containsKey("storyPoints")
                ? safeInt(toInteger(proposed.get("storyPoints"))) : currentStoryPoints;
        int storyPointDelta = proposedStoryPoints - currentStoryPoints;
        LocalDate currentDueDate = issue.getDueDate();
        LocalDate proposedDueDate = proposed.containsKey("dueDate")
                ? toDate(proposed.get("dueDate")) : currentDueDate;
        Long dueDateDeltaDays = dueDateDelta(currentDueDate, proposedDueDate);

        List<AffectedIssueVO> affectedVOs = affectedIssues.stream()
                .map(item -> toAffectedIssue(item, relationById.get(item.getId()), sprintNames))
                .toList();
        int completedTaskCount = (int) affectedIssues.stream()
                .filter(this::isCompleted)
                .count();
        int affectedTaskCount = (int) affectedIssues.stream()
                .filter(item -> "TASK".equals(item.getType()))
                .count();
        List<AffectedSprintVO> affectedSprintVOs = sprints.values().stream()
                .map(this::toAffectedSprint)
                .toList();
        List<String> riskReasons = buildRiskReasons(proposed, affectedIssues, relationById,
                completedTaskCount, currentSprintId, proposedSprintId, sprintNames,
                activeSprintAffected, currentStoryPoints, proposedStoryPoints,
                dueDateDeltaDays);

        ImpactVO vo = new ImpactVO();
        vo.setRequestId(request.getId());
        vo.setIssueId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setIssueTitle(issue.getTitle());
        vo.setAffectedIssueCount(affectedVOs.size());
        vo.setAffectedTaskCount(affectedTaskCount);
        vo.setCompletedTaskCount(completedTaskCount);
        vo.setAffectedIssues(affectedVOs);
        vo.setAffectedSprintId(currentSprintId != null ? currentSprintId : proposedSprintId);
        vo.setAffectedSprintName(sprintNames.get(vo.getAffectedSprintId()));
        vo.setAffectedSprints(affectedSprintVOs);
        vo.setCurrentSprintId(currentSprintId);
        vo.setCurrentSprintName(sprintNames.get(currentSprintId));
        vo.setProposedSprintId(proposedSprintId);
        vo.setProposedSprintName(sprintNames.get(proposedSprintId));
        vo.setSprintScopeChanged(sprintScopeChanged);
        vo.setActiveSprintAffected(activeSprintAffected);
        vo.setCurrentStoryPoints(currentStoryPoints);
        vo.setProposedStoryPoints(proposedStoryPoints);
        vo.setStoryPointDelta(storyPointDelta);
        vo.setWorkloadDelta(storyPointDelta);
        vo.setCurrentDueDate(currentDueDate);
        vo.setProposedDueDate(proposedDueDate);
        vo.setDueDateDeltaDays(dueDateDeltaDays);
        vo.setRiskReasons(riskReasons);
        vo.setRiskLevel(resolveRiskLevel(proposed, affectedIssues.size(), completedTaskCount,
                sprintScopeChanged, activeSprintAffected, currentStoryPoints,
                proposedStoryPoints, dueDateDeltaDays));
        return vo;
    }

    private Map<Long, String> collectAffectedIssues(Issue root, List<Issue> projectIssues,
                                                     Map<String, Object> proposed) {
        Map<Long, List<Issue>> children = new HashMap<>();
        Map<Long, List<Issue>> dependents = new HashMap<>();
        for (Issue item : projectIssues) {
            if (item.getParentId() != null) {
                children.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
            for (Long dependencyId : readDependencyIds(item.getDependencies())) {
                dependents.computeIfAbsent(dependencyId, key -> new ArrayList<>()).add(item);
            }
        }

        Map<Long, String> relations = new LinkedHashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(root.getId());
        Set<Long> visited = new HashSet<>();
        visited.add(root.getId());
        while (!queue.isEmpty()) {
            Long currentId = queue.removeFirst();
            for (Issue child : children.getOrDefault(currentId, Collections.emptyList())) {
                addRelation(relations, visited, queue, child.getId(), "SUBTASK");
            }
            for (Issue dependent : dependents.getOrDefault(currentId, Collections.emptyList())) {
                addRelation(relations, visited, queue, dependent.getId(), "DEPENDENT");
            }
        }

        if (proposed.containsKey("dependencies")) {
            for (Long dependencyId : readDependencyIds(proposed.get("dependencies"))) {
                if (!Objects.equals(dependencyId, root.getId()) && projectIssues.stream()
                        .anyMatch(item -> Objects.equals(item.getId(), dependencyId))) {
                    relations.putIfAbsent(dependencyId, "NEW_DEPENDENCY");
                }
            }
        }
        return relations;
    }

    private void addRelation(Map<Long, String> relations, Set<Long> visited,
                             Deque<Long> queue, Long issueId, String relation) {
        if (issueId == null || !visited.add(issueId)) return;
        relations.put(issueId, relation);
        queue.addLast(issueId);
    }

    private Map<Long, Sprint> loadRelevantSprints(Issue issue, Map<String, Object> proposed,
                                                   List<Issue> affectedIssues) {
        Set<Long> sprintIds = new LinkedHashSet<>();
        addIfPresent(sprintIds, issue.getSprintId());
        if (proposed.containsKey("sprintId")) {
            addIfPresent(sprintIds, toLong(proposed.get("sprintId")));
        }
        affectedIssues.forEach(item -> addIfPresent(sprintIds, item.getSprintId()));
        if (sprintIds.isEmpty()) return Collections.emptyMap();
        return sprintMapper.selectBatchIds(sprintIds).stream()
                .collect(Collectors.toMap(Sprint::getId, Function.identity(), (left, right) -> left));
    }

    private List<String> buildRiskReasons(Map<String, Object> proposed,
                                          List<Issue> affectedIssues,
                                          Map<Long, String> relationById,
                                          int completedTaskCount,
                                          Long currentSprintId, Long proposedSprintId,
                                          Map<Long, String> sprintNames,
                                          boolean activeSprintAffected,
                                          int currentStoryPoints, int proposedStoryPoints,
                                          Long dueDateDeltaDays) {
        List<String> reasons = new ArrayList<>();
        if (activeSprintAffected) {
            reasons.add("变更涉及进行中的 Sprint，会影响当前迭代范围或交付节奏");
        }
        if (!Objects.equals(currentSprintId, proposedSprintId)) {
            reasons.add("Sprint 范围变化：" + sprintLabel(currentSprintId, sprintNames)
                    + " → " + sprintLabel(proposedSprintId, sprintNames));
        }
        if (proposedStoryPoints > currentStoryPoints) {
            int delta = proposedStoryPoints - currentStoryPoints;
            String ratio = currentStoryPoints > 0
                    ? String.format("，增加约 %.0f%%", delta * 100.0 / currentStoryPoints)
                    : "";
            reasons.add("故事点由 " + currentStoryPoints + " 增至 "
                    + proposedStoryPoints + ratio);
        } else if (proposedStoryPoints < currentStoryPoints) {
            reasons.add("故事点减少 " + (currentStoryPoints - proposedStoryPoints)
                    + "，需确认验收范围同步缩小");
        }
        if (dueDateDeltaDays != null && dueDateDeltaDays < 0) {
            reasons.add("截止日期提前 " + Math.abs(dueDateDeltaDays) + " 天");
        } else if (dueDateDeltaDays != null && dueDateDeltaDays > 0) {
            reasons.add("截止日期延后 " + dueDateDeltaDays + " 天");
        }
        if (completedTaskCount > 0) {
            reasons.add("影响 " + completedTaskCount + " 个已完成工作项，存在返工风险");
        }
        if (proposed.containsKey("acceptanceCriteria")) {
            reasons.add("验收标准发生变化，交付范围和测试口径需要重新确认");
        }
        if (proposed.containsKey("dependencies")) {
            reasons.add("前置依赖发生变化，相关任务的排期和可执行性可能受影响");
        }
        long dependentCount = affectedIssues.stream()
                .filter(item -> "DEPENDENT".equals(relationById.get(item.getId())))
                .count();
        if (affectedIssues.size() > 0) {
            reasons.add("共关联 " + affectedIssues.size() + " 个受影响工作项"
                    + (dependentCount > 0 ? "，其中包含下游依赖项" : ""));
        }
        if (proposed.keySet().stream().noneMatch(DELIVERY_FIELDS::contains)) {
            reasons.add("未修改交付范围字段，风险较低");
        } else if (reasons.isEmpty()) {
            reasons.add("仅涉及低影响的交付字段调整");
        }
        return reasons;
    }

    private String resolveRiskLevel(Map<String, Object> proposed, int affectedCount,
                                    int completedTaskCount, boolean sprintScopeChanged,
                                    boolean activeSprintAffected, int currentStoryPoints,
                                    int proposedStoryPoints, Long dueDateDeltaDays) {
        boolean deliveryChanged = proposed.keySet().stream().anyMatch(DELIVERY_FIELDS::contains);
        boolean workloadSurge = activeSprintAffected && proposedStoryPoints > currentStoryPoints
                && (currentStoryPoints == 0
                || (proposedStoryPoints - currentStoryPoints) * 100.0 / currentStoryPoints > 20);
        if (workloadSurge || (deliveryChanged && completedTaskCount > 0)) {
            return "HIGH";
        }
        boolean dueDateAdvanced = dueDateDeltaDays != null && dueDateDeltaDays < 0;
        if (sprintScopeChanged || workloadSurge || proposedStoryPoints > currentStoryPoints
                || dueDateAdvanced || affectedCount > 0
                || proposed.keySet().stream().anyMatch(SCOPE_FIELDS::contains)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private AffectedIssueVO toAffectedIssue(Issue issue, String relation,
                                            Map<Long, String> sprintNames) {
        AffectedIssueVO vo = new AffectedIssueVO();
        vo.setId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setTitle(issue.getTitle());
        vo.setType(issue.getType());
        vo.setStatus(isCompleted(issue) ? "DONE" : issue.getStatus());
        vo.setSprintId(issue.getSprintId());
        vo.setSprintName(sprintNames.get(issue.getSprintId()));
        vo.setCompleted(isCompleted(issue));
        vo.setRelation(relation);
        return vo;
    }

    private AffectedSprintVO toAffectedSprint(Sprint sprint) {
        AffectedSprintVO vo = new AffectedSprintVO();
        vo.setId(sprint.getId());
        vo.setName(sprint.getName());
        vo.setStatus(sprint.getStatus());
        vo.setActive(isActive(sprint));
        return vo;
    }

    private boolean isActive(Sprint sprint) {
        return sprint != null && (Integer.valueOf(1).equals(sprint.getIsActive())
                || "ACTIVE".equals(sprint.getStatus()));
    }

    private boolean isCompleted(Issue issue) {
        return "DONE".equals(issue.getStatus())
                || "VERIFIED".equals(issue.getBugStatus())
                || "CLOSED".equals(issue.getBugStatus());
    }

    private Long dueDateDelta(LocalDate currentDate, LocalDate proposedDate) {
        if (currentDate == null || proposedDate == null) return null;
        return ChronoUnit.DAYS.between(currentDate, proposedDate);
    }

    private String sprintLabel(Long sprintId, Map<Long, String> sprintNames) {
        if (sprintId == null) return "未分配";
        return sprintNames.getOrDefault(sprintId, "#" + sprintId);
    }

    private void addIfPresent(Collection<Long> values, Long value) {
        if (value != null) values.add(value);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer toInteger(Object value) {
        if (value == null || "".equals(value)) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null || "".equals(value)) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDate toDate(Object value) {
        if (value == null || "".equals(value)) return null;
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Long> readDependencyIds(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<Long> readDependencyIds(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof Collection<?> values) {
            return values.stream().map(this::toLong).filter(Objects::nonNull).toList();
        }
        return readDependencyIds(String.valueOf(value));
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
