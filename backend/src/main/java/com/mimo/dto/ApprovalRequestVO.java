package com.mimo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalRequestVO {
    private Long id;
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private Long requesterId;
    private String requesterUsername;
    private String targetType;
    private String title;
    private String description;
    private String dataJson;
    private String status;       // PENDING / APPROVED / REJECTED
    private Long approverId;
    private String approverUsername;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
}
