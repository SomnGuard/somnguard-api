package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public record ProvisioningTokenRequest(
        @Size(max = 100, message = "serial_number máximo 100 caracteres")
        @JsonAlias({"serialNumber", "serial_number"})
        String serialNumber
) {}
