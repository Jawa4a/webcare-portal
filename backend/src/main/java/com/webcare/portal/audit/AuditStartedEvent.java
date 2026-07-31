package com.webcare.portal.audit;

import java.util.UUID;

public record AuditStartedEvent(UUID auditId) {
}
