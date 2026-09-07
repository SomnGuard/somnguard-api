package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record HeartbeatRequest(
        @Size(max = 50, message = "firmware_version máximo 50 caracteres")
        @JsonAlias({"firmwareVersion", "firmware_version"})
        String firmwareVersion,

        @Min(value = 0, message = "pending_count debe ser >= 0")
        @JsonAlias({"pendingCount", "pending_count"})
        Integer pendingCount,

        @Min(value = 0, message = "free_disk_pct debe estar entre 0 y 100")
        @Max(value = 100, message = "free_disk_pct debe estar entre 0 y 100")
        @JsonAlias({"freeDiskPct", "free_disk_pct"})
        Integer freeDiskPct,

        @Min(value = 0, message = "uptime_s debe ser >= 0")
        @JsonAlias({"uptimeS", "uptime_s"})
        Long uptimeS
) {}
