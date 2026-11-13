package com.mimo.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class RagDTO {

    @Data
    public static class AskRequest {
        @NotNull
        private Long projectId;
        @NotBlank
        private String question;
        private Integer topK;
    }

    @Data
    public static class CitationVO {
        private Integer citationNo;
        private String sourceType;
        private Long sourceId;
        private Long parentId;
        private String title;
        private String snippet;
        private String sourceUrl;
        private Double score;
    }

    @Data
    public static class AskVO {
        private Long projectId;
        private String question;
        private String answer;
        private Boolean aiAvailable;
        private Boolean evidenceSufficient;
        private List<CitationVO> citations;
        private Integer retrievedCount;
        private LocalDateTime generatedAt;
    }

    @Data
    public static class IndexResultVO {
        private Long projectId;
        private Integer wikiChunks;
        private Integer issueChunks;
        private Integer commentChunks;
        private Integer totalChunks;
        private LocalDateTime indexedAt;
    }

    @Data
    public static class EvaluationRequest {
        @NotNull
        private Long projectId;
        private Integer topK = 5;
        @NotEmpty
        @Valid
        private List<EvaluationCase> cases;
    }

    @Data
    public static class EvaluationCase {
        @NotBlank
        private String question;
        @NotEmpty
        private List<String> expectedSources;
    }

    @Data
    public static class EvaluationResultVO {
        private Integer caseCount;
        private Integer topK;
        private Double recallAtK;
        private Double citationCorrectness;
        private Double unsupportedAnswerRate;
        private List<EvaluationDetail> details;
    }

    @Data
    public static class EvaluationDetail {
        private String question;
        private List<String> expectedSources;
        private List<String> retrievedSources;
        private Double recall;
        private Double citationCorrectness;
        private Boolean unsupported;
    }
}
