package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HeartbeatResponse(
        UUID deviceId,
        String status,
        OffsetDateTime lastHeartbeatAt,
        Boolean configPending,
        Integer configVersionAvailable
) {}
