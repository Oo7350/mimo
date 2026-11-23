package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.dto.CommentDTO;
import com.mimo.dto.CommentDTO.UpdateRequest;
import com.mimo.entity.Comment;
import com.mimo.entity.Issue;
import com.mimo.entity.Notification;
import com.mimo.entity.User;
import com.mimo.mapper.CommentMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KNOWN-009：评论以前发出去只能删、不能改。补更新端点时同时守住「只有作者能改」。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentMapper commentMapper;
    @Mock private UserMapper userMapper;
    @Mock private IssueMapper issueMapper;
    @Mock private NotificationService notificationService;
    @Mock private WebSocketService webSocketService;

    private CommentService service() {
        return new CommentService(commentMapper, userMapper, issueMapper,
                notificationService, webSocketService);
    }

    private Comment commentOf(long userId) {
        Comment c = new Comment();
        c.setId(5L);
        c.setIssueId(1L);
        c.setUserId(userId);
        c.setContent("旧内容");
        return c;
    }

    private UpdateRequest req(String content) {
        UpdateRequest r = new UpdateRequest();
        r.setContent(content);
        return r;
    }

    @Test
    void authorCanUpdateOwnComment() {
        when(commentMapper.selectById(5L)).thenReturn(commentOf(3L));

        CommentDTO.CommentVO vo = service().update(5L, req("新内容"), 3L);

        assertThat(vo.getContent()).isEqualTo("新内容");
        verify(commentMapper).updateById(org.mockito.ArgumentMatchers.any(Comment.class));
    }

    @Test
    void otherUsersCannotUpdateComment() {
        when(commentMapper.selectById(5L)).thenReturn(commentOf(3L));

        assertThatThrownBy(() -> service().update(5L, req("改别人的"), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能修改自己的评论");

        verify(commentMapper, never()).updateById(org.mockito.ArgumentMatchers.any(Comment.class));
    }

    @Test
    void updateShouldRejectMissingComment() {
        when(commentMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service().update(404L, req("x"), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评论不存在");
    }

    // ==================== @提及 通知（计划书 §七：遗漏的通知触发点） ====================

    private Issue issue(long id, Long assigneeId) {
        Issue i = new Issue();
        i.setId(id);
        i.setProjectId(2L);
        i.setIssueKey("MIMO-" + id);
        i.setAssigneeId(assigneeId);
        return i;
    }

    private CommentDTO.CreateRequest newComment(String content) {
        CommentDTO.CreateRequest r = new CommentDTO.CreateRequest();
        r.setIssueId(1L);
        r.setContent(content);
        return r;
    }

    private User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        return u;
    }

    @Test
    void mentionShouldNotifyMentionedUser() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, "zhangsan"));
        when(issueMapper.selectById(1L)).thenReturn(issue(1L, null));
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(user(7L, "lisi"));

        service().create(newComment("@lisi 这个验收标准你看下"), 3L);

        // 指派人不存在 → 只有 @提及 这一条通知
        org.mockito.ArgumentCaptor<Notification> captor =
                org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).create(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(7L);
        assertThat(n.getType()).isEqualTo("MENTION");
        assertThat(n.getRelatedType()).isEqualTo("ISSUE");
        assertThat(n.getRelatedId()).isEqualTo(1L);
        assertThat(n.getContent()).contains("MIMO-1").contains("zhangsan");
        // 实时推送也要打给被 @ 的人
        verify(webSocketService).sendNotification(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mentionShouldSkipSelfAndUnknownUser() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, "zhangsan"));
        when(issueMapper.selectById(1L)).thenReturn(issue(1L, null));
        // zhangsan @ 自己 → 不通知；@不存在的人 → 查不到也不通知
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        service().create(newComment("@zhangsan 自问自答 @nobody"), 3L);

        verify(notificationService, never()).create(org.mockito.ArgumentMatchers.any(Notification.class));
        verify(webSocketService, never()).sendNotification(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mentionShouldNotDuplicateAssigneeNotification() {
        // 指派人是 lisi，评论里又 @ 了 lisi：只该收一条「新评论」，不该再收一条「@提及」
        when(userMapper.selectById(3L)).thenReturn(user(3L, "zhangsan"));
        when(issueMapper.selectById(1L)).thenReturn(issue(1L, 7L));
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(user(7L, "lisi"));

        service().create(newComment("@lisi 麻烦确认下"), 3L);

        org.mockito.ArgumentCaptor<Notification> captor =
                org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, org.mockito.Mockito.times(1)).create(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("STATUS_CHANGED");
    }

    @Test
    void parseMentionsShouldFollowUsernameCharset() {
        // @张三，谢谢 —— 解析出的用户名必须是「张三」，不能把中文逗号带进去
        assertThat(CommentService.parseMentions("@张三，谢谢")).containsExactly("张三");
        // 英文标点/换行同样截断；重复提及只算一次，按出现顺序
        assertThat(CommentService.parseMentions("@lisi, @zhangsan\n@lisi 再看下"))
                .containsExactly("lisi", "zhangsan");
        // 邮箱不是提及（否则每写个邮箱都白查一次库）
        assertThat(CommentService.parseMentions("发我邮箱 a@b.com")).isEmpty();
        // 空正文 / 没有 @
        assertThat(CommentService.parseMentions(null)).isEmpty();
        assertThat(CommentService.parseMentions("普通评论")).isEmpty();
    }
}
