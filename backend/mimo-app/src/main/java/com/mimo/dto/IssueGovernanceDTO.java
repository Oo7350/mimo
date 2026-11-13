package com.mimo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class IssueGovernanceDTO {

    @Data
    public static class IssueVersionVO {
        private Long id;
        private Long issueId;
        private Integer versionNo;
        private JsonNode snapshot;
        private String changeSummary;
        private String source;
        private Long operatorId;
        private String operatorName;
        private LocalDateTime createdAt;
    }

    @Data
    public static class FieldDiff {
        private String field;
        private Object before;
        private Object after;
    }

    @Data
    public static class CreateChangeRequest {
        @NotNull
        private Integer baseVersion;
        @NotEmpty
        private Map<String, Object> proposedChanges;
        @NotBlank
        private String changeReason;
    }

    @Data
    public static class ReviewChangeRequest {
        private String note;
    }

    @Data
    public static class ChangeRequestVO {
        private Long id;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Integer baseVersion;
        private Integer currentVersion;
        private Map<String, Object> proposedChanges;
        private List<FieldDiff> diffs;
        private String changeReason;
        private String status;
        private Long applicantId;
        private String applicantName;
        private Long reviewerId;
        private String reviewerName;
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private LocalDateTime createdAt;
        private Boolean versionConflict;
    }

    @Data
    public static class SubmitReviewRequest {
        private String completionNote;
        private List<String> evidenceUrls;
    }

    @Data
    public static class ReviewVO {
        private Long id;
        private Long issueId;
        private Integer roundNo;
        private Long submitterId;
        private String submitterName;
        private String completionNote;
        private List<String> evidenceUrls;
        private String status;
        private Long reviewerId;
        private String reviewerName;
        private String reviewNote;
        private LocalDateTime submittedAt;
        private LocalDateTime reviewedAt;
    }

    @Data
    public static class ImpactVO {
        private Long requestId;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Integer affectedIssueCount;
        private Integer affectedTaskCount;
        private Integer completedTaskCount;
        private List<AffectedIssueVO> affectedIssues;
        private Long affectedSprintId;
        private String affectedSprintName;
        private List<AffectedSprintVO> affectedSprints;
        private Long currentSprintId;
        private String currentSprintName;
        private Long proposedSprintId;
        private String proposedSprintName;
        private Boolean sprintScopeChanged;
        private Boolean activeSprintAffected;
        private Integer currentStoryPoints;
        private Integer proposedStoryPoints;
        private Integer storyPointDelta;
        private Integer workloadDelta;
        private LocalDate currentDueDate;
        private LocalDate proposedDueDate;
        private Long dueDateDeltaDays;
        private String riskLevel;
        private List<String> riskReasons;
    }

    @Data
    public static class AffectedIssueVO {
        private Long id;
        private String issueKey;
        private String title;
        private String type;
        private String status;
        private Long sprintId;
        private String sprintName;
        private Boolean completed;
        private String relation;
    }

    @Data
    public static class AffectedSprintVO {
        private Long id;
        private String name;
        private String status;
        private Boolean active;
    }
}
