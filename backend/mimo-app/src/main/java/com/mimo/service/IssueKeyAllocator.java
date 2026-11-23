package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.entity.Issue;
import com.mimo.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 任务编号（issue_key）分配器。
 *
 * <p>KNOWN-004：编号取「项目内当前最大值 + 1」，两个并发事务可能读到同一个最大值，
 * 撞上 {@code uk_issue_key} 唯一约束后以前把 DuplicateKeyException 直接抛给用户（500）。
 * 这里把「取号 + 插入」收成一个可重试单元：撞键就重新取号再插，超过次数才报可读错误。
 *
 * <p>能在一个事务里重试的原因：MySQL/InnoDB 的重复键错误只回滚当前语句，事务本身仍可用。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IssueKeyAllocator {

    /** 并发抢号的最大尝试次数（含首次） */
    static final int MAX_ATTEMPTS = 5;

    private final IssueMapper issueMapper;

    /**
     * 分配编号并插入 issue。成功返回最终编号；连续冲突时抛 {@link BusinessException}（409）。
     *
     * @param issue      待插入的任务（其余字段需已填充，projectId 必填）
     * @param projectKey 项目 Key（不含尾部连字符）
     * @return 最终写入的 issueKey
     */
    public String insertWithAllocatedKey(Issue issue, String projectKey) {
        String prefix = projectKey + "-";
        DataIntegrityViolationException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String key = prefix + nextSequence(issue.getProjectId(), prefix);
            issue.setIssueKey(key);
            // 上一次失败可能留下自增主键，清掉避免 MyBatis-Plus 走 update
            issue.setId(null);
            try {
                issueMapper.insert(issue);
                if (attempt > 1) {
                    log.info("任务编号重试成功: projectId={}, key={}, attempt={}",
                            issue.getProjectId(), key, attempt);
                }
                return key;
            } catch (DataIntegrityViolationException e) {
                lastError = e;
                log.warn("任务编号冲突，重新取号: projectId={}, key={}, attempt={}",
                        issue.getProjectId(), key, attempt);
            }
        }
        log.error("任务编号连续 {} 次冲突，放弃: projectId={}", MAX_ATTEMPTS, issue.getProjectId(), lastError);
        throw new BusinessException(ResultCode.CONFLICT, "任务编号分配冲突，请稍后重试");
    }

    private int nextSequence(Long projectId, String prefix) {
        Integer max = issueMapper.selectMaxIssueKeySequence(projectId, prefix);
        return (max == null ? 0 : max) + 1;
    }
}
