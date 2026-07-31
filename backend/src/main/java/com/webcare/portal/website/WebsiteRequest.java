package com.webcare.portal.website;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebsiteRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 2048) String url,
        @Min(1) @Max(100) Integer maxPages,
        @Min(0) @Max(3) Integer maxDepth,
        Boolean active) {
}
