package com.mimo.dto.common;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class IdRequest {
    @NotNull(message = "ID不能为空")
    private Long id;
}
