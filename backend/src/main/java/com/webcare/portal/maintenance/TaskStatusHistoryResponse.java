package com.webcare.portal.maintenance;

import java.time.Instant;
import java.util.UUID;

public record TaskStatusHistoryResponse(
        UUID id,
        TaskStatus oldStatus,
        TaskStatus newStatus,
        UUID changedByUserId,
        String changedBy,
        String comment,
        Instant changedAt) {

    static TaskStatusHistoryResponse from(TaskStatusHistory history) {
        return new TaskStatusHistoryResponse(
                history.getId(), history.getOldStatus(), history.getNewStatus(),
                history.getChangedBy().getId(), history.getChangedBy().getDisplayName(),
                history.getComment(), history.getChangedAt());
    }
}
