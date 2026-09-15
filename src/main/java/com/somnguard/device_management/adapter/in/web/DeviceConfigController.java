package com.somnguard.device_management.adapter.in.web;

import com.somnguard.device_management.adapter.in.web.dto.DeviceConfigResponse;
import com.somnguard.device_management.adapter.in.web.dto.DeviceConfigStatusResponse;
import com.somnguard.device_management.adapter.in.web.dto.PatchDeviceConfigRequest;
import com.somnguard.device_management.adapter.in.web.dto.PatchDeviceConfigResponse;
import com.somnguard.device_management.application.usecase.DeviceConfigService;
import com.somnguard.device_management.domain.exception.ConfigDeprecatedException;
import com.somnguard.platform.security.RequireFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración global versionada (ADR-011, solo global).
 * GET dual (JWT o X-Device-ID + X-API-Key); PATCH deprecated (410 Gone).
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceConfigController {

    private final DeviceConfigService deviceConfigService;

    public DeviceConfigController(DeviceConfigService deviceConfigService) {
        this.deviceConfigService = deviceConfigService;
    }

    @GetMapping("/{id}/config")
    public DeviceConfigResponse getConfig(@PathVariable UUID id,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest http) {
        UUID headerId = parseDeviceIdHeader(deviceIdHeader);
        boolean jwt = DeviceAuthSupport.isJwt();
        return deviceConfigService.getEffectiveConfig(id, headerId, apiKey, jwt,
                DeviceAuthSupport.currentFeatures(), clientIp(http));
    }

    @GetMapping("/{id}/config/status")
    @RequireFeature({"device.read", "device.write", "device.config_read", "device.config_write", "device.assign"})
    public DeviceConfigStatusResponse getConfigStatus(@PathVariable UUID id) {
        return deviceConfigService.getConfigStatus(id);
    }

    @PatchMapping("/{id}/config")
    public PatchDeviceConfigResponse patchConfig(@PathVariable UUID id,
            @Valid @RequestBody(required = false) PatchDeviceConfigRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // ADR-011: PATCH por device derogado (solo global vía catálogos).
        throw new ConfigDeprecatedException(
                "PATCH /devices/{id}/config está deprecated (410): gestione sound_pattern/event_type vía PATCH /api/v1/catalogs/...; la versión global se propaga por pull manual");
    }

    @PostMapping("/{id}/config/refresh")
    @RequireFeature({"device.read", "device.write", "device.config_read", "device.config_write", "device.assign"})
    public Map<String, Object> requestRefresh(@PathVariable UUID id) {
        boolean admin = DeviceAuthSupport.isAdmin();
        return deviceConfigService.requestRefresh(id, DeviceAuthSupport.currentUserId(), admin,
                DeviceAuthSupport.currentFeatures());
    }

    private UUID parseDeviceIdHeader(String deviceIdHeader) {
        if (deviceIdHeader == null || deviceIdHeader.isBlank()) return null;
        try {
            return UUID.fromString(deviceIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("X-Device-ID debe ser un UUID válido");
        }
    }

    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
