package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.entity.Issue;
import com.mimo.mapper.IssueMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KNOWN-004 回归：任务编号取「项目内最大值 + 1」，并发下会撞 uk_issue_key。
 * 撞键后必须重新取号重试，而不是把 DuplicateKeyException 直接抛成 500。
 */
@ExtendWith(MockitoExtension.class)
class IssueKeyAllocatorTest {

    @Mock
    private IssueMapper issueMapper;

    private IssueKeyAllocator allocator() {
        return new IssueKeyAllocator(issueMapper);
    }

    private Issue newIssue() {
        Issue issue = new Issue();
        issue.setProjectId(1L);
        issue.setTitle("示例任务");
        return issue;
    }

    @Test
    void firstAllocationStartsAtOne() {
        when(issueMapper.selectMaxIssueKeySequence(1L, "MIMO-")).thenReturn(null);
        when(issueMapper.insert(any(Issue.class))).thenReturn(1);

        Issue issue = newIssue();
        String key = allocator().insertWithAllocatedKey(issue, "MIMO");

        assertThat(key).isEqualTo("MIMO-1");
        assertThat(issue.getIssueKey()).isEqualTo("MIMO-1");
        verify(issueMapper, times(1)).insert(any(Issue.class));
    }

    @Test
    void duplicateKeyShouldReallocateAndSucceed() {
        // 第一次取到 5（算出 MIMO-6），插入撞号；重新取号时最大值已变成 6
        when(issueMapper.selectMaxIssueKeySequence(1L, "MIMO-")).thenReturn(5, 6);
        when(issueMapper.insert(any(Issue.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'MIMO-6' for key 'uk_issue_key'"))
                .thenReturn(1);

        Issue issue = newIssue();
        String key = allocator().insertWithAllocatedKey(issue, "MIMO");

        assertThat(key).isEqualTo("MIMO-7");
        assertThat(issue.getIssueKey()).isEqualTo("MIMO-7");
        verify(issueMapper, times(2)).selectMaxIssueKeySequence(anyLong(), anyString());
        verify(issueMapper, times(2)).insert(any(Issue.class));
    }

    @Test
    void shouldGiveUpWithReadableErrorAfterMaxAttempts() {
        when(issueMapper.selectMaxIssueKeySequence(1L, "MIMO-")).thenReturn(5);
        when(issueMapper.insert(any(Issue.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'MIMO-6' for key 'uk_issue_key'"));

        assertThatThrownBy(() -> allocator().insertWithAllocatedKey(newIssue(), "MIMO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务编号分配冲突");

        verify(issueMapper, times(IssueKeyAllocator.MAX_ATTEMPTS)).insert(any(Issue.class));
    }
}
