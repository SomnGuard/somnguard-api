package com.somnguard.telemetry_service.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SeverityRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.AlertLogRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.adapter.out.storage.EvidenceStoragePort;
import com.somnguard.telemetry_service.domain.exception.EvidenceConflictException;
import com.somnguard.telemetry_service.domain.exception.TelemetryEventNotFoundException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

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
    @Mock
    EvidenceRepository evidenceRepository;
    @Mock
    MediaTypeRepository mediaTypeRepository;
    @Mock
    EvidenceStoragePort storage;

    final DeviceApiKeyService apiKeyService = new DeviceApiKeyService();
    EvidenceService service;

    final UUID deviceId = UUID.randomUUID();
    final String plainKey = "test-api-key-12345";
    final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TelemetryService telemetry = new TelemetryService(deviceRepository, apiKeyService,
                eventTypeRepository, severityRepository, soundPatternRepository,
                eventRepository, alertLogRepository);
        service = new EvidenceService(telemetry, eventRepository, evidenceRepository,
                mediaTypeRepository, storage);
        DeviceEntity device = device();
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(device));
    }

    private DeviceEntity device() {
        DeviceEntity device = new DeviceEntity();
        device.setId(deviceId);
        device.setApiKeyHash(apiKeyService.hash(plainKey));
        device.setIsActive(true);
        device.setStatus(DeviceStatus.DEVICE_ACTIVE.code());
        device.setStatusCategory(DeviceStatus.DEVICE_ACTIVE.category());
        return device;
    }

    private EventEntity event() {
        EventEntity event = new EventEntity();
        event.setId(eventId);
        event.setDeviceId(deviceId);
        event.setOccurredAt(OffsetDateTime.parse("2026-03-15T10:00:00Z"));
        return event;
    }

    @Test
    void uploadStoresWithExpectedKey() {
        byte[] jpg = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x01, 0x02};
        String checksum = EvidenceService.sha256Hex(jpg);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(evidenceRepository.existsByEventId(eventId)).thenReturn(false);
        MediaTypeEntity mediaType = new MediaTypeEntity();
        mediaType.setId(UUID.randomUUID());
        mediaType.setCode("image_jpeg");
        when(mediaTypeRepository.findByCodeAndIsActiveTrue("image_jpeg"))
                .thenReturn(Optional.of(mediaType));
        UUID evidenceId = service.upload(deviceId, plainKey, eventId, jpg, "image/jpeg", checksum);
        assertTrue(evidenceId != null);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq(jpg), eq("image/jpeg"));
        assertEquals(deviceId + "/2026/03/15/" + eventId + ".jpg", key.getValue());
    }

    @Test
    void uploadConflictWhenEvidenceExists() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(evidenceRepository.existsByEventId(eventId)).thenReturn(true);
        byte[] jpg = new byte[]{0x01};
        String checksum = EvidenceService.sha256Hex(jpg);
        assertThrows(EvidenceConflictException.class,
                () -> service.upload(deviceId, plainKey, eventId, jpg, "image/jpeg", checksum));
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void uploadNotFoundWhenEventMissing() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());
        byte[] jpg = new byte[]{0x01};
        String checksum = EvidenceService.sha256Hex(jpg);
        assertThrows(TelemetryEventNotFoundException.class,
                () -> service.upload(deviceId, plainKey, eventId, jpg, "image/jpeg", checksum));
    }

    @Test
    void uploadRejectsChecksumMismatch() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(evidenceRepository.existsByEventId(eventId)).thenReturn(false);
        byte[] jpg = new byte[]{0x01, 0x02};
        String wrong = "0".repeat(64);
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(deviceId, plainKey, eventId, jpg, "image/jpeg", wrong));
        verify(storage, never()).put(anyString(), any(), anyString());
    }
}
