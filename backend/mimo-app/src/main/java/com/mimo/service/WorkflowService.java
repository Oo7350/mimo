package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.WorkflowDTO.*;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.Workflow;
import com.mimo.mapper.BoardColumnMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== CRUD ==========

    /** 获取项目的所有工作流 */
    public List<WorkflowVO> listByProject(Long projectId) {
        List<Workflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getProjectId, projectId));
        return workflows.stream().map(this::toVO).collect(Collectors.toList());
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

        try {
            String configJson = objectMapper.writeValueAsString(request.getConfig());

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
    public boolean validateTransition(Long projectId, String issueType,
                                     Long fromColumnId, Long toColumnId) {
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
        boolean found = wf.getConfig().getTransitions().stream()
                .anyMatch(t -> Objects.equals(t.getFromColumnId(), fromColumnId)
                        && Objects.equals(t.getToColumnId(), toColumnId));

        if (!found) {
            String fromName = getColumnName(projectId, fromColumnId);
            String toName = getColumnName(projectId, toColumnId);
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "工作流不允许从「" + fromName + "」转换到「" + toName + "」");
        }

        return true;
    }

    /**
     * 获取当前状态下可用的转换列表
     */
    public List<AvailableTransitionVO> getAvailableTransitions(Long projectId, String issueType, Long currentColumnId) {
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
                        return vo;
                    })
                    .collect(Collectors.toList());
        }

        return wf.getConfig().getTransitions().stream()
                .filter(t -> Objects.equals(t.getFromColumnId(), currentColumnId))
                .map(t -> {
                    AvailableTransitionVO vo = new AvailableTransitionVO();
                    vo.setToColumnId(t.getToColumnId());
                    vo.setToColumnName(getColumnName(projectId, t.getToColumnId()));
                    vo.setConditions(t.getConditions());
                    return vo;
                })
                .collect(Collectors.toList());
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
        // 至少一个终态节点
        boolean hasTerminal = config.getNodes().stream()
                .anyMatch(n -> Boolean.TRUE.equals(n.getIsTerminal()));
        if (!hasTerminal) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工作流至少需要一个终态节点");
        }
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
        return vo;
    }

    private String getColumnName(Long projectId, Long columnId) {
        if (columnId == null) return "未知";
        BoardColumn col = boardColumnMapper.selectById(columnId);
        return col != null ? col.getName() : "未知";
    }
}
