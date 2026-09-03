package com.somnguard.security.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserMeResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String status,
        String statusCategory
) {}
