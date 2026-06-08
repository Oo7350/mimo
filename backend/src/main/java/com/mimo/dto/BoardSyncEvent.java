package com.mimo.dto;

import com.mimo.dto.IssueDTO.IssueVO;
import lombok.Data;

@Data
public class BoardSyncEvent {
    private String type;        // ISSUE_MOVED, ISSUE_CREATED, ISSUE_UPDATED, ISSUE_DELETED
    private Long projectId;
    private Long issueId;
    private Long targetColumnId;
    private Integer sortOrder;
    private IssueVO issue;      // full issue for CREATE events

    public static BoardSyncEvent moved(Long projectId, Long issueId, Long targetColumnId, Integer sortOrder) {
        BoardSyncEvent e = new BoardSyncEvent();
        e.setType("ISSUE_MOVED");
        e.setProjectId(projectId);
        e.setIssueId(issueId);
        e.setTargetColumnId(targetColumnId);
        e.setSortOrder(sortOrder);
        return e;
    }

    public static BoardSyncEvent created(Long projectId, IssueVO issue) {
        BoardSyncEvent e = new BoardSyncEvent();
        e.setType("ISSUE_CREATED");
        e.setProjectId(projectId);
        e.setIssueId(issue.getId());
        e.setIssue(issue);
        return e;
    }

    public static BoardSyncEvent updated(Long projectId, Long issueId) {
        BoardSyncEvent e = new BoardSyncEvent();
        e.setType("ISSUE_UPDATED");
        e.setProjectId(projectId);
        e.setIssueId(issueId);
        return e;
    }

    public static BoardSyncEvent deleted(Long projectId, Long issueId) {
        BoardSyncEvent e = new BoardSyncEvent();
        e.setType("ISSUE_DELETED");
        e.setProjectId(projectId);
        e.setIssueId(issueId);
        return e;
    }
}
