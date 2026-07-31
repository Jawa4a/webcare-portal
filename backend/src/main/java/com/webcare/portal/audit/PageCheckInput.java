package com.webcare.portal.audit;

import java.time.Instant;

import org.jsoup.nodes.Document;

public record PageCheckInput(
        String url,
        Integer httpStatus,
        Integer responseTimeMs,
        int redirectCount,
        Instant certificateExpiresAt,
        String errorCode,
        String errorMessage,
        Document document) {
}
