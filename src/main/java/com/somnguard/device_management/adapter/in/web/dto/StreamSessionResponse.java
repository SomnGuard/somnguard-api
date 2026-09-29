package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StreamSessionResponse(
        UUID sessionId,
        UUID deviceId,
        String room,
        int viewerCount,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt
) {}
