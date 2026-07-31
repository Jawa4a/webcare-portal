package com.webcare.portal.audit;

import java.util.List;
import java.util.UUID;

public record AuditComparisonResponse(
        UUID websiteId,
        String websiteName,
        AuditResponse latestAudit,
        AuditResponse previousAudit,
        Integer scoreDelta,
        List<FindingResponse> newFindings,
        List<FindingResponse> ongoingFindings,
        List<FindingResponse> fixedFindings) {
}
