package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueDTO;
import com.mimo.dto.IssueDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import com.mimo.dto.BoardSyncEvent;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueLabelMapper issueLabelMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ActivityLogMapper activityLogMapper;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;

    // ---- BUG status transition rules ----
    private static final Map<String, Set<String>> BUG_TRANSITIONS = Map.of(
        "NEW",         Set.of("CONFIRMED", "CLOSED"),
        "CONFIRMED",   Set.of("IN_PROGRESS"),
        "IN_PROGRESS", Set.of("RESOLVED"),
        "RESOLVED",    Set.of("VERIFIED", "REOPENED"),
        "VERIFIED",    Set.of("CLOSED"),
        "CLOSED",      Set.of("REOPENED"),
        "REOPENED",    Set.of("IN_PROGRESS")
    );

    @Transactional
    public IssueVO create(CreateRequest request, Long reporterId) {
        Project project = projectMapper.selectById(request.getProjectId());
        if (project == null) throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);

        // Generate issue key — 查询当前项目最大编号，避免并发冲突和软删除计数错误
        String keyPrefix = project.getKey() + "-";
        Issue maxKeyIssue = issueMapper.selectOne(
            new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, request.getProjectId())
                .likeRight(Issue::getIssueKey, keyPrefix)
                .orderByDesc(Issue::getId)
                .last("LIMIT 1"));
        int nextNum = 1;
        if (maxKeyIssue != null && maxKeyIssue.getIssueKey() != null) {
            String numStr = maxKeyIssue.getIssueKey().substring(keyPrefix.length());
            try { nextNum = Integer.parseInt(numStr) + 1; } catch (NumberFormatException ignored) {}
        }
        String key = project.getKey() + "-" + nextNum;

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
        // STORY fields
        issue.setUserRole(request.getUserRole());
        issue.setUserGoal(request.getUserGoal());
        issue.setBusinessValue(request.getBusinessValue());
        issue.setEpic(request.getEpic());
        issue.setParentId(request.getParentId());
        // BUG fields
        issue.setBugStatus("BUG".equals(request.getType()) ? "NEW" : null);
        issue.setEnvironment(request.getEnvironment());
        issue.setExpectedResult(request.getExpectedResult());
        issue.setActualResult(request.getActualResult());
        issue.setFoundVersion(request.getFoundVersion());
        issue.setFixedVersion(request.getFixedVersion());

        // Sort order — place at end of column
        int maxSort = 0;
        if (request.getColumnId() != null) {
            maxSort = issueMapper.selectList(
                    new LambdaQueryWrapper<Issue>().eq(Issue::getColumnId, request.getColumnId()))
                    .stream().mapToInt(Issue::getSortOrder).max().orElse(0);
        }
        issue.setSortOrder(maxSort + 1);

        // Validate parent: if parentId is set, parent must be a STORY
        if (request.getParentId() != null) {
            Issue parent = issueMapper.selectById(request.getParentId());
            if (parent == null || !"STORY".equals(parent.getType())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父级必须是一个故事(STORY)");
            }
        }

        issueMapper.insert(issue);

        // Activity log
        User reporter = userMapper.selectById(reporterId);
        ActivityLog log = new ActivityLog();
        log.setUserId(reporterId);
        log.setUsername(reporter != null ? reporter.getUsername() : "");
        log.setProjectId(issue.getProjectId());
        log.setTargetType("ISSUE");
        log.setTargetId(issue.getId());
        log.setAction("CREATE");
        log.setDetail("创建了" + typeLabel(issue.getType()) + " " + key + ": " + issue.getTitle());
        activityLogMapper.insert(log);

        // Save labels
        if (request.getLabels() != null) {
            for (String label : request.getLabels()) {
                IssueLabel il = new IssueLabel();
                il.setIssueId(issue.getId());
                il.setLabel(label);
                il.setColor("#409EFF");
                issueLabelMapper.insert(il);
            }
        }

        // WebSocket broadcast
        try {
            webSocketService.sendBoardUpdate(project.getId(), BoardSyncEvent.created(project.getId(), getById(issue.getId())));
        } catch (Exception ignored) { /* WebSocket optional */ }

        return getById(issue.getId());
    }

    public IssueVO getById(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        return toVO(issue);
    }

    @Transactional
    public void update(UpdateRequest request, Long operatorId) {
        Issue issue = issueMapper.selectById(request.getId());
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);

        Long oldAssigneeId = issue.getAssigneeId();
        String oldStatus = issue.getStatus();
        String oldBugStatus = issue.getBugStatus();

        if (request.getTitle() != null) issue.setTitle(request.getTitle());
        if (request.getDescription() != null) issue.setDescription(request.getDescription());
        if (request.getType() != null) issue.setType(request.getType());
        if (request.getPriority() != null) issue.setPriority(request.getPriority());
        if (request.getAssigneeId() != null) issue.setAssigneeId(request.getAssigneeId());
        if (request.getSprintId() != null) issue.setSprintId(request.getSprintId());
        if (request.getColumnId() != null) issue.setColumnId(request.getColumnId());
        if (request.getDueDate() != null) issue.setDueDate(request.getDueDate());
        if (request.getStoryPoints() != null) issue.setStoryPoints(request.getStoryPoints());
        if (request.getSeverity() != null) issue.setSeverity(request.getSeverity());
        if (request.getStepsToRepro() != null) issue.setStepsToRepro(request.getStepsToRepro());
        if (request.getStatus() != null) issue.setStatus(request.getStatus());
        // STORY fields
        if (request.getUserRole() != null) issue.setUserRole(request.getUserRole());
        if (request.getUserGoal() != null) issue.setUserGoal(request.getUserGoal());
        if (request.getBusinessValue() != null) issue.setBusinessValue(request.getBusinessValue());
        if (request.getEpic() != null) issue.setEpic(request.getEpic());
        if (request.getParentId() != null) {
            Issue parent = issueMapper.selectById(request.getParentId());
            if (parent == null || !"STORY".equals(parent.getType())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父级必须是一个故事(STORY)");
            }
            issue.setParentId(request.getParentId());
        }
        // BUG fields
        if (request.getBugStatus() != null) {
            validateBugTransition(issue.getBugStatus(), request.getBugStatus());
            issue.setBugStatus(request.getBugStatus());
        }
        if (request.getEnvironment() != null) issue.setEnvironment(request.getEnvironment());
        if (request.getExpectedResult() != null) issue.setExpectedResult(request.getExpectedResult());
        if (request.getActualResult() != null) issue.setActualResult(request.getActualResult());
        if (request.getFoundVersion() != null) issue.setFoundVersion(request.getFoundVersion());
        if (request.getFixedVersion() != null) issue.setFixedVersion(request.getFixedVersion());

        issueMapper.updateById(issue);

        // Notifications
        if (operatorId != null) {
            User operator = userMapper.selectById(operatorId);
            Long newAssigneeId = request.getAssigneeId();

            // Assignee changed
            if (newAssigneeId != null && !newAssigneeId.equals(oldAssigneeId)) {
                User assignee = userMapper.selectById(newAssigneeId);
                if (assignee != null) {
                    Notification n = new Notification();
                    n.setUserId(newAssigneeId);
                    n.setType("ASSIGNED");
                    n.setTitle("新任务分配");
                    n.setContent("你被分配了" + typeLabel(issue.getType()) + " " + issue.getIssueKey() + ": " + issue.getTitle());
                    n.setRelatedId(issue.getId());
                    n.setRelatedType("ISSUE");
                    n.setIsRead(0);
                    notificationService.create(n);
                    try {
                        webSocketService.sendNotification(newAssigneeId, Map.of(
                            "type", "ASSIGNED",
                            "title", n.getTitle(),
                            "content", n.getContent(),
                            "relatedId", issue.getId(),
                            "relatedType", "ISSUE"
                        ));
                    } catch (Exception ignored) {}
                }
            }

            // Status changed by non-assignee
            String newStatus = request.getStatus();
            if (newStatus != null && !newStatus.equals(oldStatus)
                && issue.getAssigneeId() != null
                && !issue.getAssigneeId().equals(operatorId)) {
                Notification n = new Notification();
                n.setUserId(issue.getAssigneeId());
                n.setType("STATUS_CHANGED");
                n.setTitle("任务状态变更");
                n.setContent("你负责的" + typeLabel(issue.getType()) + " " + issue.getIssueKey() + " 状态被更新为 " + newStatus);
                n.setRelatedId(issue.getId());
                n.setRelatedType("ISSUE");
                n.setIsRead(0);
                notificationService.create(n);
                try {
                    webSocketService.sendNotification(issue.getAssigneeId(), Map.of(
                        "type", "STATUS_CHANGED",
                        "title", n.getTitle(),
                        "content", n.getContent(),
                        "relatedId", issue.getId(),
                        "relatedType", "ISSUE"
                    ));
                } catch (Exception ignored) {}
            }

            // BUG status changed
            if (request.getBugStatus() != null && !request.getBugStatus().equals(oldBugStatus)) {
                String label = typeLabel(issue.getType());
                ActivityLog blog = new ActivityLog();
                blog.setUserId(operatorId);
                blog.setUsername(operator != null ? operator.getUsername() : "");
                blog.setProjectId(issue.getProjectId());
                blog.setTargetType("ISSUE");
                blog.setTargetId(issue.getId());
                blog.setAction("BUG_STATUS");
                blog.setDetail(label + " " + issue.getIssueKey() + " 缺陷状态: " + oldBugStatus + " → " + request.getBugStatus());
                activityLogMapper.insert(blog);
            }
        }

        // Activity log
        if (operatorId != null) {
            User operator = userMapper.selectById(operatorId);
            ActivityLog log = new ActivityLog();
            log.setUserId(operatorId);
            log.setUsername(operator != null ? operator.getUsername() : "");
            log.setProjectId(issue.getProjectId());
            log.setTargetType("ISSUE");
            log.setTargetId(issue.getId());
            log.setAction("UPDATE");
            log.setDetail("更新了" + typeLabel(issue.getType()) + " " + issue.getIssueKey());
            activityLogMapper.insert(log);
        }
    }

    @Transactional
    public void delete(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        Long projectId = null;
        if (issue != null) {
            projectId = issue.getProjectId();
            ActivityLog log = new ActivityLog();
            log.setProjectId(issue.getProjectId());
            log.setTargetType("ISSUE");
            log.setTargetId(issue.getId());
            log.setAction("DELETE");
            log.setDetail("删除了" + typeLabel(issue.getType()) + " " + issue.getIssueKey());
            activityLogMapper.insert(log);
        }
        issueMapper.deleteById(issueId);
        issueLabelMapper.delete(new LambdaQueryWrapper<IssueLabel>().eq(IssueLabel::getIssueId, issueId));

        if (projectId != null) {
            try {
                webSocketService.sendBoardUpdate(projectId, BoardSyncEvent.deleted(projectId, issueId));
            } catch (Exception ignored) {}
        }
    }

    public List<IssueVO> query(QueryRequest request) {
        LambdaQueryWrapper<Issue> qw = new LambdaQueryWrapper<>();
        if (request.getProjectId() != null) qw.eq(Issue::getProjectId, request.getProjectId());
        if (request.getSprintId() != null) qw.eq(Issue::getSprintId, request.getSprintId());
        if (request.getAssigneeId() != null) qw.eq(Issue::getAssigneeId, request.getAssigneeId());
        if (request.getType() != null) qw.eq(Issue::getType, request.getType());
        if (request.getPriority() != null) qw.eq(Issue::getPriority, request.getPriority());
        if (request.getStatus() != null) qw.eq(Issue::getStatus, request.getStatus());
        if (request.getBugStatus() != null) qw.eq(Issue::getBugStatus, request.getBugStatus());
        if (request.getEpic() != null) qw.eq(Issue::getEpic, request.getEpic());
        if (request.getParentId() != null) qw.eq(Issue::getParentId, request.getParentId());
        if (request.getKeyword() != null) qw.and(w -> w.like(Issue::getTitle, request.getKeyword())
                .or().like(Issue::getIssueKey, request.getKeyword()));
        qw.orderByDesc(Issue::getCreatedAt);

        Page<Issue> page = new Page<>(request.getPage(), request.getSize());
        Page<Issue> result = issueMapper.selectPage(page, qw);
        return result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
    }

    // ---- BUG status management ----

    public void updateBugStatus(BugStatusRequest request) {
        Issue issue = issueMapper.selectById(request.getIssueId());
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        if (!"BUG".equals(issue.getType())) throw new BusinessException(ResultCode.BAD_REQUEST, "只有缺陷(BUG)才能更改缺陷状态");
        validateBugTransition(issue.getBugStatus(), request.getBugStatus());
        issue.setBugStatus(request.getBugStatus());
        issueMapper.updateById(issue);
    }

    private void validateBugTransition(String from, String to) {
        Set<String> allowed = BUG_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不允许从 " + (from != null ? from : "无") + " 转换到 " + to);
        }
    }

    // ---- Acceptance Criteria management (STORY only) ----

    @Transactional
    public List<AcceptanceCriterion> addAcceptanceCriteria(Long issueId, AcceptanceCriteriaRequest req) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);
        if (!"STORY".equals(issue.getType())) throw new BusinessException(ResultCode.BAD_REQUEST, "只有故事(STORY)才能添加验收标准");

        List<AcceptanceCriterion> list = IssueDTO.parseAcceptanceCriteria(issue.getAcceptanceCriteria());
        AcceptanceCriterion ac = new AcceptanceCriterion();
        ac.setId(UUID.randomUUID().toString().substring(0, 8));
        ac.setText(req.getText());
        ac.setDone(false);
        list.add(ac);

        issue.setAcceptanceCriteria(IssueDTO.toJson(list));
        issueMapper.updateById(issue);
        return list;
    }

    @Transactional
    public List<AcceptanceCriterion> updateAcceptanceCriteria(Long issueId, String criteriaId, AcceptanceCriterion update) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);

        List<AcceptanceCriterion> list = IssueDTO.parseAcceptanceCriteria(issue.getAcceptanceCriteria());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(criteriaId)) {
                if (update.getText() != null) list.get(i).setText(update.getText());
                list.get(i).setDone(update.isDone());
                break;
            }
        }

        issue.setAcceptanceCriteria(IssueDTO.toJson(list));
        issueMapper.updateById(issue);
        return list;
    }

    @Transactional
    public List<AcceptanceCriterion> deleteAcceptanceCriteria(Long issueId, String criteriaId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new BusinessException(ResultCode.ISSUE_NOT_FOUND);

        List<AcceptanceCriterion> list = IssueDTO.parseAcceptanceCriteria(issue.getAcceptanceCriteria());
        list.removeIf(c -> c.getId().equals(criteriaId));

        issue.setAcceptanceCriteria(IssueDTO.toJson(list));
        issueMapper.updateById(issue);
        return list;
    }

    // ---- toVO helper ----

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
        vo.setProjectId(issue.getProjectId());

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
        // Labels
        List<IssueLabel> labels = issueLabelMapper.selectList(
                new LambdaQueryWrapper<IssueLabel>().eq(IssueLabel::getIssueId, issue.getId()));
        vo.setLabels(labels.stream().map(l -> {
            IssueLabelVO lvo = new IssueLabelVO();
            lvo.setId(l.getId());
            lvo.setLabel(l.getLabel());
            lvo.setColor(l.getColor());
            return lvo;
        }).collect(Collectors.toList()));

        // Parent info
        if (issue.getParentId() != null) {
            Issue parent = issueMapper.selectById(issue.getParentId());
            if (parent != null) {
                vo.setParentIssueKey(parent.getIssueKey());
                vo.setParentTitle(parent.getTitle());
            }
        }

        // Sub-tasks (for STORY)
        if ("STORY".equals(issue.getType())) {
            List<Issue> children = issueMapper.selectList(
                    new LambdaQueryWrapper<Issue>().eq(Issue::getParentId, issue.getId()));
            if (!children.isEmpty()) {
                vo.setSubTasks(children.stream().map(this::toVO).collect(Collectors.toList()));
            }
        }

        vo.setCreatedAt(issue.getCreatedAt());
        vo.setUpdatedAt(issue.getUpdatedAt());
        return vo;
    }

    // ---- label helpers ----

    static String typeLabel(String type) {
        if ("STORY".equals(type)) return "故事";
        if ("BUG".equals(type)) return "缺陷";
        return "任务";
    }

    public static String bugStatusLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "NEW" -> "新建";
            case "CONFIRMED" -> "已确认";
            case "IN_PROGRESS" -> "修复中";
            case "RESOLVED" -> "已解决";
            case "VERIFIED" -> "已验证";
            case "CLOSED" -> "已关闭";
            case "REOPENED" -> "重新打开";
            default -> s;
        };
    }
}
