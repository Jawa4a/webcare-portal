package com.webcare.portal.audit;

public record PageSnapshot(
        String url,
        String normalizedUrl,
        int depth,
        Integer httpStatus,
        Integer responseTimeMs,
        String contentType,
        Long responseBytes,
        String errorCode,
        String errorMessage) {
}
