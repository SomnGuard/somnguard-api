package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotBlank(message = "serial_number es requerido")
        @Size(max = 100, message = "serial_number máximo 100 caracteres")
        @JsonAlias({"serialNumber", "serial_number"})
        String serialNumber,

        @NotBlank(message = "firmware_version es requerido")
        @Size(max = 50, message = "firmware_version máximo 50 caracteres")
        @JsonAlias({"firmwareVersion", "firmware_version"})
        String firmwareVersion
) {}
