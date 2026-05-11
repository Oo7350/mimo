package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.BoardDTO.*;
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

    public BoardVO getBoard(Long projectId) {
        List<BoardColumn> columns = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId)
                        .orderByAsc(BoardColumn::getSortOrder));
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, projectId).ne(Issue::getStatus, "DONE"));

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
        issue.setColumnId(targetColumnId);
        issue.setSortOrder(sortOrder);
        issueMapper.updateById(issue);
    }

    private IssueVO toIssueVO(Issue issue, Map<Long, User> userMap, Map<Long, List<IssueLabelVO>> labelMap) {
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setIssueKey(issue.getIssueKey());
        vo.setTitle(issue.getTitle());
        vo.setType(issue.getType());
        vo.setPriority(issue.getPriority());
        vo.setStatus(issue.getStatus());
        vo.setColumnId(issue.getColumnId());
        vo.setSprintId(issue.getSprintId());
        vo.setDueDate(issue.getDueDate());
        vo.setSortOrder(issue.getSortOrder());
        vo.setStoryPoints(issue.getStoryPoints());
        vo.setSeverity(issue.getSeverity());
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
