package com.mimo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("approval_requests")
public class ApprovalRequest {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long projectId;
    private Long requesterId;
    private String targetType;  // PROJECT_CREATE / PROJECT_UPDATE / PROJECT_DELETE
    private String title;
    private String description;
    private String dataJson;     // JSON格式存储操作数据
    private String status;       // PENDING / APPROVED / REJECTED
    private Long approverId;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 执行结果：SUCCESS / FAILED；NULL = 未跟踪（KNOWN-006） */
    private String execStatus;
    /** 最近一次执行失败原因（截断到 500 字） */
    private String execError;
    /** 执行尝试次数（含首次） */
    private Integer execAttempts;
    /** 最近一次执行时间 */
    private LocalDateTime lastExecAt;
}
