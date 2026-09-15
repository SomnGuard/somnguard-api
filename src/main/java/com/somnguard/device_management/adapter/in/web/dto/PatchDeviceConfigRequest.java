package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record PatchDeviceConfigRequest(
        Map<String, Object> configuration,

        @Size(max = 200, message = "change_reason máximo 200 caracteres")
        @JsonAlias({"changeReason", "change_reason"})
        String changeReason
) {}
