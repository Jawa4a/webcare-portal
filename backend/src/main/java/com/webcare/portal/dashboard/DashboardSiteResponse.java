package com.webcare.portal.dashboard;

import java.time.Instant;
import java.util.UUID;

import com.webcare.portal.audit.AuditStatus;

public record DashboardSiteResponse(
        UUID websiteId,
        String websiteName,
        String host,
        boolean active,
        UUID latestAuditId,
        AuditStatus latestAuditStatus,
        Integer score,
        int findingsCount,
        Instant checkedAt) {
}
