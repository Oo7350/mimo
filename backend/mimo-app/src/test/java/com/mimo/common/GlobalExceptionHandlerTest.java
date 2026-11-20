package com.mimo.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调用方参数写错不该被伪装成"服务器内部错误"。
 * 这条回归来自实测：`GET /api/wiki/search?projectId=1` 少了 `q`，返回 code=500。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingRequestParameterIsClientError() {
        Result<Void> result = handler.handleMissingParam(
                new MissingServletRequestParameterException("q", "String"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("q");
    }

    @Test
    void unreadableBodyIsClientError() {
        Result<Void> result = handler.handleUnreadableBody(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad json"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("JSON");
    }

    @Test
    void unexpectedExceptionStillReports500() {
        Result<Void> result = handler.handleOther(new IllegalStateException("boom"), null);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo(ResultCode.INTERNAL_ERROR.getMessage());
    }
}
