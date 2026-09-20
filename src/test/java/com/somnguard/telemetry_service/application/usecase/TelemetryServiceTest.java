package com.somnguard.telemetry_service.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
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
import com.somnguard.telemetry_service.adapter.out.persistence.repository.AlertLogRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    EventTypeRepository eventTypeRepository;
    @Mock
    SeverityRepository severityRepository;
    @Mock
    SoundPatternRepository soundPatternRepository;
    @Mock
    EventRepository eventRepository;
    @Mock
    AlertLogRepository alertLogRepository;

    final DeviceApiKeyService apiKeyService = new DeviceApiKeyService();
    TelemetryService service;

    final UUID deviceId = UUID.randomUUID();
    final String plainKey = "test-api-key-12345";
    DeviceEntity device;
    EventTypeEntity eventType;
    SeverityEntity severity;
    SoundPatternEntity soundPattern;

    @BeforeEach
    void setUp() {
        service = new TelemetryService(deviceRepository, apiKeyService, eventTypeRepository,
                severityRepository, soundPatternRepository, eventRepository, alertLogRepository);
        device = new DeviceEntity();
        device.setId(deviceId);
        device.setApiKeyHash(apiKeyService.hash(plainKey));
        device.setIsActive(true);
        device.setStatus(DeviceStatus.DEVICE_ACTIVE.code());
        device.setStatusCategory(DeviceStatus.DEVICE_ACTIVE.category());
        eventType = new EventTypeEntity();
        eventType.setId(UUID.randomUUID());
        eventType.setCode("EV-SOM-01");
        eventType.setIsActive(true);
        eventType.setDefaultSoundPatternId(UUID.randomUUID());
        severity = new SeverityEntity();
        severity.setId(UUID.randomUUID());
        severity.setCode("warning");
        soundPattern = new SoundPatternEntity();
        soundPattern.setId(UUID.randomUUID());
        soundPattern.setCode("AS-02");
    }

    private TelemetryEventItem item(UUID eventId) {
        return new TelemetryEventItem(eventId, deviceId,
                OffsetDateTime.parse("2026-03-15T10:00:00Z"),
                "EV-SOM-01", "MODERADA", "AS-02", Map.of("ear", 0.2), true, false);
    }

    private void stubCatalogs() {
        when(eventTypeRepository.findByCodeAndDeletedAtIsNull("EV-SOM-01"))
                .thenReturn(Optional.of(eventType));
        when(severityRepository.findByCodeAndIsActiveTrue("warning"))
                .thenReturn(Optional.of(severity));
        when(soundPatternRepository.findByCodeAndIsActiveTrue("AS-02"))
                .thenReturn(Optional.of(soundPattern));
    }

    @Test
    void ingestAckedAndDuplicate() {
        stubCatalogs();
        UUID fresh = UUID.randomUUID();
        UUID dup = UUID.randomUUID();
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(device));
        when(eventRepository.existsById(fresh)).thenReturn(false);
        when(eventRepository.existsById(dup)).thenReturn(true);
        TelemetryBatchResponse response = service.ingest(deviceId, plainKey,
                new TelemetryBatchRequest(List.of(item(fresh), item(dup))));
        assertEquals(List.of(fresh), response.ackedIds());
        assertEquals(List.of(dup), response.duplicateIds());
        verify(eventRepository).save(any());
        verify(alertLogRepository).save(any());
    }

    @Test
    void ingestUnknownEventTypeFailsWholeBatch() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(device));
        when(eventTypeRepository.findByCodeAndDeletedAtIsNull("EV-XXX"))
                .thenReturn(Optional.empty());
        TelemetryEventItem bad = new TelemetryEventItem(UUID.randomUUID(), deviceId,
                OffsetDateTime.now(), "EV-XXX", "INFO", null, Map.of(), true, false);
        assertThrows(InvalidCatalogReferenceException.class,
                () -> service.ingest(deviceId, plainKey, new TelemetryBatchRequest(List.of(bad))));
        verify(eventRepository, never()).save(any());
        verify(alertLogRepository, never()).save(any());
    }

    @Test
    void ingestRegisteredWithoutAssignIsForbidden() {
        device.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(device));
        assertThrows(DeviceForbiddenException.class,
                () -> service.ingest(deviceId, plainKey,
                        new TelemetryBatchRequest(List.of(item(UUID.randomUUID())))));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void ingestNormalizesSpanishSeverity() {
        stubCatalogs();
        UUID fresh = UUID.randomUUID();
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(device));
        when(eventRepository.existsById(fresh)).thenReturn(false);
        service.ingest(deviceId, plainKey, new TelemetryBatchRequest(List.of(item(fresh))));
        verify(severityRepository).findByCodeAndIsActiveTrue("warning");
        assertTrue(true);
    }
}
