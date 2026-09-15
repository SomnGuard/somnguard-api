package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Respuesta plana (HU-API-005 AC-002/AC-003).
 * Las claves de configuración van en la raíz para que el firmware
 * (que filtra por REMOTE_CONFIG_KEYS e ignora el resto) las aplique
 * sin cambios; {@code sources} expone trazabilidad para portal/admin.
 */
public class DeviceConfigResponse {

    private final UUID deviceId;
    private final Integer version;
    private final Map<String, Object> configuration;
    private final Map<String, Object> sources;
    private final OffsetDateTime lastConfigPullAt;

    public DeviceConfigResponse(UUID deviceId, Integer version, Map<String, Object> configuration,
            Map<String, Object> sources, OffsetDateTime lastConfigPullAt) {
        this.deviceId = deviceId;
        this.version = version;
        this.configuration = configuration == null ? Map.of() : configuration;
        this.sources = sources == null ? Map.of() : sources;
        this.lastConfigPullAt = lastConfigPullAt;
    }

    public UUID getDeviceId() { return deviceId; }
    public Integer getVersion() { return version; }

    @JsonIgnore
    public Map<String, Object> getConfiguration() { return configuration; }

    @JsonAnyGetter
    public Map<String, Object> getConfigurationEntries() { return configuration; }

    public Map<String, Object> getSources() { return sources; }
    public OffsetDateTime getLastConfigPullAt() { return lastConfigPullAt; }
}
