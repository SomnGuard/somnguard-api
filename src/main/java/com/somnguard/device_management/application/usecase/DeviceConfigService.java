package com.somnguard.device_management.application.usecase;

import com.somnguard.device_management.adapter.in.web.dto.DeviceConfigResponse;
import com.somnguard.device_management.adapter.in.web.dto.DeviceConfigStatusResponse;
import com.somnguard.device_management.adapter.in.web.dto.PatchDeviceConfigResponse;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceConfigEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceConfigHistoryEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceConfigHistoryRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceConfigRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.exception.InvalidDeviceConfigException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.device_management.domain.service.DeviceConfigMergeService;
import com.somnguard.device_management.domain.service.DeviceConfigNormalizer;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
import com.somnguard.parameterization.application.usecase.GlobalConfigService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuración global versionada (ADR-011, solo global, HU-API-005 reescrita).
 * GET genera el JSON desde DB + version global (sin overrides); el pull del device
 * persiste applied_config_version. PATCH está deprecated (410 en el controller).
 */
@Service
public class DeviceConfigService {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfigService.class);

    static final String CONFIG_PUBLISHED = "DEVICE_CONFIG_PUBLISHED";
    static final String CONFIG_ACTIVE = "ACTIVE";
    private static final List<String> READ_FEATURES = List.of("device.read", "device.write",
            "device.config_read", "device.config_write", "device.assign");

    private final DeviceRepository deviceRepository;
    private final DeviceConfigRepository configRepository;
    private final DeviceConfigHistoryRepository historyRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SoundPatternRepository soundPatternRepository;
    private final DeviceApiKeyService apiKeyService;
    private final GlobalConfigService globalConfigService;

    public DeviceConfigService(DeviceRepository deviceRepository, DeviceConfigRepository configRepository,
            DeviceConfigHistoryRepository historyRepository, EventTypeRepository eventTypeRepository,
            SoundPatternRepository soundPatternRepository, DeviceApiKeyService apiKeyService,
            GlobalConfigService globalConfigService) {
        this.deviceRepository = deviceRepository;
        this.configRepository = configRepository;
        this.historyRepository = historyRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.soundPatternRepository = soundPatternRepository;
        this.apiKeyService = apiKeyService;
        this.globalConfigService = globalConfigService;
    }

    @Transactional
    public DeviceConfigResponse getEffectiveConfig(UUID deviceId, UUID headerDeviceId, String plainKey,
            boolean jwtCaller, List<String> features, String seenIp) {
        DeviceEntity device = requireDevice(deviceId);
        if (jwtCaller) {
            requireReadFeature(features);
        } else {
            requireDeviceAuth(device, deviceId, headerDeviceId, plainKey);
        }
        GlobalConfigService.GlobalVersion global = globalConfigService.getOrCreate();
        Catalog catalog = loadCatalog();
        // Solo global: sin overrides por device.
        Map<String, Object> effective = DeviceConfigMergeService.buildEffective(Map.of(),
                catalog.events(), catalog.sounds());
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("global_version", global.version());
        sources.put("global_updated_at", global.updatedAt());

        // Solo el pull del device (no el portal) marca como entregado, limpia pending
        // y PERSISTE en device_config + device_config_history
        if (!jwtCaller) {
            OffsetDateTime now = OffsetDateTime.now();
            log.info("Device pull config: deviceId={}, globalVersion={}, appliedBefore={}, pendingBefore={}",
                    deviceId, global.version(), device.getAppliedConfigVersion(), device.getPendingConfigUpdate());
            // Self-register no deja created_by (DeviceService.selfRegister): usar SYSTEM_ID
            // porque device_config_history.changed_by es NOT NULL y si no la tx hace rollback
            // (GET 500) y el device nunca limpia pending ni registra el json.
            UUID actor = device.getCreatedBy() != null ? device.getCreatedBy() : DeviceService.SYSTEM_ID;

            // Persistir en device_config (upsert). version es @Version (optimistic locking):
            // JPA lo incrementa solo en update; no tocarlo manualmente.
            DeviceConfigEntity cfg = configRepository.findByDeviceIdAndDeletedAtIsNull(deviceId).orElse(null);
            if (cfg == null) {
                cfg = new DeviceConfigEntity();
                cfg.setId(UUID.randomUUID());
                cfg.setDeviceId(device.getId());
                cfg.setIsActive(true);
                cfg.setVersion(1);
                cfg.setStatus(CONFIG_PUBLISHED);
                cfg.setStatusCategory(CONFIG_ACTIVE);
                cfg.setPublishedAt(now);
                cfg.setCreatedAt(now);
                cfg.setCreatedBy(actor);
            }
            cfg.setConfiguration(new LinkedHashMap<>(effective));
            cfg.setUpdatedAt(now);
            cfg.setUpdatedBy(actor); // device pull
            configRepository.save(cfg);
            log.debug("device_config upserted: deviceId={}, version={}", deviceId, cfg.getVersion());

            // Historial
            DeviceConfigHistoryEntity h = new DeviceConfigHistoryEntity();
            h.setId(UUID.randomUUID());
            h.setDeviceConfigId(cfg.getId());
            h.setConfiguration(new LinkedHashMap<>(effective));
            h.setChangedBy(actor); // device pull
            h.setChangeReason("Pull manual tras refresh (heartbeat pending)");
            h.setCreatedAt(now);
            h.setCreatedBy(actor);
            historyRepository.save(h);
            log.debug("device_config_history inserted: deviceId={}, version={}", deviceId, cfg.getVersion());

            // Marcar device como aplicado
            device.setAppliedConfigVersion(global.version());
            device.setLastConfigPullAt(now);
            device.setPendingConfigUpdate(false);
            if (seenIp != null && !seenIp.isBlank()) {
                device.setLastSeenIp(seenIp.length() > 45 ? seenIp.substring(0, 45) : seenIp);
            }
            device.setUpdatedAt(now);
            deviceRepository.save(device);
            log.info("Device config applied: deviceId={}, appliedVersion={}, pending=false",
                    deviceId, global.version());
            return new DeviceConfigResponse(deviceId, global.version(), effective, sources, now);
        }
        return new DeviceConfigResponse(deviceId, global.version(), effective, sources,
                device.getLastConfigPullAt());
    }

    /**
     * @deprecated PATCH por device derogado por ADR-011 (solo global).
     * El controller responde 410; se conserva sin wiring para ventana de migración.
     */
    @Deprecated
    @Transactional
    public PatchDeviceConfigResponse patchConfig(UUID deviceId, Map<String, Object> rawConfiguration,
            String changeReason, UUID adminId, String idempotencyKey) {
        DeviceEntity device = requireDevice(deviceId);
        requireAdmin(adminId);
        String reason = changeReason == null ? null : changeReason.trim();
        if (reason != null && reason.length() > 200) {
            throw new IllegalArgumentException("change_reason máximo 200 caracteres");
        }
        Map<String, Object> canonical = DeviceConfigNormalizer.normalize(rawConfiguration);
        Catalog catalog = loadCatalog();
        validateReferences(canonical, catalog);
        OffsetDateTime now = OffsetDateTime.now();
        DeviceConfigEntity cfg = configRepository.findByDeviceIdAndDeletedAtIsNull(device.getId()).orElse(null);
        if (cfg == null) {
            cfg = new DeviceConfigEntity();
            cfg.setId(UUID.randomUUID());
            cfg.setDeviceId(device.getId());
            cfg.setIsActive(true);
            cfg.setVersion(1);
            cfg.setStatus(CONFIG_PUBLISHED);
            cfg.setStatusCategory(CONFIG_ACTIVE);
            cfg.setPublishedAt(now);
            cfg.setCreatedAt(now);
            cfg.setCreatedBy(adminId);
        }
        cfg.setConfiguration(canonical);
        cfg.setUpdatedAt(now);
        cfg.setUpdatedBy(adminId);
        configRepository.save(cfg);
        // Marcar pendiente para que el device lo pullée solo cuando el usuario pulse Actualizar
        // Se marca aquí para que el próximo heartbeat avise; el GET lo limpiará
        device.setPendingConfigUpdate(true);
        device.setUpdatedAt(now);
        deviceRepository.save(device);
        DeviceConfigHistoryEntity h = new DeviceConfigHistoryEntity();
        h.setId(UUID.randomUUID());
        h.setDeviceConfigId(cfg.getId());
        h.setConfiguration(new LinkedHashMap<>(canonical));
        h.setChangedBy(adminId);
        h.setChangeReason(reason == null || reason.isEmpty() ? null : reason);
        h.setCreatedAt(now);
        h.setCreatedBy(adminId);
        historyRepository.save(h);
        return new PatchDeviceConfigResponse(device.getId(), cfg.getVersion(), now);
    }

    @Transactional
    public Map<String, Object> requestRefresh(UUID deviceId, UUID caller, boolean admin, List<String> features) {
        DeviceEntity device = requireDevice(deviceId);
        // refresh lo puede pedir el owner del device o un admin con device.config_write/device.write
        if (admin) {
            // admin pasa
        } else {
            boolean hasRead = features != null && READ_FEATURES.stream().anyMatch(features::contains);
            if (!hasRead) throw new DeviceForbiddenException("Se requiere device.read, device.config_read o device.assign");
            // ownership check vía assignment
            // se delega al caller: si no es admin, debe ser el asignado (validación ligera)
        }
        device.setPendingConfigUpdate(true);
        device.setUpdatedAt(OffsetDateTime.now());
        deviceRepository.save(device);
        return Map.of("device_id", deviceId, "pending", true);
    }

    private DeviceEntity requireDevice(UUID id) {
        return deviceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new DeviceNotFoundException("Dispositivo no encontrado"));
    }

    private void requireReadFeature(List<String> features) {
        if (features == null || READ_FEATURES.stream().noneMatch(features::contains)) {
            throw new DeviceForbiddenException("Se requiere device.read, device.write, device.config_read o device.assign");
        }
    }

    private void requireDeviceAuth(DeviceEntity device, UUID pathId, UUID headerId, String plainKey) {
        if (headerId == null || plainKey == null || plainKey.isEmpty()) {
            throw new InvalidDeviceCredentialsException("Headers X-Device-ID y X-API-Key son obligatorios");
        }
        if (!pathId.equals(headerId)) {
            throw new InvalidDeviceCredentialsException("X-Device-ID no coincide con el dispositivo de la ruta");
        }
        if (Boolean.FALSE.equals(device.getIsActive())) {
            throw new DeviceForbiddenException("Dispositivo inactivo");
        }
        if (!apiKeyService.verify(plainKey, device.getApiKeyHash())) {
            throw new InvalidDeviceCredentialsException("API key inválida");
        }
        DeviceStatus current = DeviceStatus.fromCode(device.getStatus());
        if (current == DeviceStatus.DEVICE_SUSPENDED) {
            throw new DeviceForbiddenException("Dispositivo suspendido por un administrador");
        }
        if (current == DeviceStatus.DEVICE_RETIRED) {
            throw new DeviceForbiddenException("Dispositivo retirado, no reactivable");
        }
    }

    private void requireAdmin(UUID adminId) {
        if (adminId == null || DeviceService.SYSTEM_ID.equals(adminId)) {
            throw new DeviceForbiddenException("El PATCH requiere usuario administrador (JWT), con changed_by válido");
        }
    }

    @Transactional(readOnly = true)
    public DeviceConfigStatusResponse getConfigStatus(UUID deviceId) {
        DeviceEntity device = requireDevice(deviceId);
        GlobalConfigService.GlobalVersion global = globalConfigService.getOrCreate();
        int applied = device.getAppliedConfigVersion() == null ? 0 : device.getAppliedConfigVersion();
        boolean pending = device.getPendingConfigUpdate() != null && device.getPendingConfigUpdate();
        boolean outdated = applied < global.version();
        return new DeviceConfigStatusResponse(deviceId, applied, global.version(), pending, outdated);
    }

    private Catalog loadCatalog() {
        List<EventTypeEntity> events = eventTypeRepository.findByDeletedAtIsNull();
        List<SoundPatternEntity> sounds = soundPatternRepository.findByIsActiveTrue();
        return new Catalog(events, sounds);
    }

    private void validateReferences(Map<String, Object> canonical, Catalog catalog) {
        Map<String, EventTypeEntity> eventsByCode = new LinkedHashMap<>();
        for (EventTypeEntity e : catalog.events()) {
            if (Boolean.TRUE.equals(e.getIsActive()) && e.getDeletedAt() == null
                    && "PUBLISHED".equalsIgnoreCase(e.getStatus())) {
                eventsByCode.put(e.getCode(), e);
            }
        }
        Map<String, SoundPatternEntity> soundsByCode = new LinkedHashMap<>();
        for (SoundPatternEntity s : catalog.sounds()) soundsByCode.put(s.getCode(), s);
        Object thresholds = canonical.get("thresholds");
        if (thresholds instanceof Map<?, ?> m) {
            for (Object k : m.keySet()) {
                if (!eventsByCode.containsKey(String.valueOf(k))) {
                    throw new InvalidDeviceConfigException("thresholds." + k + " no es un event_type PUBLISHED activo");
                }
            }
        }
        Object soundMap = canonical.get("event_sound_map");
        if (soundMap instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!eventsByCode.containsKey(String.valueOf(e.getKey()))) {
                    throw new InvalidDeviceConfigException("event_sound_map." + e.getKey() + " no es un event_type válido");
                }
                if (!soundsByCode.containsKey(String.valueOf(e.getValue()))) {
                    throw new InvalidDeviceConfigException("event_sound_map." + e.getKey() + " apunta a sonido inactivo");
                }
            }
        }
        Object tweaks = canonical.get("sound_pattern_tweaks");
        if (tweaks instanceof Map<?, ?> m) {
            for (Object k : m.keySet()) {
                if (!soundsByCode.containsKey(String.valueOf(k))) {
                    throw new InvalidDeviceConfigException("sound_pattern_tweaks." + k + " no es un sonido activo");
                }
            }
        }
    }

    record Catalog(List<EventTypeEntity> events, List<SoundPatternEntity> sounds) {}
}
