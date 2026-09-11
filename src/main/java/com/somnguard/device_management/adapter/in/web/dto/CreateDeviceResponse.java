package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateDeviceResponse(
        UUID id,
        String serialNumber,
        String firmwareVersion,
        String status,
        String apiKey,
        String claimCode
) {}
