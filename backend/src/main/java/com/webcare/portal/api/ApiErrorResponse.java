package com.webcare.portal.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors,
        String correlationId,
        Instant timestamp) {

    public record FieldErrorResponse(String field, String code) {
    }
}
