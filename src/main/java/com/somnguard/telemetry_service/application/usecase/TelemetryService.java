package com.somnguard.telemetry_service.application.usecase;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SeverityEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SeverityRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
import com.somnguard.telemetry_service.adapter.in.web.dto.TelemetryBatchRequest;
import com.somnguard.telemetry_service.adapter.in.web.dto.TelemetryBatchResponse;
import com.somnguard.telemetry_service.adapter.in.web.dto.TelemetryEventItem;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.AlertLogEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.AlertLogRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingesta idempotente de eventos (HU-API-007 AC-001/002/003/005/006/007, FEA-TEL-INGEST).
 *
 * <p>Semántica fail-fast (recomendada): si un item trae catálogo desconocido se responde
 * {@code 422} y no se persiste nada del lote (transaccional). Los duplicados por
 * {@code event_id} no son error: van en {@code duplicate_ids} del {@code 201}.
 */
@Service
public class TelemetryService {

    private final DeviceRepository deviceRepository;
    private final DeviceApiKeyService apiKeyService;
    private final EventTypeRepository eventTypeRepository;
    private final SeverityRepository severityRepository;
    private final SoundPatternRepository soundPatternRepository;
    private final EventRepository eventRepository;
    private final AlertLogRepository alertLogRepository;

    public TelemetryService(DeviceRepository deviceRepository,
            DeviceApiKeyService apiKeyService,
            EventTypeRepository eventTypeRepository,
            SeverityRepository severityRepository,
            SoundPatternRepository soundPatternRepository,
            EventRepository eventRepository,
            AlertLogRepository alertLogRepository) {
        this.deviceRepository = deviceRepository;
        this.apiKeyService = apiKeyService;
        this.eventTypeRepository = eventTypeRepository;
        this.severityRepository = severityRepository;
        this.soundPatternRepository = soundPatternRepository;
        this.eventRepository = eventRepository;
        this.alertLogRepository = alertLogRepository;
    }

    record ResolvedRefs(UUID eventTypeId, UUID severityId, UUID soundPatternId) {}

    @Transactional
    public TelemetryBatchResponse ingest(UUID headerDeviceId, String plainKey,
            TelemetryBatchRequest request) {
        DeviceEntity device = requireAuthorizedDevice(headerDeviceId, plainKey);
        List<UUID> acked = new ArrayList<>();
        List<UUID> duplicates = new ArrayList<>();
        for (TelemetryEventItem item : request.events()) {
            requireEventOwnership(item, headerDeviceId);
            ResolvedRefs refs = resolveRefs(item);
            if (eventRepository.existsById(item.event_id())) {
                duplicates.add(item.event_id());
                continue;
            }
            persistNewEvent(item, device, refs);
            acked.add(item.event_id());
        }
        device.setLastSyncAt(OffsetDateTime.now());
        device.setUpdatedAt(OffsetDateTime.now());
        deviceRepository.save(device);
        return new TelemetryBatchResponse(List.copyOf(acked), List.copyOf(duplicates));
    }

    DeviceEntity requireAuthorizedDevice(UUID headerDeviceId, String plainKey) {
        if (headerDeviceId == null || plainKey == null || plainKey.isEmpty()) {
            throw new InvalidDeviceCredentialsException(
                    "Headers X-Device-ID y X-API-Key son obligatorios");
        }
        DeviceEntity device = deviceRepository.findByIdAndDeletedAtIsNull(headerDeviceId)
                .orElseThrow(() -> new DeviceNotFoundException("Dispositivo no encontrado"));
        if (Boolean.FALSE.equals(device.getIsActive())) {
            throw new DeviceForbiddenException("Dispositivo inactivo");
        }
        if (!apiKeyService.verify(plainKey, device.getApiKeyHash())) {
            throw new InvalidDeviceCredentialsException("API key inválida");
        }
        DeviceStatus current = DeviceStatus.fromCode(device.getStatus());
        if (current == null) {
            current = DeviceStatus.DEVICE_REGISTERED;
        }
        switch (current) {
            case DEVICE_REGISTERED -> throw new DeviceForbiddenException(
                    "Dispositivo sin asignar, asócielo a una cuenta antes de enviar telemetría");
            case DEVICE_SUSPENDED -> throw new DeviceForbiddenException(
                    "Dispositivo suspendido por un administrador");
            case DEVICE_RETIRED -> throw new DeviceForbiddenException(
                    "Dispositivo retirado, no reactivable");
            default -> { }
        }
        return device;
    }

