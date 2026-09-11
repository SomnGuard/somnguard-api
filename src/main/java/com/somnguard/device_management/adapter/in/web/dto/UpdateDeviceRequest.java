package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public record UpdateDeviceRequest(
        @Size(max = 50, message = "firmware_version máximo 50 caracteres")
        @JsonAlias({"firmwareVersion", "firmware_version"})
        String firmwareVersion,

        @Size(max = 50, message = "status máximo 50 caracteres")
        @JsonAlias("status")
        String status
) {}
