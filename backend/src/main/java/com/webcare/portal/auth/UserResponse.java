package com.webcare.portal.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        List<RoleCode> roles,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(PortalUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.isActive(),
                user.getCreatedAt());
    }

    public static UserResponse from(PortalUserDetails user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoles().stream().sorted().toList(),
                user.isEnabled(),
                null);
    }
}
