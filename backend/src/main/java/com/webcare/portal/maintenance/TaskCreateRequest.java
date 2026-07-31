package com.webcare.portal.maintenance;

import java.time.Instant;
import java.util.UUID;

public record TaskCreateRequest(
        TaskPriority priority,
        UUID assignedToUserId,
        Instant dueAt) {
}
