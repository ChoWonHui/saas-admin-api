package com.saas.admin.common.error;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, null, LocalDateTime.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(errorCode.name(), message, fieldErrors, LocalDateTime.now());
    }
}
