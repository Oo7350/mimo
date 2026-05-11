package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueLabelMapper issueLabelMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;

    @Transactional
    public IssueVO create(CreateRequest request, Long reporterId) {
        Project project = projectMapper.selectById(request.getProjectId());
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);

        // 生成 issue key
        long count = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, request.getProjectId()));
        String key = project.getKey() + "-" + (count + 1);

        Issue issue = new Issue();
        issue.setProjectId(request.getProjectId());
        issue.setColumnId(request.getColumnId());
        issue.setSprintId(request.getSprintId());
        issue.setIssueKey(key);
        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());
        issue.setType(request.getType());
        issue.setPriority(request.getPriority());
        issue.setStatus("TODO");
        issue.setAssigneeId(request.getAssigneeId());
        issue.setReporterId(reporterId);
        issue.setDueDate(request.getDueDate());
        issue.setStoryPoints(request.getStoryPoints());
        issue.setSeverity(request.getSeverity());
        issue.setStepsToRepro(request.getStepsToRepro());
        // 排到列末尾
        int maxSort = 0;
        if (request.getColumnId() != null) {
            maxSort = issueMapper.selectList(
                    new LambdaQueryWrapper<Issue>().eq(Issue::getColumnId, request.getColumnId()))
                    .stream().mapToInt(Issue::getSortOrder).max().orElse(0);
        }
        issue.setSortOrder(maxSort + 1);
        issueMapper.insert(issue);

        // 记录操作日志
        User reporter = userMapper.selectById(reporterId);
        ActivityLog log = new ActivityLog();
        log.setUserId(reporterId);
        log.setUsername(reporter != null ? reporter.getUsername() : "");
        log.setProjectId(issue.getProjectId());
        log.setTargetType("ISSUE");
        log.setTargetId(issue.getId());
        log.setAction("CREATE");
        log.setDetail("创建了任务 " + key + ": " + issue.getTitle());
        activityLogMapper.insert(log);

        // 保存标签
        if (request.getLabels() != null) {
            for (String label : request.getLabels()) {
                IssueLabel il = new IssueLabel();
                il.setIssueId(issue.getId());
                il.setLabel(label);
                il.setColor("#409EFF");
                issueLabelMapper.insert(il);
            }
        }
        return getById(issue.getId());
    }

    public IssueVO getById(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        return toVO(issue);
    }

    public void update(UpdateRequest request, Long operatorId) {
        Issue issue = issueMapper.selectById(request.getId());
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        if (request.getTitle() != null) issue.setTitle(request.getTitle());
        if (request.getDescription() != null) issue.setDescription(request.getDescription());
        if (request.getType() != null) issue.setType(request.getType());
        if (request.getPriority() != null) issue.setPriority(request.getPriority());
        if (request.getAssigneeId() != null) issue.setAssigneeId(request.getAssigneeId());
        if (request.getSprintId() != null) issue.setSprintId(request.getSprintId());
        if (request.getDueDate() != null) issue.setDueDate(request.getDueDate());
        if (request.getStoryPoints() != null) issue.setStoryPoints(request.getStoryPoints());
        if (request.getSeverity() != null) issue.setSeverity(request.getSeverity());
        if (request.getStepsToRepro() != null) issue.setStepsToRepro(request.getStepsToRepro());
        if (request.getStatus() != null) issue.setStatus(request.getStatus());
        issueMapper.updateById(issue);

        // 记录操作日志
        if (operatorId != null) {
            User operator = userMapper.selectById(operatorId);
            ActivityLog log = new ActivityLog();
            log.setUserId(operatorId);
            log.setUsername(operator != null ? operator.getUsername() : "");
            log.setProjectId(issue.getProjectId());
            log.setTargetType("ISSUE");
            log.setTargetId(issue.getId());
            log.setAction("UPDATE");
            log.setDetail("更新了任务 " + issue.getIssueKey());
            activityLogMapper.insert(log);
        }
    }

    @Transactional
    public void delete(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue != null) {
            // 记录操作日志
            ActivityLog log = new ActivityLog();
            log.setProjectId(issue.getProjectId());
            log.setTargetType("ISSUE");
            log.setTargetId(issue.getId());
            log.setAction("DELETE");
            log.setDetail("删除了任务 " + issue.getIssueKey());
            activityLogMapper.insert(log);
        }
        issueMapper.deleteById(issueId);
        issueLabelMapper.delete(new LambdaQueryWrapper<IssueLabel>().eq(IssueLabel::getIssueId, issueId));
    }

    public List<IssueVO> query(QueryRequest request) {
        LambdaQueryWrapper<Issue> qw = new LambdaQueryWrapper<>();
        if (request.getProjectId() != null) qw.eq(Issue::getProjectId, request.getProjectId());
        if (request.getSprintId() != null) qw.eq(Issue::getSprintId, request.getSprintId());
        if (request.getAssigneeId() != null) qw.eq(Issue::getAssigneeId, request.getAssigneeId());
        if (request.getType() != null) qw.eq(Issue::getType, request.getType());
        if (request.getPriority() != null) qw.eq(Issue::getPriority, request.getPriority());
        if (request.getStatus() != null) qw.eq(Issue::getStatus, request.getStatus());
        if (request.getKeyword() != null) qw.and(w -> w.like(Issue::getTitle, request.getKeyword())
                .or().like(Issue::getIssueKey, request.getKeyword()));
        qw.orderByDesc(Issue::getCreatedAt);

        Page<Issue> page = new Page<>(request.getPage(), request.getSize());
        Page<Issue> result = issueMapper.selectPage(page, qw);
        return result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
    }

    private IssueVO toVO(Issue issue) {
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

        if (issue.getColumnId() != null) {
            BoardColumn col = boardColumnMapper.selectById(issue.getColumnId());
            vo.setColumnName(col != null ? col.getName() : "");
        }
        if (issue.getAssigneeId() != null) {
            User u = userMapper.selectById(issue.getAssigneeId());
            vo.setAssigneeName(u != null ? u.getUsername() : "");
            vo.setAssigneeAvatar(u != null ? u.getAvatar() : null);
            vo.setAssigneeId(issue.getAssigneeId());
        }
        if (issue.getReporterId() != null) {
            User u = userMapper.selectById(issue.getReporterId());
            vo.setReporterName(u != null ? u.getUsername() : "");
            vo.setReporterId(issue.getReporterId());
        }
        // 标签
        List<IssueLabel> labels = issueLabelMapper.selectList(
                new LambdaQueryWrapper<IssueLabel>().eq(IssueLabel::getIssueId, issue.getId()));
        vo.setLabels(labels.stream().map(l -> {
            IssueLabelVO lvo = new IssueLabelVO();
            lvo.setId(l.getId());
            lvo.setLabel(l.getLabel());
            lvo.setColor(l.getColor());
            return lvo;
        }).collect(Collectors.toList()));
        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }
}
