package com.webcare.portal.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditedPageResultResponse(
        UUID id,
        String url,
        int depth,
        Integer httpStatus,
        Integer responseTimeMs,
        String contentType,
        Long responseBytes,
        Instant checkedAt,
        String errorCode,
        String errorMessage) {

    static AuditedPageResultResponse from(AuditedPage page) {
        return new AuditedPageResultResponse(
                page.getId(), page.getUrl(), page.getDepth(), page.getHttpStatus(),
                page.getResponseTimeMs(), page.getContentType(), page.getResponseBytes(),
                page.getCheckedAt(), page.getErrorCode(), page.getErrorMessage());
    }
}
