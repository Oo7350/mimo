package com.mimo.service;

import com.mimo.ai.AiService;
import com.mimo.ai.dto.AiChatResponse;
import com.mimo.dto.RagDTO.AskVO;
import com.mimo.dto.RagDTO.EvaluationCase;
import com.mimo.dto.RagDTO.EvaluationRequest;
import com.mimo.dto.RagDTO.EvaluationResultVO;
import com.mimo.dto.RagDTO.IndexResultVO;
import com.mimo.entity.Comment;
import com.mimo.entity.Issue;
import com.mimo.entity.RagDocument;
import com.mimo.entity.WikiPage;
import com.mimo.mapper.CommentMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.RagDocumentMapper;
import com.mimo.mapper.WikiPageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private RagDocumentMapper ragDocumentMapper;
    @Mock
    private WikiPageMapper wikiPageMapper;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private AiService aiService;

    @Test
    void shouldAnswerWithProjectCitation() {
        RagDocument document = wiki(1L, "支付架构", "支付模块采用网关、订单服务和账务服务三层架构。");
        when(ragDocumentMapper.selectCount(any())).thenReturn(1L);
        when(ragDocumentMapper.selectList(any())).thenReturn(List.of(document));
        when(aiService.chatWithQuota(anyLong(), any()))
                .thenReturn(AiChatResponse.of("支付模块采用三层架构 [1]"));

        AskVO result = service().ask(7L, "支付架构是什么", 5, 9L);

        assertThat(result.getAiAvailable()).isTrue();
        assertThat(result.getEvidenceSufficient()).isTrue();
        assertThat(result.getAnswer()).contains("[1]");
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getSourceType()).isEqualTo("WIKI");
        assertThat(result.getCitations().get(0).getSourceUrl())
                .contains("/projects/7/wiki?page=1");
    }

    @Test
    void shouldRefuseWithoutEvidenceAndAvoidAiCall() {
        RagDocument document = wiki(1L, "支付架构", "支付模块采用三层架构。");
        when(ragDocumentMapper.selectCount(any())).thenReturn(1L);
        when(ragDocumentMapper.selectList(any())).thenReturn(List.of(document));

        AskVO result = service().ask(7L, "Kubernetes cluster autoscaling", 5, 9L);

        assertThat(result.getEvidenceSufficient()).isFalse();
        assertThat(result.getAnswer()).contains("没有找到");
        assertThat(result.getCitations()).isEmpty();
        verify(aiService, never()).chatWithQuota(anyLong(), any());
    }

    @Test
    void shouldCalculateRetrievalEvaluationMetrics() {
        RagDocument document = wiki(1L, "支付架构", "支付模块采用网关、订单服务和账务服务三层架构。");
        when(ragDocumentMapper.selectCount(any())).thenReturn(1L);
        when(ragDocumentMapper.selectList(any())).thenReturn(List.of(document));

        EvaluationCase testCase = new EvaluationCase();
        testCase.setQuestion("支付架构");
        testCase.setExpectedSources(List.of("WIKI:1"));
        EvaluationRequest request = new EvaluationRequest();
        request.setProjectId(7L);
        request.setTopK(5);
        request.setCases(List.of(testCase));

        EvaluationResultVO result = service().evaluate(request);

        assertThat(result.getRecallAtK()).isEqualTo(1.0);
        assertThat(result.getCitationCorrectness()).isEqualTo(1.0);
        assertThat(result.getUnsupportedAnswerRate()).isZero();
        assertThat(result.getDetails().get(0).getRetrievedSources()).contains("WIKI:1");
    }

    @Test
    void shouldRebuildIndexForWikiIssuesAndComments() {
        WikiPage page = new WikiPage();
        page.setId(1L);
        page.setTitle("支付架构");
        page.setContent("支付模块采用网关、订单服务和账务服务三层架构。");
        page.setUpdatedAt(LocalDateTime.now());

        Issue issue = new Issue();
        issue.setId(5L);
        issue.setIssueKey("MIMO-5");
        issue.setTitle("支付超时");
        issue.setType("BUG");
        issue.setStatus("IN_PROGRESS");
        issue.setPriority("HIGH");
        issue.setDescription("高峰期支付回调超时");
        issue.setUpdatedAt(LocalDateTime.now());

        Comment comment = new Comment();
        comment.setId(9L);
        comment.setIssueId(5L);
        comment.setUsername("张三");
        comment.setContent("已定位为锁等待");
        comment.setCreatedAt(LocalDateTime.now());

        when(wikiPageMapper.selectList(any())).thenReturn(List.of(page));
        when(issueMapper.selectList(any())).thenReturn(List.of(issue));
        when(commentMapper.selectList(any())).thenReturn(List.of(comment));

        IndexResultVO result = service().rebuildIndex(7L);

        assertThat(result.getWikiChunks()).isEqualTo(1);
        assertThat(result.getIssueChunks()).isEqualTo(1);
        assertThat(result.getCommentChunks()).isEqualTo(1);
        assertThat(result.getTotalChunks()).isEqualTo(3);
        verify(ragDocumentMapper).delete(any());
        verify(ragDocumentMapper, times(3)).insert(any());
    }

    @Test
    void shouldAppendSourceListWhenModelOmitsCitationMarks() {
        when(ragDocumentMapper.selectCount(any())).thenReturn(1L);
        when(ragDocumentMapper.selectList(any()))
                .thenReturn(List.of(wiki(1L, "支付架构", "支付模块采用网关、订单服务和账务服务三层架构。")));
        when(aiService.chatWithQuota(anyLong(), any()))
                .thenReturn(AiChatResponse.of("支付模块采用三层架构。"));

        AskVO result = service().ask(7L, "支付架构是什么", 5, 9L);

        assertThat(result.getAiAvailable()).isTrue();
        assertThat(result.getAnswer()).contains("参考来源").contains("[1]");
    }

    @Test
    void shouldFallbackToRetrievedSnippetsWhenAiFails() {
        when(ragDocumentMapper.selectCount(any())).thenReturn(1L);
        when(ragDocumentMapper.selectList(any()))
                .thenReturn(List.of(wiki(1L, "支付架构", "支付模块采用三层架构。")));
        when(aiService.chatWithQuota(anyLong(), any()))
                .thenThrow(new RuntimeException("ai offline"));

        AskVO result = service().ask(7L, "支付架构是什么", 5, 9L);

        assertThat(result.getAiAvailable()).isFalse();
        assertThat(result.getEvidenceSufficient()).isTrue();
        assertThat(result.getAnswer()).contains("AI 解读暂不可用").contains("支付架构");
        assertThat(result.getCitations()).hasSize(1);
    }

    @Test
    void shouldCountUnsupportedAnswerWhenNothingRetrieved() {
        when(ragDocumentMapper.selectCount(any())).thenReturn(1L);
        when(ragDocumentMapper.selectList(any())).thenReturn(List.of());
        EvaluationCase testCase = new EvaluationCase();
        testCase.setQuestion("支付架构");
        testCase.setExpectedSources(List.of("WIKI:1"));
        EvaluationRequest request = new EvaluationRequest();
        request.setProjectId(7L);
        request.setTopK(5);
        request.setCases(List.of(testCase));

        EvaluationResultVO result = service().evaluate(request);

        assertThat(result.getRecallAtK()).isZero();
        assertThat(result.getUnsupportedAnswerRate()).isEqualTo(1.0);
        assertThat(result.getDetails().get(0).getUnsupported()).isTrue();
    }

    private RagService service() {
        return new RagService(ragDocumentMapper, wikiPageMapper, issueMapper,
                commentMapper, aiService);
    }

    private RagDocument wiki(Long id, String title, String content) {
        RagDocument document = new RagDocument();
        document.setId(id);
        document.setProjectId(7L);
        document.setSourceType("WIKI");
        document.setSourceId(id);
        document.setTitle(title);
        document.setChunkIndex(0);
        document.setContent(content);
        document.setSourceUrl("/projects/7/wiki?page=" + id);
        return document;
    }
}
