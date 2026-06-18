package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class WorkLogDTO {

    @Data
    public static class CreateRequest {
        @NotNull
        private Long issueId;
        @NotNull
        private LocalDate workDate;
        @NotNull
        @DecimalMin(value = "0.01", message = "工时必须大于 0")
        private BigDecimal hours;
        private String description;
    }

    @Data
    public static class WorkLogVO {
        private Long id;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Long userId;
        private String username;
        private String avatar;
        private LocalDate workDate;
        private BigDecimal hours;
        private String description;
        private LocalDateTime createdAt;
    }

    @Data
    public static class MemberWorkloadVO {
        private Long userId;
        private String username;
        private String avatar;
        private BigDecimal totalHours;     // 总工时
        private Integer logCount;           // 工时记录数
        private Integer issueCount;         // 涉及 Issue 数
    }

    @Data
    public static class SprintWorkloadVO {
        private Long sprintId;
        private String sprintName;
        private BigDecimal totalHours;
        private Integer memberCount;
        private List<MemberWorkloadVO> members;
    }
}
