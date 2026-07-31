package com.webcare.portal.website;

import java.time.Instant;
import java.util.UUID;

public record WebsiteResponse(
        UUID id,
        String name,
        String url,
        String normalizedHost,
        int maxPages,
        int maxDepth,
        boolean active,
        UUID createdByUserId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static WebsiteResponse from(Website website) {
        return new WebsiteResponse(
                website.getId(),
                website.getName(),
                website.getStartUrl(),
                website.getNormalizedHost(),
                website.getMaxPages(),
                website.getMaxDepth(),
                website.isActive(),
                website.getCreatedBy().getId(),
                website.getCreatedBy().getDisplayName(),
                website.getCreatedAt(),
                website.getUpdatedAt());
    }
}