    private void requireEventOwnership(TelemetryEventItem item, UUID headerDeviceId) {
        if (item.device_id() == null || !headerDeviceId.equals(item.device_id())) {
            throw new InvalidDeviceCredentialsException(
                    "device_id del evento no coincide con X-Device-ID");
        }
    }

    ResolvedRefs resolveRefs(TelemetryEventItem item) {
        EventTypeEntity type = resolveEventType(item);
        UUID severityId = resolveSeverity(item);
        UUID soundId = resolveSoundPattern(item, type);
        return new ResolvedRefs(type.getId(), severityId, soundId);
    }

    private EventTypeEntity resolveEventType(TelemetryEventItem item) {
        String code = item.event_type() == null ? null : item.event_type().trim();
        if (code == null || code.isEmpty()) {
            throw new InvalidCatalogReferenceException("event_type es requerido");
        }
        EventTypeEntity type = eventTypeRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> new InvalidCatalogReferenceException(
                        "event_type desconocido: " + code));
        if (Boolean.FALSE.equals(type.getIsActive())) {
            throw new InvalidCatalogReferenceException("event_type inactivo: " + code);
        }
        return type;
    }

    private UUID resolveSeverity(TelemetryEventItem item) {
        String code = item.severity() == null ? null : item.severity().trim();
        if (code == null || code.isEmpty()) {
            throw new InvalidCatalogReferenceException("severity es requerida");
        }
        String canonical = normalizeSeverity(code);
        SeverityEntity severity = severityRepository.findByCodeAndIsActiveTrue(canonical)
                .orElseThrow(() -> new InvalidCatalogReferenceException(
                        "severity desconocida: " + code));
        return severity.getId();
    }

    private UUID resolveSoundPattern(TelemetryEventItem item, EventTypeEntity type) {
        String code = item.sound_pattern() == null ? null : item.sound_pattern().trim();
        if (code == null || code.isEmpty()) {
            return fallbackSoundPattern(type);
        }
        SoundPatternEntity pattern = soundPatternRepository.findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new InvalidCatalogReferenceException(
                        "sound_pattern desconocido: " + code));
        return pattern.getId();
    }

    private UUID fallbackSoundPattern(EventTypeEntity type) {
        UUID fallback = type.getDefaultSoundPatternId();
        if (fallback == null) {
            throw new InvalidCatalogReferenceException(
                    "sound_pattern requerido (sin fallback en event_type " + type.getCode() + ")");
        }
        return fallback;
    }

    /**
     * El edge envía severidades en español ({@code LEVE/MODERADA/SEVERA/CRITICA/INFO});
     * el catálogo usa {@code info/warning/high/critical}. Se normaliza aquí para no
     * romper la ingesta con {@code 422} espurios; lo realmente desconocido sigue en 422.
     */
    static String normalizeSeverity(String raw) {
        String upper = raw.trim().toUpperCase();
        switch (upper) {
            case "INFO":
            case "LEVE":
                return "info";
            case "WARNING":
            case "MODERADA":
                return "warning";
            case "HIGH":
            case "SEVERA":
                return "high";
            case "CRITICAL":
            case "CRITICA":
                return "critical";
            default:
                return raw.trim().toLowerCase();
        }
    }

    private void persistNewEvent(TelemetryEventItem item, DeviceEntity device, ResolvedRefs refs) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> metadata = item.metadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(item.metadata());
        EventEntity event = new EventEntity();
        event.setId(item.event_id());
        event.setDeviceId(device.getId());
        event.setEventTypeId(refs.eventTypeId());
        event.setOccurredAt(item.occurred_at());
        event.setSeverityId(refs.severityId());
        event.setSoundPatternId(refs.soundPatternId());
        event.setIsOfflineSync(Boolean.TRUE.equals(item.is_offline_sync()));
        event.setMetadata(metadata);
        event.setIsActive(true);
        event.setCreatedAt(now);
        event.setCreatedBy(device.getId());
        event.setUpdatedAt(now);
        event.setVersion(1);
        eventRepository.save(event);
        AlertLogEntity alert = new AlertLogEntity();
        alert.setId(UUID.randomUUID());
        alert.setEventId(item.event_id());
        alert.setSoundPatternId(refs.soundPatternId());
        alert.setSeverityId(refs.severityId());
        alert.setTriggeredAt(item.occurred_at());
        alert.setDeviceId(device.getId());
        alert.setCreatedAt(now);
        alert.setCreatedBy(device.getId());
        alert.setIsActive(true);
        alertLogRepository.save(alert);
    }
}
