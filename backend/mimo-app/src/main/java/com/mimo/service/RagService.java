package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.ai.AiService;
import com.mimo.ai.dto.AiChatRequest;
import com.mimo.ai.dto.AiChatResponse;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.RagDTO.*;
import com.mimo.entity.*;
import com.mimo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 120;
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "有", "我", "你",
            "他", "她", "它", "这", "那", "个", "些", "吗", "呢", "啊", "什么",
            "怎么", "如何", "哪些", "为何", "为什么"
    );

    private final RagDocumentMapper ragDocumentMapper;
    private final WikiPageMapper wikiPageMapper;
    private final IssueMapper issueMapper;
    private final CommentMapper commentMapper;
    private final AiService aiService;
    @Transactional
    public IndexResultVO rebuildIndex(Long projectId) {
        List<WikiPage> pages = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getProjectId, projectId));
        List<Issue> issues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId));
        Map<Long, Issue> issueById = issues.stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity(), (a, b) -> a));
        Set<Long> issueIds = issueById.keySet();
        List<Comment> comments = issueIds.isEmpty() ? List.of()
                : commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .in(Comment::getIssueId, issueIds));

        ragDocumentMapper.delete(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getProjectId, projectId));
        int wikiChunks = 0;
        int issueChunks = 0;
        int commentChunks = 0;

        for (WikiPage page : pages) {
            String body = page.getTitle() + "\n" + nullToEmpty(page.getContent());
            for (Chunk chunk : splitChunks(body)) {
                RagDocument doc = document(projectId, "WIKI", page.getId(), null,
                        page.getTitle(), chunk.index, chunk.content,
                        "/projects/" + projectId + "/wiki?page=" + page.getId(),
                        page.getUpdatedAt());
                ragDocumentMapper.insert(doc);
                wikiChunks++;
            }
        }
        for (Issue issue : issues) {
            String body = issueText(issue);
            for (Chunk chunk : splitChunks(body)) {
                RagDocument doc = document(projectId, "ISSUE", issue.getId(), null,
                        issue.getIssueKey() + " · " + issue.getTitle(), chunk.index, chunk.content,
                        "/projects/" + projectId + "/board?issue=" + issue.getId(),
                        issue.getUpdatedAt());
                ragDocumentMapper.insert(doc);
                issueChunks++;
            }
        }
        for (Comment comment : comments) {
            Issue issue = issueById.get(comment.getIssueId());
            if (issue == null) continue;
            String body = "评论人：" + nullToEmpty(comment.getUsername()) + "\n"
                    + nullToEmpty(comment.getContent());
            for (Chunk chunk : splitChunks(body)) {
                RagDocument doc = document(projectId, "COMMENT", comment.getId(),
                        issue.getId(), issue.getIssueKey() + " 评论", chunk.index, chunk.content,
                        "/projects/" + projectId + "/board?issue=" + issue.getId(),
                        comment.getCreatedAt());
                ragDocumentMapper.insert(doc);
                commentChunks++;
            }
        }

        IndexResultVO result = new IndexResultVO();
        result.setProjectId(projectId);
        result.setWikiChunks(wikiChunks);
        result.setIssueChunks(issueChunks);
        result.setCommentChunks(commentChunks);
        result.setTotalChunks(wikiChunks + issueChunks + commentChunks);
        result.setIndexedAt(LocalDateTime.now());
        return result;
    }

    public AskVO ask(Long projectId, String question, Integer topK, Long userId) {
        ensureIndex(projectId);
        int safeTopK = Math.max(1, Math.min(topK == null ? 5 : topK, 8));
        List<ScoredDocument> ranked = retrieve(projectId, question, safeTopK);

        AskVO result = new AskVO();
        result.setProjectId(projectId);
        result.setQuestion(question);
        result.setRetrievedCount(ranked.size());
        result.setGeneratedAt(LocalDateTime.now());
        if (ranked.isEmpty()) {
            result.setAnswer("项目知识库中没有找到与该问题相关且可引用的资料，暂不能给出有依据的回答。");
            result.setAiAvailable(false);
            result.setEvidenceSufficient(false);
            result.setCitations(List.of());
            return result;
        }

        List<CitationVO> citations = toCitations(ranked);
        result.setCitations(citations);
        result.setEvidenceSufficient(true);
        try {
            String context = citations.stream()
                    .map(c -> "[" + c.getCitationNo() + "] 来源：" + c.getTitle()
                            + "\n" + c.getSnippet())
                    .collect(Collectors.joining("\n\n"));
            AiChatResponse response = aiService.chatWithQuota(userId, AiChatRequest.builder()
                    .systemMessage("你是项目知识库问答助手。只能根据给定资料回答，"
                            + "每个关键结论必须用 [编号] 标注引用；资料不足时明确说明，不得补写常识或猜测。"
                            + "使用中文，回答简洁，并在末尾列出实际使用的引用编号。")
                    .userMessage("问题：" + question + "\n\n可用资料：\n" + context)
                    .temperature(0.1)
                    .maxTokens(1200)
                    .build());
            String answer = response == null ? null : response.getContent();
            if (answer == null || answer.isBlank() || response.hasError()) {
                throw new IllegalStateException("AI 未返回有效回答");
            }
            answer = answer.trim();
            if (!answer.matches("(?s).*\\[\\d+].*")) {
                answer += "\n\n参考来源：" + citations.stream()
                        .map(c -> "[" + c.getCitationNo() + "]" + c.getTitle())
                        .collect(Collectors.joining("；"));
            }
            result.setAnswer(answer);
            result.setAiAvailable(true);
        } catch (Exception e) {
            log.warn("RAG AI 回答不可用，返回检索摘要: projectId={}, reason={}",
                    projectId, e.getMessage());
            result.setAiAvailable(false);
            result.setAnswer(buildFallbackAnswer(citations));
        }
        return result;
    }

    public EvaluationResultVO evaluate(EvaluationRequest request) {
        ensureIndex(request.getProjectId());
        int topK = Math.max(1, Math.min(
                request.getTopK() == null ? 5 : request.getTopK(), 10));
        double recallSum = 0;
        double precisionSum = 0;
        int unsupportedCount = 0;
        List<EvaluationDetail> details = new ArrayList<>();

        for (EvaluationCase testCase : request.getCases()) {
            List<ScoredDocument> ranked = retrieve(
                    request.getProjectId(), testCase.getQuestion(), topK);
            List<String> retrieved = ranked.stream()
                    .map(item -> sourceKey(item.document)).toList();
            Set<String> expected = new HashSet<>(testCase.getExpectedSources());
            Set<String> retrievedSet = new HashSet<>(retrieved);
            long hits = expected.stream().filter(retrievedSet::contains).count();
            double recall = expected.isEmpty() ? 0 : hits * 1.0 / expected.size();
            double precision = retrieved.isEmpty() ? 0
                    : retrieved.stream().filter(expected::contains).count() * 1.0 / retrieved.size();
            boolean unsupported = !expected.isEmpty() && retrieved.isEmpty();
            if (unsupported) unsupportedCount++;
            recallSum += recall;
            precisionSum += precision;

            EvaluationDetail detail = new EvaluationDetail();
            detail.setQuestion(testCase.getQuestion());
            detail.setExpectedSources(new ArrayList<>(expected));
            detail.setRetrievedSources(retrieved);
            detail.setRecall(round4(recall));
            detail.setCitationCorrectness(round4(precision));
            detail.setUnsupported(unsupported);
            details.add(detail);
        }

        int count = Math.max(1, request.getCases().size());
        EvaluationResultVO result = new EvaluationResultVO();
        result.setCaseCount(request.getCases().size());
        result.setTopK(topK);
        result.setRecallAtK(round4(recallSum / count));
        result.setCitationCorrectness(round4(precisionSum / count));
        result.setUnsupportedAnswerRate(round4(unsupportedCount * 1.0 / count));
        result.setDetails(details);
        return result;
    }

    private void ensureIndex(Long projectId) {
        long count = ragDocumentMapper.selectCount(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getProjectId, projectId));
        if (count == 0) rebuildIndex(projectId);
    }

    private List<ScoredDocument> retrieve(Long projectId, String question, int topK) {
        List<RagDocument> documents = ragDocumentMapper.selectList(
                new LambdaQueryWrapper<RagDocument>().eq(RagDocument::getProjectId, projectId));
        if (documents.isEmpty()) return List.of();
        Set<String> queryTerms = tokenize(question);
        if (queryTerms.isEmpty()) return List.of();

        List<Set<String>> docTerms = documents.stream()
                .map(doc -> tokenize(doc.getTitle() + "\n" + doc.getContent()))
                .toList();
        double avgLength = docTerms.stream().mapToInt(Set::size).average().orElse(1);
        Map<String, Integer> docFrequency = new HashMap<>();
        for (Set<String> terms : docTerms) {
            for (String term : queryTerms) {
                if (terms.contains(term)) docFrequency.merge(term, 1, Integer::sum);
            }
        }

        List<ScoredDocument> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Set<String> terms = docTerms.get(i);
            Map<String, Integer> frequencies = termFrequency(
                    documents.get(i).getTitle() + "\n" + documents.get(i).getContent());
            double score = 0;
            for (String term : queryTerms) {
                int tf = frequencies.getOrDefault(term, 0);
                if (tf <= 0) continue;
                int df = docFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (documents.size() - df + 0.5) / (df + 0.5));
                double norm = tf + 1.2 * (1 - 0.75 + 0.75 * terms.size() / avgLength);
                score += idf * tf * 2.2 / norm;
                if (documents.get(i).getTitle().toLowerCase(Locale.ROOT)
                        .contains(term)) {
                    score += 0.8;
                }
            }
            if (score > 0) result.add(new ScoredDocument(documents.get(i), score));
        }
        result.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
        return diversify(result, topK);
    }

    private List<ScoredDocument> diversify(List<ScoredDocument> ranked, int topK) {
        List<ScoredDocument> result = new ArrayList<>();
        Map<String, Integer> perSource = new HashMap<>();
        for (ScoredDocument item : ranked) {
            String key = item.document.getSourceType() + ":" + item.document.getSourceId();
            if (perSource.getOrDefault(key, 0) >= 2) continue;
            result.add(item);
            perSource.merge(key, 1, Integer::sum);
            if (result.size() >= topK) break;
        }
        return result;
    }

    private List<CitationVO> toCitations(List<ScoredDocument> ranked) {
        List<CitationVO> citations = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RagDocument doc = ranked.get(i).document;
            CitationVO citation = new CitationVO();
            citation.setCitationNo(i + 1);
            citation.setSourceType(doc.getSourceType());
            citation.setSourceId(doc.getSourceId());
            citation.setParentId(doc.getParentId());
            citation.setTitle(doc.getTitle());
            citation.setSnippet(snippet(doc.getContent()));
            citation.setSourceUrl(doc.getSourceUrl());
            citation.setScore(round4(ranked.get(i).score));
            citations.add(citation);
        }
        return citations;
    }

    private String buildFallbackAnswer(List<CitationVO> citations) {
        StringBuilder answer = new StringBuilder("AI 解读暂不可用。以下是根据项目知识库检索到的相关资料：\n\n");
        for (CitationVO citation : citations) {
            answer.append("[").append(citation.getCitationNo()).append("] ")
                    .append(citation.getTitle()).append("\n")
                    .append(citation.getSnippet()).append("\n\n");
        }
        answer.append("请根据以上引用资料核对答案。");
        return answer.toString();
    }

    private String issueText(Issue issue) {
        StringBuilder text = new StringBuilder();
        text.append(issue.getIssueKey()).append(" ").append(issue.getTitle()).append("\n");
        text.append("类型：").append(nullToEmpty(issue.getType()))
                .append("，状态：").append(nullToEmpty(issue.getStatus()))
                .append("，优先级：").append(nullToEmpty(issue.getPriority())).append("\n");
        if (issue.getDescription() != null) text.append(issue.getDescription()).append("\n");
        if (issue.getAcceptanceCriteria() != null) {
            text.append("验收标准：").append(issue.getAcceptanceCriteria()).append("\n");
        }
        if (issue.getActualResult() != null) {
            text.append("实际结果：").append(issue.getActualResult()).append("\n");
        }
        if (issue.getExpectedResult() != null) {
            text.append("期望结果：").append(issue.getExpectedResult()).append("\n");
        }
        return text.toString();
    }

    private RagDocument document(Long projectId, String type, Long sourceId, Long parentId,
                                 String title, int chunkIndex, String content,
                                 String sourceUrl, LocalDateTime updatedAt) {
        RagDocument doc = new RagDocument();
        doc.setProjectId(projectId);
        doc.setSourceType(type);
        doc.setSourceId(sourceId);
        doc.setParentId(parentId);
        doc.setTitle(title);
        doc.setChunkIndex(chunkIndex);
        doc.setContent(content);
        doc.setSourceUrl(sourceUrl);
        doc.setSourceUpdatedAt(updatedAt);
        doc.setIndexedAt(LocalDateTime.now());
        return doc;
    }

    private List<Chunk> splitChunks(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) return List.of();
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            if (end < normalized.length()) {
                int breakAt = normalized.lastIndexOf('\n', end);
                if (breakAt > start + CHUNK_SIZE / 2) end = breakAt;
            }
            chunks.add(new Chunk(index++, normalized.substring(start, end).trim()));
            if (end >= normalized.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>();
        java.util.regex.Matcher english = java.util.regex.Pattern
                .compile("[a-z0-9_\\-]{2,}").matcher(normalized);
        while (english.find()) tokens.add(english.group());
        java.util.regex.Matcher chinese = java.util.regex.Pattern
                .compile("[\\u4e00-\\u9fff]+").matcher(normalized);
        while (chinese.find()) {
            String run = chinese.group();
            for (int i = 0; i < run.length(); i++) {
                String single = String.valueOf(run.charAt(i));
                if (!STOPWORDS.contains(single)) tokens.add(single);
                if (i + 1 < run.length()) {
                    String pair = run.substring(i, i + 2);
                    if (!STOPWORDS.contains(pair)) tokens.add(pair);
                }
            }
        }
        return tokens;
    }

    private Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> result = new HashMap<>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher english = java.util.regex.Pattern
                .compile("[a-z0-9_\\-]{2,}").matcher(normalized);
        while (english.find()) result.merge(english.group(), 1, Integer::sum);
        java.util.regex.Matcher chinese = java.util.regex.Pattern
                .compile("[\\u4e00-\\u9fff]+").matcher(normalized);
        while (chinese.find()) {
            String run = chinese.group();
            for (int i = 0; i < run.length(); i++) {
                result.merge(String.valueOf(run.charAt(i)), 1, Integer::sum);
                if (i + 1 < run.length()) {
                    result.merge(run.substring(i, i + 2), 1, Integer::sum);
                }
            }
        }
        return result;
    }

    private String sourceKey(RagDocument doc) {
        return doc.getSourceType() + ":" + doc.getSourceId();
    }

    private String snippet(String content) {
        if (content == null) return "";
        String clean = content.replaceAll("\\s+", " ").trim();
        return clean.length() <= 420 ? clean : clean.substring(0, 420) + "…";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record Chunk(int index, String content) {}
    private record ScoredDocument(RagDocument document, double score) {}
}
