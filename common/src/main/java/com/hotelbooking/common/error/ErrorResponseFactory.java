package com.hotelbooking.common.error;

import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

public final class ErrorResponseFactory {

    private static final String TRACE_ID_KEY = "traceId";

    private ErrorResponseFactory() {}

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, Map.of());
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                MDC.get(TRACE_ID_KEY),
                fieldErrors);
    }
}
