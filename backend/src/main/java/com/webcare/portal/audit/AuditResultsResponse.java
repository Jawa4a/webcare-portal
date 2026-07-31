package com.webcare.portal.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditResultsResponse(
        AuditResponse audit,
        UUID previousAuditId,
        Map<FindingSeverity, Long> severityCounts,
        List<AuditedPageResultResponse> pages,
        List<FindingResponse> findings,
        int totalFindings,
        int filteredFindings) {
}
