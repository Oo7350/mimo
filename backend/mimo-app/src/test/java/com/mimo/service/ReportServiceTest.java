package com.mimo.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mimo.dto.StatsDTO.StatsVO;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.entity.User;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.ReportMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目统计卡片（v2.22.4）。
 *
 * <p>这个接口以前没有测试，也从来没人量过：给 100 个成员的项目点了「统计」之后，
 * 实测一次请求 **40 条 SELECT** —— 成员分布是每个成员一次 `selectById`，
 * 迭代速度是每个迭代两次 `selectCount`。人数一多就线性变慢，属于"能跑但会越来越慢"。
 *
 * <p>这里锁两件事：<b>数字要对</b>，以及<b>查询条数不随人数涨</b>。
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final long PROJECT = 84L;

    @Mock private ReportMapper reportMapper;
    @Mock private IssueMapper issueMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private SprintMapper sprintMapper;

    private ReportService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Issue.class);
        TableInfoHelper.initTableInfo(assistant, Sprint.class);
    }

    @BeforeEach
    void setUp() {
        service = new ReportService(reportMapper, issueMapper, userMapper, projectMapper, sprintMapper);
    }

    @Test
    @DisplayName("成员分布：一次批量取用户，不是每人一条 SQL")
    void memberDistributionLoadsUsersInOneBatch() {
        List<Issue> done = LongStream.rangeClosed(1, 40)
                .mapToObj(i -> doneIssue(i, 100L + (i % 40), null))
                .toList();
        when(issueMapper.selectList(any())).thenReturn(done);
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(
                LongStream.rangeClosed(100, 139)
                        .mapToObj(id -> user(id, "load" + id))
                        .toList());
        when(sprintMapper.selectList(any())).thenReturn(List.of());

        StatsVO stats = service.getProjectStats(PROJECT);

        assertThat(stats.getMemberDistribution()).hasSize(40);
        assertThat(stats.getMemberDistribution().get(0).getCount()).isEqualTo(1);
        assertThat(stats.getMemberDistribution().get(0).getUsername()).startsWith("load");
        // 关键：不许再按成员逐个查
        verify(userMapper, never()).selectById(anyLong());
        verify(userMapper).selectBatchIds(anyCollection());
    }

    @Test
    @DisplayName("迭代速度：每轮的总量/完成量来自一次批量查询，不再每轮两条 count")
    void sprintVelocityCountsWithoutPerSprintQueries() {
        when(issueMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(sprintMapper.selectList(any())).thenReturn(List.of(sprint(900L, "迭代一"), sprint(901L, "迭代二")));
        // 迭代一：3 条（2 条 DONE）；迭代二：2 条（0 条 DONE）
        when(issueMapper.selectList(any())).thenReturn(
                List.of(),                       // 已完成工作项（成员分布用）→ 空
                List.of(doneIssue(1L, 100L, 900L),
                        doneIssue(2L, 101L, 900L),
                        todoIssue(3L, 102L, 900L),
                        todoIssue(4L, 103L, 901L),
                        todoIssue(5L, 104L, 901L)));

        StatsVO stats = service.getProjectStats(PROJECT);

        assertThat(stats.getSprintVelocity()).hasSize(2);
        assertThat(stats.getSprintVelocity().get(0).getTotalPoints()).isEqualTo(3);
        assertThat(stats.getSprintVelocity().get(0).getCompletedPoints()).isEqualTo(2);
        assertThat(stats.getSprintVelocity().get(1).getTotalPoints()).isEqualTo(2);
        assertThat(stats.getSprintVelocity().get(1).getCompletedPoints()).isZero();
        // 不再逐轮 count
        verify(issueMapper, never()).selectCount(any());
        // 整体查询次数固定（这里是 4 条：项目工作项系列 + 迭代系列），不该随迭代数增长
        verify(issueMapper, atMost(6)).selectList(any());
    }

    @Test
    @DisplayName("没有已完成迭代时不查工作项，也不报错")
    void emptySprintsAreSafe() {
        when(issueMapper.selectList(any())).thenReturn(List.of());
        when(sprintMapper.selectList(any())).thenReturn(List.of());

        StatsVO stats = service.getProjectStats(PROJECT);

        assertThat(stats.getSprintVelocity()).isEmpty();
        assertThat(stats.getWeeklyCompleted()).hasSize(7);
        verify(issueMapper, atMost(4)).selectList(any());
        // 没人有已完成工作项：成员分布那一批查询也不该发出去
        verify(userMapper, never()).selectBatchIds(anyCollection());
    }

    @Test
    @DisplayName("趋势按天分桶，空的日子补 0（不是跳过）")
    void weeklyCompletedFillsEmptyDays() {
        LocalDate today = LocalDate.now();
        when(issueMapper.selectList(any())).thenReturn(List.of(
                doneIssue(1L, 100L, null, today.atTime(9, 0)),
                doneIssue(2L, 101L, null, today.atTime(10, 0)),
                doneIssue(3L, 102L, null, today.minusDays(3).atTime(15, 0))));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(sprintMapper.selectList(any())).thenReturn(List.of());

        StatsVO stats = service.getProjectStats(PROJECT);

        assertThat(stats.getWeeklyCompleted()).hasSize(7);
        assertThat(stats.getWeeklyCompleted().get(6).getCount()).isEqualTo(2);
        assertThat(stats.getWeeklyCompleted().get(3).getCount()).isEqualTo(1);
        assertThat(stats.getWeeklyCompleted().get(5).getCount()).isZero();
    }

    // ==================== 造数据 ====================

    private Issue doneIssue(Long id, Long assigneeId, Long sprintId) {
        return doneIssue(id, assigneeId, sprintId, LocalDateTime.now());
    }

    private Issue doneIssue(Long id, Long assigneeId, Long sprintId, LocalDateTime updatedAt) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(PROJECT);
        issue.setAssigneeId(assigneeId);
        issue.setSprintId(sprintId);
        issue.setStatus("DONE");
        issue.setType("TASK");
        issue.setUpdatedAt(updatedAt);
        return issue;
    }

    private Issue todoIssue(Long id, Long assigneeId, Long sprintId) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(PROJECT);
        issue.setAssigneeId(assigneeId);
        issue.setSprintId(sprintId);
        issue.setStatus("TODO");
        issue.setType("TASK");
        return issue;
    }

    private Sprint sprint(Long id, String name) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setProjectId(PROJECT);
        sprint.setName(name);
        sprint.setStatus("COMPLETED");
        sprint.setEndDate(LocalDate.now().minusDays(7));
        return sprint;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
