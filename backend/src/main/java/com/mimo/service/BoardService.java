package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.BoardDTO.*;
import com.mimo.dto.BoardSyncEvent;
import com.mimo.dto.IssueDTO;
import com.mimo.dto.IssueDTO.IssueVO;
import com.mimo.dto.IssueDTO.IssueLabelVO;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardColumnMapper boardColumnMapper;
    private final IssueMapper issueMapper;
    private final IssueLabelMapper issueLabelMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;

    public BoardVO getBoard(Long projectId) {
        List<BoardColumn> columns = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId)
                        .orderByAsc(BoardColumn::getSortOrder));
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, projectId));

        // 收集所有相关用户
        Set<Long> userIds = new HashSet<>();
        issues.forEach(i -> {
            if (i.getAssigneeId() != null) userIds.add(i.getAssigneeId());
            if (i.getReporterId() != null) userIds.add(i.getReporterId());
        });
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 收集标签
        List<Long> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toList());
        Map<Long, List<IssueLabelVO>> labelMap = new HashMap<>();
        if (!issueIds.isEmpty()) {
            issueLabelMapper.selectList(
                    new LambdaQueryWrapper<IssueLabel>().in(IssueLabel::getIssueId, issueIds))
                    .forEach(l -> labelMap.computeIfAbsent(l.getIssueId(), k -> new ArrayList<>())
                            .add(toLabelVO(l)));
        }

        Map<Long, List<Issue>> issueMap = issues.stream()
                .collect(Collectors.groupingBy(Issue::getColumnId));

        List<ColumnVO> columnVOs = columns.stream().map(col -> {
            ColumnVO cvo = new ColumnVO();
            cvo.setId(col.getId());
            cvo.setName(col.getName());
            cvo.setColor(col.getColor());
            cvo.setSortOrder(col.getSortOrder());
            List<Issue> colIssues = issueMap.getOrDefault(col.getId(), new ArrayList<>());
            colIssues.sort(Comparator.comparing(Issue::getSortOrder));
            cvo.setIssues(colIssues.stream().map(i -> toIssueVO(i, userMap, labelMap)).collect(Collectors.toList()));
            return cvo;
        }).collect(Collectors.toList());

        BoardVO board = new BoardVO();
        board.setProjectId(projectId);
        board.setColumns(columnVOs);
        return board;
    }

    public BoardColumn createColumn(CreateColumnRequest request) {
        BoardColumn col = new BoardColumn();
        col.setProjectId(request.getProjectId());
        col.setName(request.getName());
        col.setColor(request.getColor() != null ? request.getColor() : "#409EFF");
        int maxSort = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, request.getProjectId()))
                .stream().mapToInt(BoardColumn::getSortOrder).max().orElse(-1);
        col.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : maxSort + 1);
        boardColumnMapper.insert(col);
        return col;
    }

    public void updateColumn(UpdateColumnRequest request) {
        BoardColumn col = boardColumnMapper.selectById(request.getId());
        if (col == null) throw new BusinessException(ResultCode.COLUMN_NOT_FOUND);
        if (request.getName() != null) col.setName(request.getName());
        if (request.getColor() != null) col.setColor(request.getColor());
        if (request.getSortOrder() != null) col.setSortOrder(request.getSortOrder());
        boardColumnMapper.updateById(col);
    }

    public void deleteColumn(Long columnId) {
        // 检查列下是否有任务
        if (issueMapper.exists(new LambdaQueryWrapper<Issue>().eq(Issue::getColumnId, columnId))) {
            throw new BusinessException(ResultCode.CONFLICT, "该列下还有任务，无法删除");
        }
        boardColumnMapper.deleteById(columnId);
    }

    @Transactional
    public void sortColumns(List<Long> columnIds) {
        for (int i = 0; i < columnIds.size(); i++) {
            BoardColumn col = boardColumnMapper.selectById(columnIds.get(i));
            if (col != null) {
                col.setSortOrder(i);
                boardColumnMapper.updateById(col);
            }
        }
    }

    @Transactional
    public void moveIssue(Long issueId, Long targetColumnId, Integer sortOrder) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);

        Long oldColumnId = issue.getColumnId();

        issue.setColumnId(targetColumnId);
        issue.setSortOrder(sortOrder);

        // 同步 status 字段（STORY/TASK）和 bugStatus 字段（BUG）
        BoardColumn targetCol = boardColumnMapper.selectById(targetColumnId);
        if (targetCol != null) {
            if ("BUG".equals(issue.getType())) {
                // BUG 类型：根据目标列名映射 bugStatus
                String newBugStatus = columnToBugStatus(targetCol.getName());
                // 验证 BUG 状态转换是否合法
                String oldBugStatus = issue.getBugStatus();
                if (oldBugStatus != null && !oldBugStatus.equals(newBugStatus)) {
                    if (!isValidBugTransition(oldBugStatus, newBugStatus)) {
                        // 如果转换不合法，只更新位置不更新状态
                        issueMapper.updateById(issue);
                        logMoveActivity(issue, oldColumnId, targetCol);
                        broadcastMove(issue.getProjectId(), issueId, targetColumnId, sortOrder);
                        return;
                    }
                }
                issue.setBugStatus(newBugStatus);
                // 同步通用 status 字段以便看板统一展示
                issue.setStatus(bugStatusToGenericStatus(newBugStatus));
            } else {
                // STORY/TASK 类型：映射通用 status
                String newStatus = columnToStatus(targetCol.getName());
                issue.setStatus(newStatus);
            }
        }

        issueMapper.updateById(issue);
        logMoveActivity(issue, oldColumnId, targetCol);
        broadcastMove(issue.getProjectId(), issueId, targetColumnId, sortOrder);
    }

    private void logMoveActivity(Issue issue, Long oldColumnId, BoardColumn targetCol) {
        BoardColumn oldCol = oldColumnId != null ? boardColumnMapper.selectById(oldColumnId) : null;
        String detail = "将 " + issue.getIssueKey() + " 从 " +
                (oldCol != null ? oldCol.getName() : "未知") + " 移动到 " +
                (targetCol != null ? targetCol.getName() : "未知");
        ActivityLog log = new ActivityLog();
        log.setProjectId(issue.getProjectId());
        log.setTargetType("ISSUE");
        log.setTargetId(issue.getId());
        log.setAction("MOVE");
        log.setDetail(detail);
        activityLogMapper.insert(log);
    }

    private void broadcastMove(Long projectId, Long issueId, Long targetColumnId, Integer sortOrder) {
        try {
            webSocketService.sendBoardUpdate(projectId,
                    BoardSyncEvent.moved(projectId, issueId, targetColumnId, sortOrder));
        } catch (Exception ignored) { /* WebSocket optional */ }
    }

    /**
     * 验证 BUG 状态转换是否合法
     */
    private boolean isValidBugTransition(String from, String to) {
        // 允许的 BUG 状态转换规则
        Map<String, Set<String>> transitions = Map.of(
            "NEW",         Set.of("CONFIRMED", "CLOSED"),
            "CONFIRMED",   Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("RESOLVED"),
            "RESOLVED",    Set.of("VERIFIED", "REOPENED"),
            "VERIFIED",    Set.of("CLOSED"),
            "CLOSED",      Set.of("REOPENED"),
            "REOPENED",    Set.of("IN_PROGRESS")
        );
        Set<String> allowed = transitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    private String columnToStatus(String colName) {
        if (colName == null) return "TODO";
        String lower = colName.toLowerCase();
        if (lower.contains("done") || lower.contains("完成")) return "DONE";
        if (lower.contains("progress") || lower.contains("进行")) return "IN_PROGRESS";
        if (lower.contains("todo") || lower.contains("待办")) return "TODO";
        return "TODO";
    }

    /**
     * 列名 → BUG 的 bugStatus 映射
     * 待办列 → NEW, 进行中列 → IN_PROGRESS, 完成列 → CLOSED/VERIFIED
     */
    private String columnToBugStatus(String colName) {
        if (colName == null) return "NEW";
        String lower = colName.toLowerCase();
        if (lower.contains("done") || lower.contains("完成")) return "CLOSED";
        if (lower.contains("progress") || lower.contains("进行")) return "IN_PROGRESS";
        return "NEW"; // 待办列默认 NEW
    }

    /**
     * BUG 的 bugStatus → 通用 status 映射（用于看板统一展示）
     */
    private String bugStatusToGenericStatus(String bugStatus) {
        if (bugStatus == null) return "TODO";
        switch (bugStatus) {
            case "CLOSED":
            case "VERIFIED":
            case "RESOLVED": return "DONE";
            case "IN_PROGRESS":
            case "CONFIRMED": return "IN_PROGRESS";
            default: return "TODO"; // NEW, REOPENED → TODO
        }
    }

    private IssueVO toIssueVO(Issue issue, Map<Long, User> userMap, Map<Long, List<IssueLabelVO>> labelMap) {
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setTitle(issue.getTitle());
        vo.setDescription(issue.getDescription());
        vo.setType(issue.getType());
        vo.setPriority(issue.getPriority());
        vo.setStatus(issue.getStatus());
        vo.setColumnId(issue.getColumnId());
        vo.setSprintId(issue.getSprintId());
        vo.setDueDate(issue.getDueDate());
        vo.setSortOrder(issue.getSortOrder());
        vo.setStoryPoints(issue.getStoryPoints());
        vo.setSeverity(issue.getSeverity());
        vo.setStepsToRepro(issue.getStepsToRepro());
        // STORY fields
        vo.setUserRole(issue.getUserRole());
        vo.setUserGoal(issue.getUserGoal());
        vo.setBusinessValue(issue.getBusinessValue());
        vo.setAcceptanceCriteria(IssueDTO.parseAcceptanceCriteria(issue.getAcceptanceCriteria()));
        vo.setEpic(issue.getEpic());
        vo.setParentId(issue.getParentId());
        // BUG fields
        vo.setBugStatus(issue.getBugStatus());
        vo.setEnvironment(issue.getEnvironment());
        vo.setExpectedResult(issue.getExpectedResult());
        vo.setActualResult(issue.getActualResult());
        vo.setFoundVersion(issue.getFoundVersion());
        vo.setFixedVersion(issue.getFixedVersion());
        if (issue.getAssigneeId() != null) {
            User u = userMap.get(issue.getAssigneeId());
            if (u != null) { vo.setAssigneeName(u.getUsername()); vo.setAssigneeAvatar(u.getAvatar()); }
            vo.setAssigneeId(issue.getAssigneeId());
        }
        if (issue.getReporterId() != null) {
            User u = userMap.get(issue.getReporterId());
            if (u != null) vo.setReporterName(u.getUsername());
            vo.setReporterId(issue.getReporterId());
        }
        vo.setLabels(labelMap.getOrDefault(issue.getId(), List.of()));
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }

    private IssueLabelVO toLabelVO(IssueLabel label) {
        IssueLabelVO vo = new IssueLabelVO();
        vo.setId(label.getId());
        vo.setLabel(label.getLabel());
        vo.setColor(label.getColor());
        return vo;
    }
}
