package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.IssueDTO;
import com.mimo.dto.WorkflowDTO.*;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.entity.Workflow;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.IssueMapper;
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
    private final IssueMapper issueMapper;
    private final AccessControlService accessControlService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 前提条件：验收标准必须全部勾完（v2.22.0） */
    public static final String PREREQUISITE_ACCEPTANCE_DONE = "ACCEPTANCE_DONE";

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
            WorkflowConfig template = buildScrumTemplate(columns);
            fillNodeIdentity(template);   // 直接带上 nodeKey/坐标落库，省得每次读出再补
            wf.setConfig(writeConfig(template));
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
        WorkflowConfig config = request.getConfig();

        // ① 归一化：补 nodeKey、补画布坐标、把连线的 key 与 columnId 对齐
        normalizeConfig(config);
        // ② 自由节点投影成看板列（节点是流程状态，列是它站的地方）
        List<String> createdColumns = projectFreeNodes(request.getProjectId(), config);
        // ③ 校验（列属于项目、责任人存在、至少一个终态）
        validateConfig(request.getProjectId(), config);

        Workflow existing = workflowMapper.selectOne(
                new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getProjectId, request.getProjectId())
                        .eq(Workflow::getIssueType, request.getIssueType()));

        String configJson = writeConfig(config);

        WorkflowVO vo;
        if (existing != null) {
            existing.setName(request.getName());
            existing.setConfig(configJson);
            existing.setIsActive(1);
            workflowMapper.updateById(existing);
            vo = toVO(existing);
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
            vo = toVO(wf);
        }
        vo.setCreatedColumns(createdColumns);
        return vo;
    }

    /**
     * 归一化配置：给节点补稳定身份与坐标，并把连线两端的 nodeKey / columnId 互相补齐。
     *
     * <p>为什么要有 nodeKey：以前 node 只有 columnId，等于"节点不存在于流程里，只存在于看板上"，
     * 所以没法先设计流程再落列。补上身份后，同一份配置既能被画布按 key 连线，也能被看板按 columnId 判定。</p>
     */
    private void normalizeConfig(WorkflowConfig config) {
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工作流至少需要一个节点");
        }
        Set<String> usedKeys = new LinkedHashSet<>();
        Map<Long, WorkflowNode> byColumn = new LinkedHashMap<>();
        int index = 0;
        for (WorkflowNode node : config.getNodes()) {
            String key = trimToNull(node.getNodeKey());
            if (key == null) {
                key = node.getColumnId() != null ? "col-" + node.getColumnId() : "n-" + shortId();
            }
            if (!usedKeys.add(key)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "节点标识重复：" + key);
            }
            node.setNodeKey(key);
            if (node.getX() == null || node.getY() == null) {
                // 没坐标就按顺序排一行，画布打开时不至于全叠在左上角
                node.setX(40 + index * 200);
                node.setY(80);
            }
            index++;
            if (node.getColumnId() != null) {
                byColumn.put(node.getColumnId(), node);
            }
        }

        if (config.getTransitions() == null) {
            config.setTransitions(new ArrayList<>());
            return;
        }
        Map<String, WorkflowNode> byKey = new LinkedHashMap<>();
        config.getNodes().forEach(n -> byKey.put(n.getNodeKey(), n));

        for (WorkflowTransition t : config.getTransitions()) {
            WorkflowNode from = resolveNode(t.getFromNodeKey(), t.getFromColumnId(), byKey, byColumn);
            WorkflowNode to = resolveNode(t.getToNodeKey(), t.getToColumnId(), byKey, byColumn);
            if (from == null || to == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "转换规则指向了不存在的节点（" + t.getFromNodeKey() + " → " + t.getToNodeKey() + "）");
            }
            t.setFromNodeKey(from.getNodeKey());
            t.setToNodeKey(to.getNodeKey());
            t.setFromColumnId(from.getColumnId());
            t.setToColumnId(to.getColumnId());
        }
    }

    /** 连线端点解析：优先 nodeKey，其次 columnId（兼容旧配置） */
    private WorkflowNode resolveNode(String nodeKey, Long columnId,
                                     Map<String, WorkflowNode> byKey, Map<Long, WorkflowNode> byColumn) {
        if (trimToNull(nodeKey) != null) {
            WorkflowNode node = byKey.get(nodeKey.trim());
            if (node != null) return node;
        }
        return columnId != null ? byColumn.get(columnId) : null;
    }

    /**
     * 自由节点投影：没有看板列的节点，保存时在项目里补一列。
     *
     * <p>为什么不是"允许节点没有列"：工作项的总得停在某一列上，界面才画得出来。
     * 让节点凭空存在、看板却看不见它，等于让界面撒谎。所以这里的做法是——
     * 你把流程画出来，缺的列我补上，然后**明确告诉你补了哪几列**。</p>
     *
     * @return 本次新建的列名（给前端提示用）
     */
    private List<String> projectFreeNodes(Long projectId, WorkflowConfig config) {
        List<String> created = new ArrayList<>();
        int nextSort = boardColumnMapper.selectList(
                        new LambdaQueryWrapper<BoardColumn>().eq(BoardColumn::getProjectId, projectId))
                .stream()
                .map(BoardColumn::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        for (WorkflowNode node : config.getNodes()) {
            if (node.getColumnId() != null) continue;
            String name = trimToNull(node.getName());
            if (name == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "新增的节点需要填名字（它要成为看板上的一列）");
            }
            BoardColumn column = new BoardColumn();
            column.setProjectId(projectId);
            column.setName(name);
            column.setColor(trimToNull(node.getColor()) != null ? node.getColor() : "#909399");
            column.setSortOrder(nextSort++);
            boardColumnMapper.insert(column);
            node.setColumnId(column.getId());
            created.add(name);
            log.info("工作流节点投影为看板列: projectId={}, nodeKey={}, columnId={}, name={}",
                    projectId, node.getNodeKey(), column.getId(), name);
        }
        return created;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

        // 前提条件先看：它给出的原因最具体（"验收标准还有 2 项没勾完"比"你不满足条件"有用得多）
        String unmet = checkPrerequisites(transition.getPrerequisites(), issue);
        if (unmet != null) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "还不能进入「" + getColumnName(projectId, toColumnId) + "」：" + unmet);
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
     * @param issueId    具体工作项；给了才算得出"验收标准还有几项没勾"这类前提，
     *                   不给就只能对"谁"下结论。空值不代表允许，只是无法提前判断。
     */
    public List<AvailableTransitionVO> getAvailableTransitions(Long projectId, String issueType,
                                                               Long currentColumnId, Long operatorId,
                                                               Long issueId) {
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
        Issue issue = issueId != null ? issueMapper.selectById(issueId) : null;

        return wf.getConfig().getTransitions().stream()
                .filter(t -> Objects.equals(t.getFromColumnId(), currentColumnId))
                .map(t -> {
                    AvailableTransitionVO vo = new AvailableTransitionVO();
                    vo.setToColumnId(t.getToColumnId());
                    vo.setToColumnName(getColumnName(projectId, t.getToColumnId()));
                    vo.setConditions(t.getConditions());
                    vo.setPrerequisites(t.getPrerequisites());
                    vo.setFromOwnerId(ownerId);
                    vo.setFromOwnerName(ownerId != null ? displayName(ownerId) : null);

                    String blocked = blockedReason(t.getConditions(), t.getPrerequisites(),
                            issue, ownerId, projectId, operatorId);
                    vo.setAllowed(blocked == null);
                    vo.setReason(blocked);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 不带 issueId：只对"谁可以做"下结论 */
    public List<AvailableTransitionVO> getAvailableTransitions(Long projectId, String issueType,
                                                               Long currentColumnId, Long operatorId) {
        return getAvailableTransitions(projectId, issueType, currentColumnId, operatorId, null);
    }

    /** 不传 operatorId 的旧签名（内部/测试用）：只列规则，不做"人"的判定 */
    public List<AvailableTransitionVO> getAvailableTransitions(Long projectId, String issueType,
                                                               Long currentColumnId) {
        return getAvailableTransitions(projectId, issueType, currentColumnId, null, null);
    }

    /**
     * 这一步为什么推不动？能推（或者说不准）返回 null。
     *
     * <p>只下"确定"的结论：前提条件不满足（验收标准没勾完，有 issue 时才算得出）、
     * 责任人不是当前用户、或规则写着"仅管理员"而当前用户不是管理员。
     * 指派人 / 报告人这类条件需要具体工作项才判得准，这里不猜——真被拦下来时
     * {@link #validateTransition} 会给准确提示。</p>
     */
    private String blockedReason(List<String> conditions, List<String> prerequisites, Issue issue,
                                 Long ownerId, Long projectId, Long operatorId) {
        if (operatorId == null) return null;
        if (issue != null) {
            String unmet = checkPrerequisites(prerequisites, issue);
            if (unmet != null) return unmet;
        }
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

    /**
     * 前提条件检查：全部满足才返回 null，否则返回一句人话原因。
     *
     * <p>关于 {@code ACCEPTANCE_DONE} 与"没有验收标准"的处理：一条都没写的工作项**不受限制**。
     * 否则所有不写验收标准的老任务会永远推不动，那不是严格，那是把流程变成死路。</p>
     */
    private String checkPrerequisites(List<String> prerequisites, Issue issue) {
        if (prerequisites == null || prerequisites.isEmpty() || issue == null) return null;
        for (String raw : prerequisites) {
            String prerequisite = trimToNull(raw);
            if (prerequisite == null) continue;
            switch (prerequisite.toUpperCase(Locale.ROOT)) {
                case PREREQUISITE_ACCEPTANCE_DONE: {
                    List<IssueDTO.AcceptanceCriterion> criteria =
                            IssueDTO.parseAcceptanceCriteria(issue.getAcceptanceCriteria());
                    if (criteria.isEmpty()) continue;   // 没写验收标准 → 不拦
                    long open = criteria.stream().filter(c -> !c.isDone()).count();
                    if (open > 0) {
                        return "验收标准还有 " + open + " 项没勾完（共 " + criteria.size() + " 项）";
                    }
                    break;
                }
                default:
                    log.warn("忽略未知前提条件: {}", raw);
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
        // 节点是流程状态，名字必须人看得懂（它同时会成为看板列名）
        for (WorkflowNode node : config.getNodes()) {
            if (trimToNull(node.getName()) == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "工作流节点「" + node.getNodeKey() + "」还没有名字");
            }
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
        // 一个看板列只能是一个节点的落脚点：两个节点共列，工作项就说不清在流程的哪一步
        Set<Long> seenColumns = new LinkedHashSet<>();
        for (WorkflowNode node : config.getNodes()) {
            if (node.getColumnId() == null) continue;
            if (!seenColumns.add(node.getColumnId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "看板列只能对应一个流程节点（「" + node.getName() + "」重复绑定了同一列）");
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
        WorkflowConfig config = vo.getConfig();
        if (config == null) return vo;

        // 老配置（只有 columnId）在读出时就补上身份与坐标：画布不该看到"没有 key 的节点"，
        // 否则拖连线时两边对不上，用户会以为是设计器坏了
        fillNodeIdentity(config);

        // 责任人名字回填：设计器/看板都要显示"这一步谁负责"，别让前端再查一遍用户
        if (config.getNodes() != null) {
            config.getNodes().forEach(n -> {
                if (n.getOwnerUserId() != null) {
                    n.setOwnerName(displayName(n.getOwnerUserId()));
                }
            });
        }
        return vo;
    }

    /** 只读侧归一化：补 nodeKey/坐标、对齐连线两端（不建列、不校验） */
    private void fillNodeIdentity(WorkflowConfig config) {
        if (config.getNodes() == null) return;
        int index = 0;
        Map<Long, WorkflowNode> byColumn = new LinkedHashMap<>();
        Set<String> usedKeys = new LinkedHashSet<>();
        for (WorkflowNode node : config.getNodes()) {
            String key = trimToNull(node.getNodeKey());
            if (key == null) {
                key = node.getColumnId() != null ? "col-" + node.getColumnId() : "n-old-" + index;
            }
            if (!usedKeys.add(key)) {
                key = key + "-" + index;   // 手工改过的坏数据也别让整页打不开
            }
            node.setNodeKey(key);
            if (node.getX() == null) node.setX(40 + index * 200);
            if (node.getY() == null) node.setY(80);
            index++;
            if (node.getColumnId() != null) byColumn.put(node.getColumnId(), node);
        }
        Map<String, WorkflowNode> byKey = new LinkedHashMap<>();
        config.getNodes().forEach(n -> byKey.put(n.getNodeKey(), n));
        if (config.getTransitions() == null) return;
        for (WorkflowTransition t : config.getTransitions()) {
            WorkflowNode from = resolveNode(t.getFromNodeKey(), t.getFromColumnId(), byKey, byColumn);
            WorkflowNode to = resolveNode(t.getToNodeKey(), t.getToColumnId(), byKey, byColumn);
            if (from != null) {
                t.setFromNodeKey(from.getNodeKey());
                t.setFromColumnId(from.getColumnId());
            }
            if (to != null) {
                t.setToNodeKey(to.getNodeKey());
                t.setToColumnId(to.getColumnId());
            }
        }
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
