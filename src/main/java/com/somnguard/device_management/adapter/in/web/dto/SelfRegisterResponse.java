package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SelfRegisterResponse(
        UUID deviceId,
        String status,
        String apiKey,
        String claimCode
) {}
