package com.mimo.dto;

/**
 * 任务认领结果：直接通过（管理员）或进入审批（普通成员）
 */
public class IssueClaimResult {

    /** APPROVED = 已认领并推进；PENDING = 已提交管理员审批 */
    private String status;
    private String message;
    private Long issueId;
    private Long assigneeId;
    private Long columnId;
    private Long approvalRequestId;

    public static IssueClaimResult direct(Long issueId, Long assigneeId, Long columnId) {
        IssueClaimResult result = new IssueClaimResult();
        result.status = "APPROVED";
        result.message = columnId != null ? "认领成功，任务已自动进入进行中" : "认领成功";
        result.issueId = issueId;
        result.assigneeId = assigneeId;
        result.columnId = columnId;
        return result;
    }

    public static IssueClaimResult pending(Long issueId, Long approvalRequestId) {
        IssueClaimResult result = new IssueClaimResult();
        result.status = "PENDING";
        result.message = "已提交管理员审批，通过后会自动进入进行中";
        result.issueId = issueId;
        result.approvalRequestId = approvalRequestId;
        return result;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public Long getColumnId() { return columnId; }
    public void setColumnId(Long columnId) { this.columnId = columnId; }

    public Long getApprovalRequestId() { return approvalRequestId; }
    public void setApprovalRequestId(Long approvalRequestId) { this.approvalRequestId = approvalRequestId; }
}
