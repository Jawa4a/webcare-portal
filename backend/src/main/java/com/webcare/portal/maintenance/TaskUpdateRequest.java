package com.webcare.portal.maintenance;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 4000) String description,
        @NotNull TaskPriority priority,
        @NotNull TaskStatus status,
        UUID assignedToUserId,
        Instant dueAt,
        @Size(max = 1000) String comment) {
}
