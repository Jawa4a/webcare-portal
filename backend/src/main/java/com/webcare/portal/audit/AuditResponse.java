package com.webcare.portal.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditResponse(
        UUID id,
        UUID websiteId,
        String websiteName,
        AuditStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        int pagesDiscovered,
        int pagesChecked,
        int findingsCount,
        Integer score,
        String failureCode,
        String failureMessage,
        String startedBy) {

    public static AuditResponse from(Audit audit) {
        return new AuditResponse(
                audit.getId(),
                audit.getWebsite().getId(),
                audit.getWebsite().getName(),
                audit.getStatus(),
                audit.getCreatedAt(),
                audit.getStartedAt(),
                audit.getFinishedAt(),
                audit.getPagesDiscovered(),
                audit.getPagesChecked(),
                audit.getFindingsCount(),
                audit.getScore(),
                audit.getFailureCode(),
                audit.getFailureMessage(),
                audit.getStartedBy().getDisplayName());
    }
}
