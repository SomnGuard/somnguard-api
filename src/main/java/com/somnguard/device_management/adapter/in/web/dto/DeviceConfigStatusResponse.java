package com.somnguard.device_management.adapter.in.web.dto;

import java.util.UUID;

public record DeviceConfigStatusResponse(
        UUID deviceId,
        int appliedVersion,
        int availableVersion,
        boolean pending,
        boolean outdated
) {}