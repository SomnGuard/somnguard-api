package com.somnguard.device_management.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProvisioningTokenResponse(
        UUID tokenId,
        String token,
        OffsetDateTime expiresAt,
        short maxUses
) {}
