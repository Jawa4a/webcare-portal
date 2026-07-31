package com.webcare.portal.audit;

import java.util.List;

public record AuditPageResponse(
        List<AuditResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
