package com.mimo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

public class BoardDTO {

    @Data
    public static class CreateColumnRequest {
        @NotNull
        private Long projectId;
        @NotBlank
        private String name;
        private String color;
        private Integer sortOrder;
    }

    @Data
    public static class UpdateColumnRequest {
        @NotNull
        private Long id;
        private String name;
        private String color;
        private Integer sortOrder;
    }

    @Data
    public static class SortRequest {
        @NotNull
        private List<Long> columnIds;
    }

    @Data
    public static class ColumnVO {
        private Long id;
        private String name;
        private String color;
        private Integer sortOrder;
        private List<IssueDTO.IssueVO> issues;
    }

    @Data
    public static class BoardVO {
        private Long projectId;
        private String projectName;
        private List<ColumnVO> columns;
    }
}
