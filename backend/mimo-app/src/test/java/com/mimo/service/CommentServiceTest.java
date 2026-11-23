package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.dto.CommentDTO;
import com.mimo.dto.CommentDTO.UpdateRequest;
import com.mimo.entity.Comment;
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
}
