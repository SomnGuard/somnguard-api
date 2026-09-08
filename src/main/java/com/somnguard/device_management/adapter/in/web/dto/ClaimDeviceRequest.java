package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record ClaimDeviceRequest(
        @NotBlank(message = "claim_code es requerido")
        @JsonAlias({"claimCode", "claim_code"})
        String claimCode
) {}
