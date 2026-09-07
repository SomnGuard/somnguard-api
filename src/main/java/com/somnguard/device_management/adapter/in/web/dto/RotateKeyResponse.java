package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RotateKeyResponse(
        UUID deviceId,
        String apiKey,
        OffsetDateTime rotatedAt
) {}
