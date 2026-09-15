package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String serialNumber,
        String firmwareVersion,
        String status,
        String statusCategory,
        OffsetDateTime lastHeartbeatAt,
        String lastSeenIp,
        UUID assignedUserId,
        OffsetDateTime assignedAt,
        String claimCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer appliedConfigVersion,
        Boolean pendingConfigUpdate,
        OffsetDateTime lastConfigPullAt
) {}
