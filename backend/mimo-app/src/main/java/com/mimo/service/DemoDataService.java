package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.entity.ActivityLog;
import com.mimo.entity.Attachment;
import com.mimo.entity.BurndownSnapshot;
import com.mimo.entity.DeliveryDailySnapshot;
import com.mimo.entity.IssueChangeRequest;
import com.mimo.entity.IssueLabel;
import com.mimo.entity.IssueVersion;
import com.mimo.entity.WikiAttachment;
import com.mimo.entity.WikiPage;
import com.mimo.entity.WikiVersion;
import com.mimo.entity.BoardColumn;
import com.mimo.entity.Comment;
import com.mimo.entity.Issue;
import com.mimo.entity.IssueReview;
import com.mimo.entity.Notification;
import com.mimo.entity.Project;
import com.mimo.entity.ProjectMember;
import com.mimo.entity.Report;
import com.mimo.entity.Sprint;
import com.mimo.entity.Team;
import com.mimo.entity.TeamMember;
import com.mimo.entity.User;
import com.mimo.entity.WorkLog;
import com.mimo.entity.Workflow;
import com.mimo.mapper.ActivityLogMapper;
import com.mimo.mapper.AttachmentMapper;
import com.mimo.mapper.BurndownSnapshotMapper;
import com.mimo.mapper.DeliveryDailySnapshotMapper;
import com.mimo.mapper.IssueChangeRequestMapper;
import com.mimo.mapper.IssueLabelMapper;
import com.mimo.mapper.IssueVersionMapper;
import com.mimo.mapper.WikiAttachmentMapper;
import com.mimo.mapper.WikiPageMapper;
import com.mimo.mapper.WikiVersionMapper;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.CommentMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.IssueReviewMapper;
import com.mimo.mapper.NotificationMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.ProjectMemberMapper;
import com.mimo.mapper.ReportMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.TeamMapper;
import com.mimo.mapper.TeamMemberMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WorkLogMapper;
import com.mimo.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 演示数据生成器（KNOWN-020）。
 *
 * <p>起因：空库只有 3 个用户 1 个团队，工作台、Sprint 健康度、交付分析、审计日志全是 0，
 * 既没法体验也没法演示 —— 看到的是"一堆 0"，很容易被当成"这功能没做完"。
 *
 * <p>做法：一次性生成一个自洽的小项目（团队 → 项目 → 看板列 → 迭代 → 工作项 → 评论 / 工时 /
 * 验收 / 报告 / 动态 / 通知），让每个页面都有东西可看。<b>幂等</b>：已经生成过就直接返回现状，
 * 不重复堆数据；只有系统管理员能调。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DemoDataService {

    /** 生成的演示项目用它做标记，用来判断"是否已生成过" */
    public static final String DEMO_PROJECT_NAME = "示例项目：移动端改版";

    private static final String[] COLUMN_NAMES = {"待办", "进行中", "待验收", "已完成"};
    private static final String[] COLUMN_COLORS = {"#909399", "#409EFF", "#E6A23C", "#67C23A"};

    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final IssueKeyAllocator issueKeyAllocator;
    private final CommentMapper commentMapper;
    private final WorkLogMapper workLogMapper;
    private final IssueReviewMapper issueReviewMapper;
    private final ReportMapper reportMapper;
    private final ActivityLogMapper activityLogMapper;
    private final NotificationMapper notificationMapper;
    private final WorkflowMapper workflowMapper;
    private final IssueVersionMapper issueVersionMapper;
    private final IssueChangeRequestMapper issueChangeRequestMapper;
    private final AttachmentMapper attachmentMapper;
    private final IssueLabelMapper issueLabelMapper;
    private final BurndownSnapshotMapper burndownSnapshotMapper;
    private final DeliveryDailySnapshotMapper deliveryDailySnapshotMapper;
    private final WikiPageMapper wikiPageMapper;
    private final WikiVersionMapper wikiVersionMapper;
    private final WikiAttachmentMapper wikiAttachmentMapper;
    private final AccessControlService accessControlService;

    /** 现在有没有演示数据 */
    public Map<String, Object> status() {
        Project project = findDemoProject();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", project != null);
        if (project != null) {
            result.put("projectId", project.getId());
            result.put("issueCount", issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, project.getId()).eq(Issue::getDeleted, 0)));
        }
        return result;
    }

    /**
     * 清掉示例数据（v2.19.5）。
     *
     * <p>生成出来的东西必须能收回去：否则演示完要一条条手删，或者干脆留一堆假数据混在真数据里。
     * 删除范围就是<b>生成器自己造的那些</b>——示例项目 + 它的工作项及其子记录 + 自动生成的工作流，
     * 以及"示例团队"（只在这台机器上除了示例项目没别的项目时才删）。
     *
     * <p>不碰：其它项目、其它团队、用户账号、公告、Wiki 之外的任何全局数据。
     */
    @Transactional
    public Map<String, Object> clear(Long operatorId) {
        if (!accessControlService.isSystemAdmin(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有系统管理员可以清除演示数据");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Project project = findDemoProject();
        if (project == null) {
            result.put("cleared", false);
            result.put("message", "当前没有示例数据，不用清");
            return result;
        }

        Long projectId = project.getId();
        Long teamId = project.getTeamId();
        // 先取 id 集合，再删子表：主表删掉之后子表就找不到归属了
        List<Long> issueIds = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId))
                .stream().map(Issue::getId).toList();
        List<Long> sprintIds = sprintMapper.selectList(new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId))
                .stream().map(Sprint::getId).toList();

        int comments = 0;
        int workLogs = 0;
        int reviews = 0;
        if (!issueIds.isEmpty()) {
            comments = commentMapper.delete(new LambdaQueryWrapper<Comment>()
                    .in(Comment::getIssueId, issueIds));
            workLogs = workLogMapper.delete(new LambdaQueryWrapper<WorkLog>()
                    .in(WorkLog::getIssueId, issueIds));
            reviews = issueReviewMapper.delete(new LambdaQueryWrapper<IssueReview>()
                    .in(IssueReview::getIssueId, issueIds));
            notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                    .in(Notification::getRelatedId, issueIds)
                    .eq(Notification::getRelatedType, "ISSUE"));
            // 工作项的其它子记录：不清就是一堆指向不存在工作项的孤儿数据
            // （验收 / 变更审批的申请、版本快照、附件、标签）
            issueVersionMapper.delete(new LambdaQueryWrapper<IssueVersion>()
                    .in(IssueVersion::getIssueId, issueIds));
            issueChangeRequestMapper.delete(new LambdaQueryWrapper<IssueChangeRequest>()
                    .in(IssueChangeRequest::getIssueId, issueIds));
            attachmentMapper.delete(new LambdaQueryWrapper<Attachment>()
                    .in(Attachment::getIssueId, issueIds));
            issueLabelMapper.delete(new LambdaQueryWrapper<IssueLabel>()
                    .in(IssueLabel::getIssueId, issueIds));
        }

        // 迭代维度的燃尽快照（看过燃尽图就会产生）
        if (!sprintIds.isEmpty()) {
            burndownSnapshotMapper.delete(new LambdaQueryWrapper<BurndownSnapshot>()
                    .in(BurndownSnapshot::getSprintId, sprintIds));
        }

        // 项目级：工作流、动态、报告、交付快照、Wiki、成员、看板列、迭代、工作项、项目本体
        workflowMapper.delete(new LambdaQueryWrapper<Workflow>().eq(Workflow::getProjectId, projectId));
        deliveryDailySnapshotMapper.delete(new LambdaQueryWrapper<DeliveryDailySnapshot>()
                .eq(DeliveryDailySnapshot::getProjectId, projectId));
        List<Long> wikiPageIds = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getProjectId, projectId))
                .stream().map(WikiPage::getId).toList();
        if (!wikiPageIds.isEmpty()) {
            wikiVersionMapper.delete(new LambdaQueryWrapper<WikiVersion>()
                    .in(WikiVersion::getPageId, wikiPageIds));
            wikiAttachmentMapper.delete(new LambdaQueryWrapper<WikiAttachment>()
                    .in(WikiAttachment::getPageId, wikiPageIds));
            wikiPageMapper.delete(new LambdaQueryWrapper<WikiPage>()
                    .eq(WikiPage::getProjectId, projectId));
        }
        activityLogMapper.delete(new LambdaQueryWrapper<ActivityLog>()
                .eq(ActivityLog::getProjectId, projectId));
        int reports = reportMapper.delete(new LambdaQueryWrapper<Report>()
                .eq(Report::getProjectId, projectId));
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId));
        boardColumnMapper.delete(new LambdaQueryWrapper<BoardColumn>()
                .eq(BoardColumn::getProjectId, projectId));
        sprintMapper.delete(new LambdaQueryWrapper<Sprint>().eq(Sprint::getProjectId, projectId));
        int issues = issueMapper.delete(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId));
        projectMapper.deleteById(projectId);

        // 团队：只有"除了这个示例项目没别的项目"时才删，避免连坐用户自己的团队
        boolean teamDeleted = false;
        if (teamId != null) {
            long remaining = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                    .eq(Project::getTeamId, teamId));
            Team team = teamMapper.selectById(teamId);
            boolean autoTeam = team != null && "示例团队".equals(team.getName());
            if (remaining == 0 && autoTeam) {
                teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId));
                teamMapper.deleteById(teamId);
                teamDeleted = true;
            }
        }

        log.info("清除示例数据：projectId={}, 工作项={}, 评论={}, 工时={}, 验收={}, 报告={}, 团队删除={}",
                projectId, issues, comments, workLogs, reviews, reports, teamDeleted);

        result.put("cleared", true);
        result.put("projectId", projectId);
        result.put("issueCount", issues);
        result.put("commentCount", comments);
        result.put("workLogCount", workLogs);
        result.put("reviewCount", reviews);
        result.put("reportCount", reports);
        result.put("sprintCount", sprintIds.size());
        result.put("teamDeleted", teamDeleted);
        result.put("message", "示例数据已清除");
        return result;
    }

    @Transactional
    public Map<String, Object> generate(Long operatorId) {
        if (!accessControlService.isSystemAdmin(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有系统管理员可以生成演示数据");
        }
        Project existing = findDemoProject();
        if (existing != null) {
            Map<String, Object> result = status();
            result.put("created", false);
            result.put("message", "演示数据已经生成过了，下面这些页面现在应该都有内容");
            return result;
        }

        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getUsername() : "admin";

        // 1) 团队：优先复用操作人已有团队，没有就建一个
        Team team = ensureTeam(operatorId);
        List<Long> memberIds = teamMemberIds(team.getId());
        List<Long> actors = memberIds.isEmpty() ? List.of(operatorId) : memberIds;

        // 2) 项目
        Project project = new Project();
        project.setName(DEMO_PROJECT_NAME);
        // projects 的 key 是全局唯一（uk_project_key），不能硬写 DEMO —— demo 项目本身可能就占着
        project.setKey(allocateProjectKey());
        project.setDescription("一键生成的示例项目：用来把工作台、看板、迭代、报表这些页面填满，方便演示和试用");
        project.setTemplate("SCRUM");
        project.setTeamId(team.getId());
        project.setOwnerId(operatorId);
        project.setDeleted(0);
        projectMapper.insert(project);

        for (Long uid : actors) {
            if (projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, project.getId())
                    .eq(ProjectMember::getUserId, uid)) > 0) {
                continue;
            }
            ProjectMember pm = new ProjectMember();
            pm.setProjectId(project.getId());
            pm.setUserId(uid);
            pm.setRole(Objects.equals(uid, operatorId) ? "ROLE_ADMIN" : "ROLE_MEMBER");
            pm.setJoinedAt(LocalDateTime.now());
            projectMemberMapper.insert(pm);
        }

        // 3) 看板列
        List<BoardColumn> columns = new ArrayList<>();
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            BoardColumn column = new BoardColumn();
            column.setProjectId(project.getId());
            column.setName(COLUMN_NAMES[i]);
            column.setSortOrder(i);
            column.setColor(COLUMN_COLORS[i]);
            boardColumnMapper.insert(column);
            columns.add(column);
        }

        // 4) 迭代：一个进行中、一个规划中、一个已完成
        LocalDate today = LocalDate.now();
        Sprint active = sprint(project.getId(), "Sprint 1 · 登录与首页", "把登录链路和首页骨架做完",
                today.minusDays(6), today.plusDays(7), 1, "ACTIVE");
        Sprint planning = sprint(project.getId(), "Sprint 2 · 消息中心", "消息列表、未读数、详情页",
                today.plusDays(8), today.plusDays(21), 0, "PLANNING");
        Sprint done = sprint(project.getId(), "Sprint 0 · 项目启动", "环境搭建与规范约定",
                today.minusDays(20), today.minusDays(7), 0, "COMPLETED");

        // 5) 工作项：覆盖四种状态 / 三种类型 / 逾期与临期
        List<Issue> issues = new ArrayList<>();
        issues.add(issue(project, columns.get(0), active, "STORY", "登录页支持手机号 + 验证码",
                "HIGH", "TODO", actors, 0, today.plusDays(2), 5, operatorId));
        issues.add(issue(project, columns.get(0), active, "TASK", "梳理现有登录接口的返回结构",
                "MEDIUM", "TODO", actors, 1, today.plusDays(4), 2, operatorId));
        issues.add(issue(project, columns.get(0), active, "BUG", "验证码倒计时在 iOS Safari 上不刷新",
                "HIGH", "TODO", actors, 2, today.plusDays(1), 3, operatorId));
        issues.add(issue(project, columns.get(0), planning, "STORY", "消息列表支持按未读筛选",
                "MEDIUM", "TODO", actors, 3, today.plusDays(12), 3, operatorId));
        issues.add(issue(project, columns.get(0), null, "TASK", "整理发布检查清单",
                "LOW", "TODO", actors, 4, today.plusDays(9), 2, operatorId));

        issues.add(issue(project, columns.get(1), active, "STORY", "首页信息流骨架与骨架屏",
                "HIGH", "IN_PROGRESS", actors, 0, today.plusDays(3), 8, operatorId));
        issues.add(issue(project, columns.get(1), active, "TASK", "接入埋点上报",
                "MEDIUM", "IN_PROGRESS", actors, 1, today.minusDays(1), 3, operatorId)); // 逾期
        issues.add(issue(project, columns.get(1), active, "BUG", "列表快速滑动时图片错位",
                "HIGHEST", "IN_PROGRESS", actors, 2, today, 5, operatorId));
        issues.add(issue(project, columns.get(1), active, "TASK", "补充登录接口单元测试",
                "MEDIUM", "IN_PROGRESS", actors, 0, today.plusDays(5), 3, operatorId));
        issues.add(issue(project, columns.get(1), planning, "STORY", "消息详情页面设计与实现",
                "MEDIUM", "IN_PROGRESS", actors, 1, today.plusDays(15), 8, operatorId));

        // 待验收：提交人是别人，验收人才是管理员 —— 这样工作台「待我处理」里就有东西
        Long reviewerCandidate = actors.size() > 1 ? actors.get(1) : operatorId;
        Issue pendingOne = issue(project, columns.get(2), active, "STORY", "登录页 UI 走查问题修复",
                "HIGH", "IN_PROGRESS", actors, 0, today.plusDays(1), 3, operatorId);
        issues.add(pendingOne);
        Issue pendingTwo = issue(project, columns.get(2), active, "TASK", "补充 README 与启动说明",
                "LOW", "IN_PROGRESS", actors, 1, today.plusDays(6), 1, operatorId);
        issues.add(pendingTwo);

        issues.add(issue(project, columns.get(3), done, "STORY", "项目脚手架与目录规范",
                "HIGH", "DONE", actors, 0, today.minusDays(9), 5, operatorId));
        issues.add(issue(project, columns.get(3), done, "TASK", "配置 CI 与代码检查",
                "MEDIUM", "DONE", actors, 1, today.minusDays(8), 2, operatorId));
        issues.add(issue(project, columns.get(3), done, "BUG", "打包后静态资源 404",
                "HIGH", "DONE", actors, 2, today.minusDays(6), 2, operatorId));

        for (Issue stored : issues) {
            issueKeyAllocator.insertWithAllocatedKey(stored, project.getKey());
        }

        // 6) 评论（挑几条有讨论的）
        comment(issues.get(0), actors.get(0), "验证码走短信通道还是邮件？我先按短信做。");
        comment(issues.get(0), actors.get(Math.min(1, actors.size() - 1)), "短信，先用测试号，正式号后面再申请。");
        comment(issues.get(6), actors.get(Math.min(1, actors.size() - 1)), "埋点字段名跟数据组对齐一下，别自己起。");
        comment(issues.get(7), actors.get(Math.min(2, actors.size() - 1)), "复现步骤：快速上滑两屏，第 5 张之后的图会跑到上一行。");
        comment(issues.get(10), actors.get(0), "UI 问题已按走查清单全部改完，截图在证据链接里。");

        // 7) 工时（最近 8 天）
        workLog(issues.get(5), actors.get(0), today.minusDays(2), "1.50", "首页骨架搭完");
        workLog(issues.get(5), actors.get(0), today.minusDays(1), "2.00", "接入信息流接口");
        workLog(issues.get(6), actors.get(Math.min(1, actors.size() - 1)), today.minusDays(1), "1.00", "埋点方案对齐");
        workLog(issues.get(7), actors.get(Math.min(2, actors.size() - 1)), today, "0.75", "定位错位问题");
        workLog(issues.get(3), actors.get(0), today.minusDays(3), "1.25", "消息列表需求澄清");
        workLog(issues.get(12), actors.get(0), today.minusDays(5), "3.00", "脚手架搭建");

        // 8) 验收：两条待验收（提交人≠管理员）+ 一条历史通过
        review(pendingOne, reviewerCandidate, "PENDING", "UI 问题已全部修复，麻烦验收", null);
        review(pendingTwo, reviewerCandidate, "PENDING", "README 补完了，从零能跑起来", null);
        review(issues.get(12), actors.get(Math.min(1, actors.size() - 1)), "APPROVED",
                "脚手架符合规范", "验收通过，可以开工");

        // 9) 报告：今天一份日报、本周一份周报
        report(operatorId, project.getId(), "DAILY", today, "今日完成：首页骨架、埋点方案对齐；明日：接口联调。");
        report(operatorId, project.getId(), "WEEKLY", today.minusDays(2), "本周完成 3 项、进行中 5 项、逾期 1 项。");

        // 10) 动态（审计日志页要有东西看）
        activity(operatorId, operatorName, project.getId(), "PROJECT", project.getId(), "CREATE", "创建项目：" + project.getName());
        activity(operatorId, operatorName, project.getId(), "SPRINT", active.getId(), "START", "启动 " + active.getName());
        activity(operatorId, operatorName, project.getId(), "ISSUE", issues.get(0).getId(), "CREATE", "创建工作项：" + issues.get(0).getTitle());
        activity(operatorId, operatorName, project.getId(), "ISSUE", issues.get(6).getId(), "UPDATE", "更新工作项：" + issues.get(6).getTitle());
        Long secondActor = actors.get(Math.min(1, actors.size() - 1));
        activity(secondActor, actorNameOr(actors, 1, operatorName), project.getId(), "ISSUE",
                issues.get(7).getId(), "COMMENT", "评论了工作项：" + issues.get(7).getTitle());

        // 11) 通知：给自己的两条
        notification(operatorId, "TASK_REVIEW_PENDING", "有工作项等你验收",
                issues.get(10).getIssueKey() + " " + pendingOne.getTitle() + " 已提交验收", pendingOne.getId());
        notification(reviewerCandidate, "TASK_ASSIGNED", "你有新的工作项",
                issues.get(0).getIssueKey() + " " + issues.get(0).getTitle(), issues.get(0).getId());

        log.info("演示数据生成完成：project={} issues={} team={}", project.getId(), issues.size(), team.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", true);
        result.put("message", "演示数据已生成，刷新一下就能看到");
        result.put("projectId", project.getId());
        result.put("teamId", team.getId());
        result.put("issueCount", issues.size());
        result.put("sprintCount", 3);
        result.put("commentCount", 5);
        result.put("workLogCount", 6);
        result.put("reviewCount", 3);
        result.put("reportCount", 2);
        return result;
    }

    // ==================== 内部构造 ====================

    private Project findDemoProject() {
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getName, DEMO_PROJECT_NAME)
                .eq(Project::getDeleted, 0)
                .last("limit 1"));
    }

    /**
     * 挑一个没被占用的项目 Key。
     *
     * <p>踩过的坑：`projects.key` 上有全局唯一索引（uk_project_key），第一版直接写死 "DEMO"，
     * 结果 demo 项目本身就叫 DEMO，一点就 500（Duplicate entry 'DEMO'）。
     * 所以这里先看现有 Key 再取，绝不硬编码。
     */
    String allocateProjectKey() {
        // 必须连"已被逻辑删除"的行一起看：唯一索引不看 deleted 标记，
        // 只查未删除的行会导致清除后再生成时重复挑到同一个 Key（v2.19.5 的 500 就这么来的）
        List<String> taken = projectMapper.selectAllKeysIncludingDeleted().stream()
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .toList();
        for (int i = 1; i <= 50; i++) {
            String candidate = i == 1 ? "DEMO" : "DEMO" + i;
            if (!taken.contains(candidate)) return candidate;
        }
        return "DM" + (System.currentTimeMillis() % 100000);
    }

    private Team ensureTeam(Long operatorId) {
        TeamMember mine = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, operatorId).last("limit 1"));
        if (mine != null) {
            Team team = teamMapper.selectById(mine.getTeamId());
            if (team != null && !Objects.equals(team.getDeleted(), 1)) return team;
        }
        Team team = new Team();
        team.setName("示例团队");
        team.setDescription("演示数据用的团队");
        team.setOwnerId(operatorId);
        team.setDeleted(0);
        teamMapper.insert(team);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(operatorId);
        member.setRole("ROLE_ADMIN");
        member.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(member);
        return team;
    }

    private List<Long> teamMemberIds(Long teamId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)).stream()
                .map(TeamMember::getUserId)
                .distinct()
                .limit(3)
                .toList();
    }

    private Sprint sprint(Long projectId, String name, String goal, LocalDate start, LocalDate end,
                          int isActive, String status) {
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(name);
        sprint.setGoal(goal);
        sprint.setStartDate(start);
        sprint.setEndDate(end);
        sprint.setIsActive(isActive);
        sprint.setStatus(status);
        sprintMapper.insert(sprint);
        return sprint;
    }

    private Issue issue(Project project, BoardColumn column, Sprint sprint, String type, String title,
                        String priority, String status, List<Long> actors, int actorIndex,
                        LocalDate dueDate, Integer storyPoints, Long reporterId) {
        Issue issue = new Issue();
        issue.setProjectId(project.getId());
        issue.setColumnId(column.getId());
        issue.setSprintId(sprint != null ? sprint.getId() : null);
        issue.setTitle(title);
        issue.setType(type);
        issue.setPriority(priority);
        issue.setStatus(status);
        issue.setAssigneeId(actors.isEmpty() ? reporterId : actors.get(actorIndex % actors.size()));
        issue.setReporterId(reporterId);
        issue.setDueDate(dueDate);
        issue.setStoryPoints(storyPoints);
        issue.setSortOrder(0);
        issue.setDeleted(0);
        if ("BUG".equals(type)) {
            issue.setBugStatus("DONE".equals(status) ? "CLOSED" : ("TODO".equals(status) ? "NEW" : "IN_PROGRESS"));
        }
        issue.setDescription("示例数据：用于演示「" + title + "」这类工作项在看板上的完整流转。");
        return issue;
    }

    private void comment(Issue issue, Long userId, String content) {
        Comment comment = new Comment();
        comment.setIssueId(issue.getId());
        comment.setUserId(userId);
        User user = userMapper.selectById(userId);
        comment.setUsername(user != null ? user.getUsername() : "demo");
        comment.setContent(content);
        commentMapper.insert(comment);
    }

    private void workLog(Issue issue, Long userId, LocalDate date, String hours, String description) {
        WorkLog log = new WorkLog();
        log.setIssueId(issue.getId());
        log.setUserId(userId);
        log.setWorkDate(date);
        log.setHours(new BigDecimal(hours));
        log.setDescription(description);
        workLogMapper.insert(log);
    }

    private void review(Issue issue, Long submitterId, String status, String note, String reviewNote) {
        IssueReview review = new IssueReview();
        review.setIssueId(issue.getId());
        review.setRoundNo(1);
        review.setSubmitterId(submitterId);
        review.setCompletionNote(note);
        review.setEvidenceUrls("[]");
        review.setStatus(status);
        review.setSubmittedAt(LocalDateTime.now().minusDays(1));
        if (!"PENDING".equals(status)) {
            review.setReviewerId(issue.getReporterId());
            review.setReviewNote(reviewNote);
            review.setReviewedAt(LocalDateTime.now().minusHours(5));
        }
        issueReviewMapper.insert(review);
    }

    private void report(Long userId, Long projectId, String type, LocalDate date, String content) {
        Report report = new Report();
        report.setUserId(userId);
        report.setProjectId(projectId);
        report.setType(type);
        report.setReportDate(date);
        report.setContent(content);
        report.setStatus("SUBMITTED");
        reportMapper.insert(report);
    }

    private void activity(Long userId, String username, Long projectId, String targetType, Long targetId,
                          String action, String detail) {
        ActivityLog log = new ActivityLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setProjectId(projectId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now().minusHours(2));
        activityLogMapper.insert(log);
    }

    private void notification(Long userId, String type, String title, String content, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setRelatedType("ISSUE");
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now().minusHours(1));
        notificationMapper.insert(notification);
    }

    private String actorName(List<Long> actors, int index) {
        if (actors.isEmpty()) return "demo";
        User user = userMapper.selectById(actors.get(index % actors.size()));
        return user != null ? user.getUsername() : "demo";
    }

    private String actorNameOr(List<Long> actors, int index, String fallback) {
        String name = actorName(actors, index);
        return name == null || name.isBlank() ? fallback : name;
    }
}
