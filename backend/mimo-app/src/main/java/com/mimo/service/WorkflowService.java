package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkflowDTO.*;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.entity.Workflow;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流引擎服务 — CRUD + 状态机验证
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final ProjectMapper projectMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final AccessControlService accessControlService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== CRUD ==========

    /** 默认自动生成的类型（与前端类型选择器一致） */
    private static final List<String> DEFAULT_ISSUE_TYPES = Arrays.asList("STORY", "TASK", "BUG");

    /** 获取项目的所有工作流；项目还没有任何工作流时，按看板列自动生成默认配置 */
    @Transactional
    public List<WorkflowVO> listByProject(Long projectId, Long userId) {
        ensureDefaultWorkflows(projectId, userId);
        List<Workflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getProjectId, projectId));
        return workflows.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 项目缺少工作流时，按看板列自动生成默认工作流（幂等）。
     *
     * <p>默认采用 Scrum 模板（允许前进与回退），因此不会改变既有的拖拽行为；
     * 需要更严格约束时，可在设计器里切换 Kanban / Bug 模板或手工调整转换规则。</p>
     */
    @Transactional
    public void ensureDefaultWorkflows(Long projectId, Long userId) {
        List<BoardColumn> columns = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId)
                        .orderByAsc(BoardColumn::getSortOrder));
        if (columns.isEmpty()) {
            // 没有看板列就没有状态可编排，等列建好后再生成
            return;
        }
        for (String issueType : DEFAULT_ISSUE_TYPES) {
            boolean exists = workflowMapper.exists(new LambdaQueryWrapper<Workflow>()
                    .eq(Workflow::getProjectId, projectId)
                    .eq(Workflow::getIssueType, issueType));
            if (exists) {
                continue;
            }
            Workflow wf = new Workflow();
            wf.setProjectId(projectId);
            wf.setIssueType(issueType);
            wf.setName("默认工作流（自动生成）");
            wf.setConfig(writeConfig(buildScrumTemplate(columns)));
            wf.setIsActive(1);
            wf.setIsDefault(1);
            wf.setCreatedBy(userId);
            workflowMapper.insert(wf);
            log.info("自动生成默认工作流: projectId={}, issueType={}, workflowId={}",
                    projectId, issueType, wf.getId());
        }
    }

    /** 获取项目+类型的激活工作流 */
    public WorkflowVO getActiveWorkflow(Long projectId, String issueType) {
        Workflow wf = workflowMapper.selectOne(
                new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getProjectId, projectId)
                        .eq(Workflow::getIssueType, issueType)
                        .eq(Workflow::getIsActive, 1));
        return wf != null ? toVO(wf) : null;
    }

    /** 创建或更新工作流 */
    @Transactional
    public WorkflowVO saveWorkflow(SaveWorkflowRequest request, Long userId) {
        // 校验节点中的 columnId 确实属于该项目
        validateConfig(request.getProjectId(), request.getConfig());

        Workflow existing = workflowMapper.selectOne(
                new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getProjectId, request.getProjectId())
                        .eq(Workflow::getIssueType, request.getIssueType()));

        String configJson = writeConfig(request.getConfig());

        if (existing != null) {
            existing.setName(request.getName());
            existing.setConfig(configJson);
            existing.setIsActive(1);
            workflowMapper.updateById(existing);
            return toVO(existing);
        } else {
            Workflow wf = new Workflow();
            wf.setProjectId(request.getProjectId());
            wf.setIssueType(request.getIssueType());
            wf.setName(request.getName());
            wf.setConfig(configJson);
            wf.setIsActive(1);
            wf.setIsDefault(0);
            wf.setCreatedBy(userId);
            workflowMapper.insert(wf);
            return toVO(wf);
        }
    }

    /** 序列化工作流配置，失败时给出可读提示 */
    private String writeConfig(WorkflowConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工作流配置序列化失败: " + e.getMessage());
        }
    }

    /** 删除工作流 */
    @Transactional
    public void deleteWorkflow(Long workflowId) {
        workflowMapper.deleteById(workflowId);
    }

    /** 应用预设模板 */
    @Transactional
    public WorkflowVO applyTemplate(Long projectId, String issueType, String templateName, Long userId) {
        List<BoardColumn> columns = boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId)
                        .orderByAsc(BoardColumn::getSortOrder));

        WorkflowConfig config = buildTemplateConfig(templateName, columns);
        if (config == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未知模板: " + templateName);
        }

        SaveWorkflowRequest req = new SaveWorkflowRequest();
        req.setProjectId(projectId);
        req.setIssueType(issueType);
        req.setName(templateName + " 工作流");
        req.setConfig(config);
        return saveWorkflow(req, userId);
    }

    // ========== 状态机验证 ==========

    /**
     * 验证状态转换是否合法
     * @return true 如果转换允许；false 或抛异常如果不允许
     */
    public boolean validateTransition(Issue issue, Long fromColumnId, Long toColumnId, Long operatorId) {
        Long projectId = issue.getProjectId();
        String issueType = issue.getType();
        WorkflowVO wf = getActiveWorkflow(projectId, issueType);
        if (wf == null) {
            // 无工作流定义 → 允许所有转换（保持向后兼容）
            return true;
        }

        // 相同列 → 允许（仅调整排序）
        if (Objects.equals(fromColumnId, toColumnId)) {
            return true;
        }

        // 查找匹配的转换规则
        WorkflowTransition transition = wf.getConfig().getTransitions().stream()
                .filter(t -> Objects.equals(t.getFromColumnId(), fromColumnId)
                        && Objects.equals(t.getToColumnId(), toColumnId))
                .findFirst().orElse(null);

        if (transition == null) {
            String fromName = getColumnName(projectId, fromColumnId);
            String toName = getColumnName(projectId, toColumnId);
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "工作流不允许从「" + fromName + "」转换到「" + toName + "」");
        }

        if (transition.getConditions() != null && !transition.getConditions().isEmpty()
                && !matchesAnyCondition(transition.getConditions(), issue, operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前用户不满足该状态转换的条件");
        }

        // 节点责任人：这一步谁负责推进。项目管理员永远能推进（否则人一休假流程就卡死）
        WorkflowNode fromNode = findNode(wf, fromColumnId);
        if (fromNode != null && fromNode.getOwnerUserId() != null && operatorId != null
                && !Objects.equals(fromNode.getOwnerUserId(), operatorId)
                && !isProjectAdmin(projectId, operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "这一步由「" + displayName(fromNode.getOwnerUserId()) + "」负责，只有他或项目管理员能推进");
        }

        return true;
    }

    public boolean validateTransition(Long projectId, String issueType,
                                      Long fromColumnId, Long toColumnId) {
        Issue issue = new Issue();
        issue.setProjectId(projectId);
        issue.setType(issueType);
        return validateTransition(issue, fromColumnId, toColumnId, null);
    }

    /**
     * 获取当前状态下可用的转换列表
     *
     * <p>v2.19.6 起每条转换都带上「你现在能不能走这一步」和「不能走时卡在谁那里」：
     * 界面上要么给个能点的按钮，要么直接说清原因，而不是点了再被 403。</p>
     *
     * @param operatorId 当前登录用户；null 表示不做"人"的判定（内部调用/兼容旧调用方）
     */
    public List<AvailableTransitionVO> getAvailableTransitions(Long projectId, String issueType,
                                                               Long currentColumnId, Long operatorId) {
        WorkflowVO wf = getActiveWorkflow(projectId, issueType);
        if (wf == null) {
            // 无工作流 → 返回所有列作为可转移目标
            List<BoardColumn> columns = boardColumnMapper.selectList(
                    new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId)
                            .orderByAsc(BoardColumn::getSortOrder));
            return columns.stream()
                    .filter(c -> !c.getId().equals(currentColumnId))
                    .map(c -> {
                        AvailableTransitionVO vo = new AvailableTransitionVO();
                        vo.setToColumnId(c.getId());
                        vo.setToColumnName(c.getName());
                        vo.setConditions(Collections.emptyList());
                        vo.setAllowed(Boolean.TRUE);
                        return vo;
                    })
                    .collect(Collectors.toList());
        }

        WorkflowNode fromNode = findNode(wf, currentColumnId);
        Long ownerId = fromNode != null ? fromNode.getOwnerUserId() : null;

        return wf.getConfig().getTransitions().stream()
                .filter(t -> Objects.equals(t.getFromColumnId(), currentColumnId))
                .map(t -> {
                    AvailableTransitionVO vo = new AvailableTransitionVO();
                    vo.setToColumnId(t.getToColumnId());
                    vo.setToColumnName(getColumnName(projectId, t.getToColumnId()));
                    vo.setConditions(t.getConditions());
                    vo.setFromOwnerId(ownerId);
                    vo.setFromOwnerName(ownerId != null ? displayName(ownerId) : null);

                    String blocked = blockedReason(t.getConditions(), ownerId, projectId, operatorId);
                    vo.setAllowed(blocked == null);
                    vo.setReason(blocked);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 不传 operatorId 的旧签名（内部/测试用）：只列规则，不做"人"的判定 */
    public List<AvailableTransitionVO> getAvailableTransitions(Long projectId, String issueType,
                                                               Long currentColumnId) {
        return getAvailableTransitions(projectId, issueType, currentColumnId, null);
    }

    /**
     * 这一步为什么推不动？能推（或者说不准）返回 null。
     *
     * <p>只下"确定"的结论：责任人不是当前用户、或规则写着"仅管理员"而当前用户不是管理员。
     * 指派人 / 报告人这类条件需要具体工作项才判得准，这里不猜——真被拦下来时
     * {@link #validateTransition} 会给准确提示。</p>
     */
    private String blockedReason(List<String> conditions, Long ownerId, Long projectId, Long operatorId) {
        if (operatorId == null) return null;
        if (ownerId != null && !Objects.equals(ownerId, operatorId) && !isProjectAdmin(projectId, operatorId)) {
            return "这一步由「" + displayName(ownerId) + "」负责";
        }
        if (conditions != null && !conditions.isEmpty()) {
            boolean adminOnly = conditions.stream()
                    .filter(Objects::nonNull)
                    .allMatch(c -> "ROLE_ADMIN".equalsIgnoreCase(c.trim()));
            if (adminOnly && !isProjectAdmin(projectId, operatorId)) {
                return "只有项目管理员能执行这一步";
            }
        }
        return null;
    }

    // ========== 预设模板 ==========

    private WorkflowConfig buildTemplateConfig(String templateName, List<BoardColumn> columns) {
        if (columns.isEmpty()) return null;

        switch (templateName) {
            case "scrum":
                return buildScrumTemplate(columns);
            case "kanban":
                return buildKanbanTemplate(columns);
            case "bug":
                return buildBugTemplate(columns);
            default:
                return null;
        }
    }

    /** Scrum: Backlog → In Progress → Review → Done */
    private WorkflowConfig buildScrumTemplate(List<BoardColumn> columns) {
        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(columns.stream().map(c -> {
            WorkflowNode n = new WorkflowNode();
            n.setColumnId(c.getId());
            n.setName(c.getName());
            n.setColor(c.getColor());
            n.setIsTerminal(c.getName().contains("完成") || c.getName().equalsIgnoreCase("done"));
            return n;
        }).collect(Collectors.toList()));

        List<WorkflowTransition> transitions = new ArrayList<>();
        for (int i = 0; i < columns.size() - 1; i++) {
            WorkflowTransition t = new WorkflowTransition();
            t.setFromColumnId(columns.get(i).getId());
            t.setToColumnId(columns.get(i + 1).getId());
            t.setConditions(Collections.emptyList());
            transitions.add(t);
        }
        // 允许回退
        for (int i = columns.size() - 1; i > 0; i--) {
            WorkflowTransition t = new WorkflowTransition();
            t.setFromColumnId(columns.get(i).getId());
            t.setToColumnId(columns.get(i - 1).getId());
            t.setConditions(Collections.emptyList());
            transitions.add(t);
        }
        config.setTransitions(transitions);
        return config;
    }

    /** Kanban: Todo → Doing → Done (只允许前进) */
    private WorkflowConfig buildKanbanTemplate(List<BoardColumn> columns) {
        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(columns.stream().map(c -> {
            WorkflowNode n = new WorkflowNode();
            n.setColumnId(c.getId());
            n.setName(c.getName());
            n.setColor(c.getColor());
            n.setIsTerminal(c.getName().contains("完成") || c.getName().equalsIgnoreCase("done"));
            return n;
        }).collect(Collectors.toList()));

        List<WorkflowTransition> transitions = new ArrayList<>();
        for (int i = 0; i < columns.size() - 1; i++) {
            WorkflowTransition t = new WorkflowTransition();
            t.setFromColumnId(columns.get(i).getId());
            t.setToColumnId(columns.get(i + 1).getId());
            t.setConditions(Collections.emptyList());
            transitions.add(t);
        }
        config.setTransitions(transitions);
        return config;
    }

    /** Bug: 只允许前进，禁止回退 */
    private WorkflowConfig buildBugTemplate(List<BoardColumn> columns) {
        return buildKanbanTemplate(columns); // Bug 跟踪与 Kanban 相同的单向流
    }

    // ========== 辅助方法 ==========

    private void validateConfig(Long projectId, WorkflowConfig config) {
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工作流至少需要一个节点");
        }
        // 校验所有 columnId 属于该项目
        List<Long> columnIds = config.getNodes().stream()
                .map(WorkflowNode::getColumnId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!columnIds.isEmpty()) {
            long validCount = boardColumnMapper.selectCount(
                    new LambdaQueryWrapper<BoardColumn>()
                            .eq(BoardColumn::getProjectId, projectId)
                            .in(BoardColumn::getId, columnIds));
            if (validCount != columnIds.size()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "工作流节点包含不属于该项目的列");
            }
        }
        // 节点责任人必须是个真实存在的用户：否则配错了没人能推进，还查不出为什么
        for (WorkflowNode node : config.getNodes()) {
            if (node.getOwnerUserId() != null && userMapper.selectById(node.getOwnerUserId()) == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "节点「" + node.getName() + "」的责任人不存在（userId=" + node.getOwnerUserId() + "）");
            }
        }
        // 至少一个终态节点
        boolean hasTerminal = config.getNodes().stream()
                .anyMatch(n -> Boolean.TRUE.equals(n.getIsTerminal()));
        if (!hasTerminal) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工作流至少需要一个终态节点");
        }
    }

    /** 按列 id 找节点（节点 = 看板列） */
    private WorkflowNode findNode(WorkflowVO wf, Long columnId) {
        if (wf == null || wf.getConfig() == null || wf.getConfig().getNodes() == null || columnId == null) {
            return null;
        }
        return wf.getConfig().getNodes().stream()
                .filter(n -> Objects.equals(n.getColumnId(), columnId))
                .findFirst().orElse(null);
    }

    /** 用户显示名：优先昵称（User 表没有昵称字段，这里退到用户名），最后兜底成"用户#id" */
    private String displayName(Long userId) {
        if (userId == null) return "未指定";
        User user = userMapper.selectById(userId);
        if (user == null) return "用户#" + userId;
        return user.getUsername() != null ? user.getUsername() : "用户#" + userId;
    }

    private WorkflowVO toVO(Workflow wf) {
        WorkflowVO vo = new WorkflowVO();
        vo.setId(wf.getId());
        vo.setProjectId(wf.getProjectId());
        vo.setIssueType(wf.getIssueType());
        vo.setName(wf.getName());
        vo.setIsActive(wf.getIsActive() == 1);
        vo.setIsDefault(wf.getIsDefault() == 1);
        try {
            vo.setConfig(objectMapper.readValue(wf.getConfig(), WorkflowConfig.class));
        } catch (Exception e) {
            log.error("解析工作流配置失败, workflowId={}", wf.getId(), e);
            vo.setConfig(new WorkflowConfig());
        }
        // 责任人名字回填：设计器/看板都要显示"这一步谁负责"，别让前端再查一遍用户
        if (vo.getConfig() != null && vo.getConfig().getNodes() != null) {
            vo.getConfig().getNodes().forEach(n -> {
                if (n.getOwnerUserId() != null) {
                    n.setOwnerName(displayName(n.getOwnerUserId()));
                }
            });
        }
        return vo;
    }

    private String getColumnName(Long projectId, Long columnId) {
        if (columnId == null) return "未知";
        BoardColumn col = boardColumnMapper.selectById(columnId);
        return col != null ? col.getName() : "未知";
    }

    private boolean matchesAnyCondition(List<String> conditions, Issue issue, Long operatorId) {
        if (operatorId == null) return conditions.isEmpty();
        for (String raw : conditions) {
            if (raw == null) continue;
            String condition = raw.trim().toUpperCase(Locale.ROOT);
            switch (condition) {
                case "ASSIGNEE":
                    if (Objects.equals(issue.getAssigneeId(), operatorId)) return true;
                    break;
                case "REPORTER":
                    if (Objects.equals(issue.getReporterId(), operatorId)) return true;
                    break;
                case "ROLE_ADMIN":
                    if (isProjectAdmin(issue.getProjectId(), operatorId)) return true;
                    break;
                default:
                    log.warn("忽略未知工作流条件: {}", raw);
            }
        }
        return false;
    }

    private boolean isProjectAdmin(Long projectId, Long userId) {
        if (accessControlService.isSystemAdmin(userId)) return true;
        Project project = projectMapper.selectById(projectId);
        if (project == null) return false;
        if (Objects.equals(project.getOwnerId(), userId)) return true;
        return accessControlService.isTeamAdmin(userId, project.getTeamId());
    }
}
