package com.webcare.portal.dashboard;

import java.util.List;

import com.webcare.portal.audit.AuditResponse;

public record DashboardResponse(
        long totalWebsites,
        long activeWebsites,
        long completedAudits,
        long openTasks,
        int latestFindings,
        Integer averageScore,
        List<DashboardSiteResponse> sites,
        List<AuditResponse> recentAudits) {
}
