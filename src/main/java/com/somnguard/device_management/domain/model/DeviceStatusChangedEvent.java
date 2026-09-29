package com.somnguard.device_management.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Transición de estado del device (para push WS en tiempo real). */
public record DeviceStatusChangedEvent(
        UUID deviceId,
        String status,
        String category,
        String reason,
        OffsetDateTime at
) {}
