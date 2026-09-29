package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StreamStartResponse(
        UUID sessionId,
        UUID deviceId,
        String room,
        String tokenViewer,
        String wsUrl,
        OffsetDateTime expiresAt
) {}
