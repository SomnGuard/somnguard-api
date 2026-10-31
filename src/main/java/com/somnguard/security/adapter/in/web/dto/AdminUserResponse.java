package com.somnguard.security.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Boolean isActive,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime lastLoginAt,
        String status,
        String statusCategory,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AdminUserRoleResponse> roles
) {
    public AdminUserResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
