package com.webcare.portal.audit;

import java.time.Instant;
import java.util.UUID;

public record FindingResponse(
        UUID id,
        UUID auditId,
        UUID pageId,
        String pageUrl,
        String ruleCode,
        FindingSeverity severity,
        String title,
        String description,
        String recommendation,
        String elementContext,
        FindingChangeType changeType,
        UUID taskId,
        Instant createdAt) {

    static FindingResponse from(Finding finding, FindingChangeType changeType, UUID taskId) {
        AuditedPage page = finding.getAuditedPage();
        return new FindingResponse(
                finding.getId(), page.getAudit().getId(), page.getId(), page.getUrl(),
                finding.getRuleCode(), finding.getSeverity(), finding.getTitle(),
                finding.getDescription(), finding.getRecommendation(), finding.getElementContext(),
                changeType, taskId, finding.getCreatedAt());
    }
}
