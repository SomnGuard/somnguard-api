package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PatchDeviceConfigResponse(
        UUID deviceId,
        Integer version,
        OffsetDateTime updatedAt
) {}
