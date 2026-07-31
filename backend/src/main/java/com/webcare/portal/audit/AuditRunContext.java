package com.webcare.portal.audit;

import java.util.UUID;

public record AuditRunContext(
        UUID auditId,
        String websiteName,
        String startUrl,
        String normalizedHost,
        int maxPages,
        int maxDepth) {
}
