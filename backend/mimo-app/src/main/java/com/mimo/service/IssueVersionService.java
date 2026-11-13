package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueDTO;
import com.mimo.dto.IssueGovernanceDTO.FieldDiff;
import com.mimo.dto.IssueGovernanceDTO.IssueVersionVO;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueVersion;
import com.mimo.entity.User;
import com.mimo.mapper.IssueVersionMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueVersionService {

    private static final Set<String> SNAPSHOT_FIELDS = new LinkedHashSet<>(List.of(
            "title", "description", "type", "priority", "status", "columnId", "sprintId",
            "assigneeId", "dueDate", "storyPoints", "severity", "stepsToRepro",
            "planStartDate", "planEndDate", "dependencies", "userRole", "userGoal",
            "businessValue", "acceptanceCriteria", "epic", "parentId", "bugStatus",
            "environment", "expectedResult", "actualResult", "foundVersion", "fixedVersion"
    ));

    private final IssueVersionMapper issueVersionMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public Set<String> snapshotFields() {
        return Collections.unmodifiableSet(SNAPSHOT_FIELDS);
    }

    public int latestVersionNo(Long issueId) {
        IssueVersion latest = latest(issueId);
        return latest == null ? 0 : latest.getVersionNo();
    }

    public IssueVersion latest(Long issueId) {
        return issueVersionMapper.selectOne(new LambdaQueryWrapper<IssueVersion>()
                .eq(IssueVersion::getIssueId, issueId)
                .orderByDesc(IssueVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    /**
     * Existing issues may not have a version row yet. Create v1 lazily from their current state.
     */
    @Transactional
    public IssueVersion ensureInitialSnapshot(Issue issue, Long operatorId) {
        IssueVersion latest = latest(issue.getId());
        if (latest != null) return latest;
        return insertSnapshot(issue, 1, operatorId, "MIGRATION", "初始版本");
    }

    @Transactional
    public IssueVersion recordCurrent(Issue issue, Long operatorId, String source, String summary) {
        IssueVersion latest = latest(issue.getId());
        int nextVersion = latest == null ? 1 : latest.getVersionNo() + 1;
        return insertSnapshot(issue, nextVersion, operatorId, source, summary);
    }

    public List<IssueVersionVO> list(Long issueId, Issue issueForInitial) {
        if (latest(issueId) == null) {
            ensureInitialSnapshot(issueForInitial, issueForInitial.getReporterId());
        }
        List<IssueVersion> rows = issueVersionMapper.selectList(new LambdaQueryWrapper<IssueVersion>()
                .eq(IssueVersion::getIssueId, issueId)
                .orderByDesc(IssueVersion::getVersionNo));
        if (rows.isEmpty()) return List.of();

        Set<Long> userIds = rows.stream().map(IssueVersion::getOperatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return rows.stream().map(row -> toVO(row, names)).collect(Collectors.toList());
    }

    public JsonNode snapshot(Long issueId, int versionNo) {
        IssueVersion version = issueVersionMapper.selectOne(new LambdaQueryWrapper<IssueVersion>()
                .eq(IssueVersion::getIssueId, issueId)
                .eq(IssueVersion::getVersionNo, versionNo));
        if (version == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工作项版本不存在");
        }
        return readTree(version.getSnapshotJson());
    }

    public List<FieldDiff> diff(JsonNode before, JsonNode after) {
        List<FieldDiff> diffs = new ArrayList<>();
        for (String field : SNAPSHOT_FIELDS) {
            JsonNode beforeValue = before == null ? null : before.get(field);
            JsonNode afterValue = after == null ? null : after.get(field);
            if (!Objects.equals(normalize(beforeValue), normalize(afterValue))) {
                FieldDiff diff = new FieldDiff();
                diff.setField(field);
                diff.setBefore(toPlainValue(beforeValue));
                diff.setAfter(toPlainValue(afterValue));
                diffs.add(diff);
            }
        }
        return diffs;
    }

    public List<FieldDiff> diffWithProposed(JsonNode base, Map<String, Object> proposed) {
        JsonNode proposedNode = objectMapper.valueToTree(proposed);
        List<FieldDiff> diffs = new ArrayList<>();
        for (String field : proposed.keySet()) {
            if (!SNAPSHOT_FIELDS.contains(field)) continue;
            JsonNode beforeValue = base == null ? null : base.get(field);
            JsonNode afterValue = proposedNode.get(field);
            if (!Objects.equals(normalize(beforeValue), normalize(afterValue))) {
                FieldDiff diff = new FieldDiff();
                diff.setField(field);
                diff.setBefore(toPlainValue(beforeValue));
                diff.setAfter(toPlainValue(afterValue));
                diffs.add(diff);
            }
        }
        return diffs;
    }

    public Map<String, Object> snapshotMap(Issue issue) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", issue.getTitle());
        value.put("description", issue.getDescription());
        value.put("type", issue.getType());
        value.put("priority", issue.getPriority());
        value.put("status", issue.getStatus());
        value.put("columnId", issue.getColumnId());
        value.put("sprintId", issue.getSprintId());
        value.put("assigneeId", issue.getAssigneeId());
        value.put("dueDate", issue.getDueDate());
        value.put("storyPoints", issue.getStoryPoints());
        value.put("severity", issue.getSeverity());
        value.put("stepsToRepro", issue.getStepsToRepro());
        value.put("planStartDate", issue.getPlanStartDate());
        value.put("planEndDate", issue.getPlanEndDate());
        value.put("dependencies", issue.getDependencies());
        value.put("userRole", issue.getUserRole());
        value.put("userGoal", issue.getUserGoal());
        value.put("businessValue", issue.getBusinessValue());
        value.put("acceptanceCriteria", IssueDTO.parseAcceptanceCriteria(issue.getAcceptanceCriteria()));
        value.put("epic", issue.getEpic());
        value.put("parentId", issue.getParentId());
        value.put("bugStatus", issue.getBugStatus());
        value.put("environment", issue.getEnvironment());
        value.put("expectedResult", issue.getExpectedResult());
        value.put("actualResult", issue.getActualResult());
        value.put("foundVersion", issue.getFoundVersion());
        value.put("fixedVersion", issue.getFixedVersion());
        return value;
    }

    private IssueVersion insertSnapshot(Issue issue, int versionNo, Long operatorId,
                                        String source, String summary) {
        try {
            IssueVersion version = new IssueVersion();
            version.setIssueId(issue.getId());
            version.setVersionNo(versionNo);
            version.setSnapshotJson(objectMapper.writeValueAsString(snapshotMap(issue)));
            version.setSource(source);
            version.setChangeSummary(summary);
            version.setOperatorId(operatorId != null ? operatorId : issue.getReporterId());
            issueVersionMapper.insert(version);
            return version;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "保存工作项版本失败: " + e.getMessage());
        }
    }

    private IssueVersionVO toVO(IssueVersion version, Map<Long, String> names) {
        IssueVersionVO vo = new IssueVersionVO();
        vo.setId(version.getId());
        vo.setIssueId(version.getIssueId());
        vo.setVersionNo(version.getVersionNo());
        vo.setSnapshot(readTree(version.getSnapshotJson()));
        vo.setChangeSummary(version.getChangeSummary());
        vo.setSource(version.getSource());
        vo.setOperatorId(version.getOperatorId());
        vo.setOperatorName(names.getOrDefault(version.getOperatorId(), "未知用户"));
        vo.setCreatedAt(version.getCreatedAt());
        return vo;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private Object toPlainValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return objectMapper.convertValue(node, Object.class);
    }

    private String normalize(JsonNode node) {
        return node == null || node.isNull() ? "null" : node.toString();
    }
}
