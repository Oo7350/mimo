package com.mimo.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimo.common.BusinessException;
import com.mimo.dto.ProjectDTO.ProjectVO;
import com.mimo.dto.WorkbenchDTO.MemberStreamVO;
import com.mimo.dto.WorkbenchDTO.TeamWorkbenchVO;
import com.mimo.dto.WorkbenchDTO.WorkbenchVO;
import com.mimo.entity.ActivityLog;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Issue;
import com.mimo.entity.Project;
import com.mimo.entity.Sprint;
import com.mimo.entity.Team;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.entity.WorkLog;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.IssueChangeRequestMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WorkLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceTest {

    /**
     * LambdaQueryWrapper 生成条件时要用实体元数据（字段名 ↔ 列名）。
     * 纯 Mockito 用例没有 MyBatis 上下文，得手动灌一次，否则抛
     * "can not find lambda cache for this entity"（同 RoleServiceTest 的做法）。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Issue.class);
        TableInfoHelper.initTableInfo(assistant, ActivityLog.class);
        TableInfoHelper.initTableInfo(assistant, BoardColumn.class);
    }

    @Mock
    private ActivityLogMapper activityLogMapper;
    @Mock
    private BoardColumnMapper boardColumnMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private IssueReviewMapper issueReviewMapper;
    @Mock
    private IssueChangeRequestMapper issueChangeRequestMapper;
    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkLogMapper workLogMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private IssueChangeRequestService issueChangeRequestService;
    @Mock
    private IssueReviewService issueReviewService;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private PresenceService presenceService;

    @Test
    void shouldReturnCrossProjectTasksRiskAndWeeklySummary() {
        ProjectVO accessibleProject = new ProjectVO();
        accessibleProject.setId(10L);
        accessibleProject.setName("Mimo");
        when(projectService.listByUser(9L)).thenReturn(List.of(accessibleProject));

        LocalDate today = LocalDate.now();
        Issue overdue = issue(1L, "TASK", "逾期联调", "HIGH", today.minusDays(2), 10L, 20L);
        Issue dueToday = issue(2L, "STORY", "今日评审", "MEDIUM", today, 10L, 20L);
        Issue blocked = issue(3L, "TASK", "依赖未完成", "LOW", today.plusDays(3), 10L, 20L);
        blocked.setDependencies("[4]");
        Issue dependency = issue(4L, "TASK", "前置任务", "HIGH", today.plusDays(1), 10L, 20L);
        Issue completed = issue(5L, "TASK", "本周完成", "MEDIUM", today.minusDays(1), 10L, 20L);
        completed.setStatus("DONE");
        completed.setUpdatedAt(LocalDateTime.now());
        when(issueMapper.selectList(any()))
                .thenReturn(List.of(blocked, completed, dueToday, overdue));
        when(issueMapper.selectBatchIds(any())).thenReturn(List.of(dependency));

        Project project = new Project();
        project.setId(10L);
        project.setName("Mimo");
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project));

        Sprint sprint = new Sprint();
        sprint.setId(20L);
        sprint.setName("Sprint 12");
        sprint.setProjectId(10L);
        sprint.setStatus("ACTIVE");
        sprint.setIsActive(1);
        when(sprintMapper.selectBatchIds(any())).thenReturn(List.of(sprint));

        WorkLog firstLog = workLog(1L, new BigDecimal("1.50"));
        WorkLog secondLog = workLog(5L, new BigDecimal("2.00"));
        when(workLogMapper.selectList(any())).thenReturn(List.of(firstLog, secondLog));
        when(issueChangeRequestService.listPendingForUser(9L)).thenReturn(List.of());
        when(issueReviewService.listPendingForUser(9L)).thenReturn(List.of());
        when(issueChangeRequestMapper.selectList(any())).thenReturn(List.of());
        when(issueReviewMapper.selectList(any())).thenReturn(List.of());

        WorkbenchVO result = service().getMyWorkbench(9L);

        assertThat(result.getTasks()).extracting("id").containsExactly(1L, 2L, 3L);
        assertThat(result.getOverdue()).extracting("id").containsExactly(1L);
        assertThat(result.getDueToday()).extracting("id").containsExactly(2L);
        assertThat(result.getTasks().get(0).getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getTasks().get(0).getRiskReasons())
                .anyMatch(reason -> reason.contains("已逾期 2 天"));
        assertThat(result.getTasks().get(2).getRiskReasons())
                .anyMatch(reason -> reason.contains("前置工作项"));
        assertThat(result.getWeeklySummary().getWorkHours())
                .isEqualByComparingTo("3.50");
        assertThat(result.getWeeklySummary().getCompletedTaskCount()).isEqualTo(1);
        assertThat(result.getWeeklySummary().getActiveSprintCount()).isEqualTo(1);
        assertThat(result.getWeeklySummary().getActiveSprints()).hasSize(1);
    }

    @Test
    void shouldReturnClearEmptyStateForUserWithoutProjects() {
        when(projectService.listByUser(99L)).thenReturn(List.of());

        WorkbenchVO result = service().getMyWorkbench(99L);

        assertThat(result.getTasks()).isEmpty();
        assertThat(result.getDueToday()).isEmpty();
        assertThat(result.getOverdue()).isEmpty();
        assertThat(result.getPendingChangeReviews()).isEmpty();
        assertThat(result.getPendingTaskReviews()).isEmpty();
        assertThat(result.getRejectedItems()).isEmpty();
        assertThat(result.getWeeklySummary().getWorkHours()).isEqualByComparingTo("0");
    }

    @Test
    void shouldShowPendingReviewsFromProjectsImNotIn() {
        // 系统管理员 / 团队管理员常常不在项目成员表里，但按 IssueReviewService 的规则
        // 他们是有资格验收的人 —— 工作台必须把这条待验收显示出来，否则"能批但看不到"。
        when(projectService.listByUser(1L)).thenReturn(List.of());

        Issue issue = issue(31L, "TASK", "外部项目的待验收任务", "HIGH",
                LocalDate.now().plusDays(3), 77L, null);
        when(issueReviewService.listPendingForUser(1L)).thenReturn(List.of(review(5L, 31L, 18L)));
        when(issueMapper.selectBatchIds(any())).thenReturn(List.of(issue));
        when(projectMapper.selectById(77L)).thenReturn(project(77L, "别人的项目"));

        WorkbenchVO result = service().getMyWorkbench(1L);

        assertThat(result.getPendingTaskReviews()).hasSize(1);
        assertThat(result.getPendingTaskReviews().get(0).getIssueTitle())
                .isEqualTo("外部项目的待验收任务");
        assertThat(result.getPendingTaskReviews().get(0).getProjectName())
                .isEqualTo("别人的项目");
    }

    // ==================== 管理员视角：全员任务流（v2.19.8） ====================

    @Test
    void teamWorkbenchGroupsByMemberAndFlagsOverdue() {
        // 系统管理员：范围是全部团队
        when(accessControlService.isSystemAdmin(1L)).thenReturn(true);
        when(teamMapper.selectList(any())).thenReturn(List.of(team(1L, "Mimo 团队")));
        when(teamMemberMapper.selectList(any())).thenReturn(List.of(
                teamMember(1L, 10L, "ROLE_MEMBER"),
                teamMember(1L, 11L, "ROLE_ADMIN")));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(10L, "zhangsan", "ROLE_MEMBER"),
                user(11L, "lisi", "ROLE_ADMIN")));
        when(projectMapper.selectList(any())).thenReturn(List.of(teamProject(100L, 1L)));

        LocalDate today = LocalDate.now();
        Issue zhangsanOverdue = issue(1L, "TASK", "逾期两天", "HIGH", today.minusDays(2), 100L, null);
        zhangsanOverdue.setAssigneeId(10L);
        Issue zhangsanTodo = issue(2L, "TASK", "待开始", "LOW", today.plusDays(3), 100L, null);
        zhangsanTodo.setAssigneeId(10L);
        Issue lisiDoing = issue(3L, "STORY", "进行中的故事", "MEDIUM", today.plusDays(2), 100L, null);
        lisiDoing.setAssigneeId(11L);
        lisiDoing.setStatus("IN_PROGRESS");
        Issue lisiDone = issue(4L, "TASK", "上周就做完了", "MEDIUM", today.minusDays(5), 100L, null);
        lisiDone.setAssigneeId(11L);
        lisiDone.setStatus("DONE");
        Issue nobody = issue(5L, "BUG", "没人认领的缺陷", "HIGHEST", today.plusDays(1), 100L, null);
        when(issueMapper.selectList(any())).thenReturn(
                List.of(zhangsanOverdue, zhangsanTodo, lisiDoing, lisiDone, nobody));
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(teamProject(100L, 1L)));
        when(presenceService.filterOnline(any())).thenReturn(Set.of(11L));

        TeamWorkbenchVO result = service().getTeamWorkbench(1L);

        assertThat(result.getScope()).isEqualTo("SYSTEM");
        assertThat(result.getTeamIds()).containsExactly(1L);
        assertThat(result.getMembers()).extracting(MemberStreamVO::getUsername)
                .containsExactly("zhangsan", "lisi");

        MemberStreamVO zhang = result.getMembers().get(0);
        assertThat(zhang.getOpenCount()).isEqualTo(2);
        assertThat(zhang.getOverdueTaskCount()).isEqualTo(1);
        assertThat(zhang.getLoadLevel()).isEqualTo("BUSY");
        assertThat(zhang.getLoadReason()).contains("1 项逾期");
        // 逾期任务必须排在这个人的第一位，别让管理员自己找
        assertThat(zhang.getTasks()).extracting("id").containsExactly(1L, 2L);
        assertThat(zhang.getOnline()).isFalse();

        MemberStreamVO li = result.getMembers().get(1);
        assertThat(li.getAdmin()).isTrue();
        assertThat(li.getOnline()).isTrue();
        assertThat(li.getDoneCount()).isEqualTo(1);
        assertThat(li.getInProgressCount()).isEqualTo(1);

        // 未指派的单独统计——这是管理员最该先处理的一类
        assertThat(result.getSummary().getUnassignedTaskCount()).isEqualTo(1);
        assertThat(result.getSummary().getUnassignedTasks().get(0).getIssueKey()).isEqualTo("MIMO-5");
        assertThat(result.getSummary().getOpenTaskCount()).isEqualTo(4);
        assertThat(result.getSummary().getDoneTaskCount()).isEqualTo(1);
        assertThat(result.getSummary().getOverdueTaskCount()).isEqualTo(1);
        assertThat(result.getSummary().getBusyMemberCount()).isEqualTo(2);
        assertThat(result.getSummary().getOnlineMemberCount()).isEqualTo(1);
    }

    @Test
    void teamWorkbenchKeepsMembersWithoutTasks() {
        // 空闲的人也是信息：如果前端只能看到"有任务的人"，管理员会以为团队只有三个人
        when(accessControlService.isSystemAdmin(1L)).thenReturn(true);
        when(teamMapper.selectList(any())).thenReturn(List.of(team(1L, "Mimo 团队")));
        when(teamMemberMapper.selectList(any())).thenReturn(List.of(
                teamMember(1L, 10L, "ROLE_MEMBER"),
                teamMember(1L, 12L, "ROLE_MEMBER")));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(10L, "zhangsan", "ROLE_MEMBER"),
                user(12L, "wangwu", "ROLE_MEMBER")));
        when(projectMapper.selectList(any())).thenReturn(List.of(teamProject(100L, 1L)));
        Issue busy = issue(1L, "TASK", "只有他有活", "MEDIUM", LocalDate.now().plusDays(2), 100L, null);
        busy.setAssigneeId(10L);
        when(issueMapper.selectList(any())).thenReturn(List.of(busy));
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(teamProject(100L, 1L)));
        when(presenceService.filterOnline(any())).thenReturn(Set.of());

        TeamWorkbenchVO result = service().getTeamWorkbench(1L);

        assertThat(result.getMembers()).hasSize(2);
        MemberStreamVO idle = result.getMembers().stream()
                .filter(member -> "wangwu".equals(member.getUsername()))
                .findFirst().orElseThrow();
        assertThat(idle.getOpenCount()).isZero();
        assertThat(idle.getLoadLevel()).isEqualTo("IDLE");
        assertThat(idle.getLoadReason()).contains("没有未完成任务");
        assertThat(result.getSummary().getBusyMemberCount()).isEqualTo(1);
    }

    @Test
    void teamWorkbenchRejectsMemberWhoManagesNoTeam() {
        when(accessControlService.isSystemAdmin(5L)).thenReturn(false);
        when(teamMapper.selectList(any())).thenReturn(List.of(team(1L, "别人的团队")));
        when(accessControlService.isTeamAdmin(5L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service().getTeamWorkbench(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有管理员能查看全员任务流");
    }

    @Test
    void teamWorkbenchScopesToManagedTeamsForTeamAdmin() {
        // 团队管理员只看自己管理的团队：团队 2 不在他名下，成员也不该出现
        when(accessControlService.isSystemAdmin(1L)).thenReturn(false);
        when(teamMapper.selectList(any())).thenReturn(List.of(
                team(1L, "我管理的团队"),
                team(2L, "别人的团队")));
        when(accessControlService.isTeamAdmin(1L, 1L)).thenReturn(true);
        when(accessControlService.isTeamAdmin(1L, 2L)).thenReturn(false);
        when(teamMemberMapper.selectList(any())).thenReturn(List.of(teamMember(1L, 10L, "ROLE_MEMBER")));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(10L, "zhangsan", "ROLE_MEMBER")));
        when(projectMapper.selectList(any())).thenReturn(List.of(teamProject(100L, 1L)));
        when(issueMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(teamProject(100L, 1L)));
        when(presenceService.filterOnline(any())).thenReturn(Set.of());

        TeamWorkbenchVO result = service().getTeamWorkbench(1L);

        assertThat(result.getScope()).isEqualTo("TEAM");
        assertThat(result.getTeamIds()).containsExactly(1L);
        assertThat(result.getTeamNames()).containsExactly("我管理的团队");
    }

    // ==================== v2.20.1：筛选 / 排序 / 分页 / 趋势 ====================

    @Test
    void teamWorkbenchPaginatesAfterSortingSoFirstPageIsTheBusiest() {
        // 5 个成员，每页 2 人：第 1 页必须是"最该管的两个人"，不是按 id 排的头两个
        stubSystemAdminWithMembers(5);
        Issue many = issue(900L, "TASK", "最忙的人的第一件事", "HIGH", LocalDate.now().minusDays(3), 100L, null);
        many.setAssigneeId(10L);
        Issue manyToo = issue(901L, "TASK", "最忙的人的第二件事", "LOW", LocalDate.now(), 100L, null);
        manyToo.setAssigneeId(10L);
        Issue some = issue(902L, "TASK", "第二忙的任务", "MEDIUM", LocalDate.now(), 100L, null);
        some.setAssigneeId(11L);
        when(issueMapper.selectList(any())).thenReturn(List.of(many, manyToo, some));

        TeamWorkbenchVO page1 = service().getTeamWorkbench(1L, 1, 2, 6, null, "all", "load");

        assertThat(page1.getMembers()).hasSize(2);
        assertThat(page1.getMemberTotal()).isEqualTo(5);
        assertThat(page1.getMemberTotalAll()).isEqualTo(5);
        assertThat(page1.getMemberPageCount()).isEqualTo(3);
        assertThat(page1.getMembers().get(0).getUserId()).isEqualTo(10L);
        assertThat(page1.getMembers().get(0).getOpenCount()).isEqualTo(2);

        TeamWorkbenchVO page3 = service().getTeamWorkbench(1L, 3, 2, 6, null, "all", "load");
        assertThat(page3.getMembers()).hasSize(1);

        // 页码超出上限时夹到最后一页，不返回空白页
        TeamWorkbenchVO clamped = service().getTeamWorkbench(1L, 99, 2, 6, null, "all", "load");
        assertThat(clamped.getMemberPage()).isEqualTo(3);
        assertThat(clamped.getMembers()).hasSize(1);
    }

    @Test
    void teamWorkbenchCapsTasksPerMemberButCountsStayedFull() {
        // 抓过的坑：被截断的列表拿去算计数，负载标签就会说谎
        stubSystemAdminWithMembers(1);
        List<Issue> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Issue issue = issue(800L + i, "TASK", "任务 " + i, "MEDIUM",
                    LocalDate.now().plusDays(i), 100L, null);
            issue.setAssigneeId(10L);
            tasks.add(issue);
        }
        when(issueMapper.selectList(any())).thenReturn(tasks);

        TeamWorkbenchVO result = service().getTeamWorkbench(1L, 1, 8, 3, null, "all", "load");

        MemberStreamVO only = result.getMembers().get(0);
        assertThat(only.getTasks()).hasSize(3);
        assertThat(only.getTaskTotal()).isEqualTo(8);
        assertThat(only.getOpenCount()).isEqualTo(8);
        assertThat(result.getTaskLimit()).isEqualTo(3);
    }

    @Test
    void teamWorkbenchFiltersAndSortsOnServer() {
        stubSystemAdminWithMembers(3);
        Issue overdue = issue(700L, "TASK", "逾期活", "HIGH", LocalDate.now().minusDays(2), 100L, null);
        overdue.setAssigneeId(10L);
        when(issueMapper.selectList(any())).thenReturn(List.of(overdue));

        // 只有 10 号有逾期任务，11/12 号空着
        TeamWorkbenchVO overdueOnly = service().getTeamWorkbench(1L, 1, 8, 6, null, "overdue", "overdue");
        assertThat(overdueOnly.getMembers()).extracting(MemberStreamVO::getUserId).containsExactly(10L);
        assertThat(overdueOnly.getMemberTotal()).isEqualTo(1);
        assertThat(overdueOnly.getMemberTotalAll()).isEqualTo(3);

        TeamWorkbenchVO idleOnly = service().getTeamWorkbench(1L, 1, 8, 6, null, "idle", "name");
        assertThat(idleOnly.getMembers()).hasSize(2);
        assertThat(idleOnly.getMemberTotal()).isEqualTo(2);

        // 关键词按用户名匹配
        TeamWorkbenchVO byName = service().getTeamWorkbench(1L, 1, 8, 6, "member11", "all", "name");
        assertThat(byName.getMembers()).extracting(MemberStreamVO::getUsername).containsExactly("member11");

        // 概览统计不受筛选影响：筛"空闲"不该让"未完成"跟着变成 0
        assertThat(idleOnly.getSummary().getMemberCount()).isEqualTo(3);
        assertThat(idleOnly.getSummary().getOpenTaskCount()).isEqualTo(1);
    }

    @Test
    void teamWorkbenchTrendCountsOnlyRealMoveIntoTerminalColumn() {
        // 趋势必须来自活动日志：新建 = CREATE，完成 = MOVE 且目标列是终态列。
        // 用 updated_at 近似的话，"改个标题"会被算成完成。
        stubSystemAdminWithMembers(1);
        when(issueMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime now = LocalDateTime.now();
        when(activityLogMapper.selectList(any())).thenReturn(List.of(
                activityLog(100L, "CREATE", "创建了 MIMO-1", now),
                activityLog(100L, "MOVE", "将 MIMO-1 从 待办 移动到 完成", now),
                activityLog(100L, "MOVE", "将 MIMO-2 从 待办 移动到 进行中", now),
                activityLog(100L, "UPDATE", "改了标题", now)));
        // 项目的列一次取回：完成算终态，进行中不算（判定交给 BoardColumnRules，不靠 SQL 过滤）
        BoardColumn done = new BoardColumn();
        done.setProjectId(100L);
        done.setName("完成");
        BoardColumn doing = new BoardColumn();
        doing.setProjectId(100L);
        doing.setName("进行中");
        when(boardColumnMapper.selectList(any())).thenReturn(List.of(done, doing));

        TeamWorkbenchVO result = service().getTeamWorkbench(1L, 1, 8, 6, null, "all", "overdue");

        assertThat(result.getTrend()).hasSize(7);
        assertThat(result.getTrend().get(6).getDate()).isEqualTo(LocalDate.now().toString());
        assertThat(result.getTrend().get(6).getCreated()).isEqualTo(1);
        assertThat(result.getTrend().get(6).getDone()).isEqualTo(1);
        assertThat(result.getTrendNote()).contains("活动日志");
    }

    @Test
    void trendTargetColumnParsingIgnoresUnknownDetails() {
        assertThat(WorkbenchService.targetColumnName("将 MIMO-1 从 待办 移动到 完成")).isEqualTo("完成");
        assertThat(WorkbenchService.targetColumnName("将 MIMO-1 从 待办 移动到进行中")).isEqualTo("进行中");
        assertThat(WorkbenchService.targetColumnName("随手改了个标题")).isNull();
        assertThat(WorkbenchService.targetColumnName(null)).isNull();
    }

    private void stubSystemAdminWithMembers(int memberCount) {
        when(accessControlService.isSystemAdmin(1L)).thenReturn(true);
        when(teamMapper.selectList(any())).thenReturn(List.of(team(1L, "Mimo 团队")));
        List<TeamMember> members = new java.util.ArrayList<>();
        List<User> users = new java.util.ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            members.add(teamMember(1L, 10L + i, "ROLE_MEMBER"));
            users.add(user(10L + i, "member1" + i, "ROLE_MEMBER"));
        }
        when(teamMemberMapper.selectList(any())).thenReturn(members);
        when(userMapper.selectBatchIds(any())).thenReturn(users);
        when(projectMapper.selectList(any())).thenReturn(List.of(teamProject(100L, 1L)));
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(teamProject(100L, 1L)));
        when(presenceService.filterOnline(any())).thenReturn(Set.of());
    }

    private ActivityLog activityLog(Long projectId, String action, String detail, LocalDateTime createdAt) {
        ActivityLog log = new ActivityLog();
        log.setProjectId(projectId);
        log.setTargetType("ISSUE");
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(createdAt);
        return log;
    }

    private Team team(Long id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setDeleted(0);
        return team;
    }

    private TeamMember teamMember(Long teamId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@mimo.dev");
        user.setRole(role);
        user.setDeleted(0);
        return user;
    }

    private Project teamProject(Long id, Long teamId) {
        Project project = new Project();
        project.setId(id);
        project.setName("项目 " + id);
        project.setTeamId(teamId);
        project.setDeleted(0);
        return project;
    }

    private com.mimo.dto.IssueGovernanceDTO.ReviewVO review(Long id, Long issueId, Long submitterId) {        com.mimo.dto.IssueGovernanceDTO.ReviewVO review = new com.mimo.dto.IssueGovernanceDTO.ReviewVO();
        review.setId(id);
        review.setIssueId(issueId);
        review.setRoundNo(1);
        review.setSubmitterId(submitterId);
        review.setSubmitterName("chain");
        review.setStatus("PENDING");
        review.setCompletionNote("做完了");
        review.setSubmittedAt(LocalDateTime.now());
        return review;
    }

    private Project project(Long id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }

    private WorkbenchService service() {
        return new WorkbenchService(issueMapper, issueReviewMapper, issueChangeRequestMapper,
                sprintMapper, projectMapper, workLogMapper, projectService,
                issueChangeRequestService, issueReviewService, new ObjectMapper(),
                teamMapper, teamMemberMapper, userMapper, activityLogMapper, boardColumnMapper,
                accessControlService, presenceService);
    }

    private Issue issue(Long id, String type, String title, String priority,
                        LocalDate dueDate, Long projectId, Long sprintId) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setIssueKey("MIMO-" + id);
        issue.setType(type);
        issue.setTitle(title);
        issue.setPriority(priority);
        issue.setStatus("TODO");
        issue.setDueDate(dueDate);
        issue.setProjectId(projectId);
        issue.setSprintId(sprintId);
        issue.setStoryPoints(3);
        return issue;
    }

    private WorkLog workLog(Long issueId, BigDecimal hours) {
        WorkLog log = new WorkLog();
        log.setIssueId(issueId);
        log.setHours(hours);
        log.setWorkDate(LocalDate.now());
        return log;
    }
}
