package com.somnguard.telemetry_service.application.usecase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.adapter.out.storage.EvidenceStoragePort;
import com.somnguard.telemetry_service.domain.exception.EvidenceStorageException;
import com.somnguard.telemetry_service.domain.exception.TelemetryEventNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEventEvidenceServiceTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    EvidenceRepository evidenceRepository;
    @Mock
    MediaTypeRepository mediaTypeRepository;
    @Mock
    DeviceAssignmentRepository assignmentRepository;
    @Mock
    EvidenceStoragePort storage;

    UserEventEvidenceService service;

    final UUID userId = UUID.randomUUID();
    final UUID deviceId = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();
    final UUID mediaTypeId = UUID.randomUUID();
    final byte[] jpg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @BeforeEach
    void setup() {
        service = new UserEventEvidenceService(eventRepository, evidenceRepository,
                mediaTypeRepository, assignmentRepository, storage);
    }

    private EventEntity event() {
        EventEntity event = new EventEntity();
        event.setId(eventId);
        event.setDeviceId(deviceId);
        return event;
    }

    private EvidenceEntity evidence(boolean active) {
        EvidenceEntity evidence = new EvidenceEntity();
        evidence.setId(UUID.randomUUID());
        evidence.setEventId(eventId);
        evidence.setMediaTypeId(mediaTypeId);
        evidence.setMinioKey(deviceId + "/2026/10/06/" + eventId + ".jpg");
        evidence.setIsActive(active);
        return evidence;
    }

    private DeviceAssignmentEntity assignment(UUID user, UUID device) {
        DeviceAssignmentEntity entity = new DeviceAssignmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(user);
        entity.setDeviceId(device);
        return entity;
    }

    private MediaTypeEntity mediaType() {
        MediaTypeEntity media = new MediaTypeEntity();
        media.setId(mediaTypeId);
        media.setCode("image_jpeg");
        media.setMimeType("image/jpeg");
        return media;
    }

    @Test
    void ownerWithEvidenceReturnsBytes() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(List.of(assignment(userId, deviceId)));
        when(evidenceRepository.findByEventId(eventId))
                .thenReturn(Optional.of(evidence(true)));
        when(mediaTypeRepository.findById(mediaTypeId))
                .thenReturn(Optional.of(mediaType()));
        when(storage.get(any())).thenReturn(jpg);

        UserEventEvidenceService.EvidenceContent content =
                service.getForUser(eventId, userId);

        assertArrayEquals(jpg, content.bytes());
        assertEquals("image/jpeg", content.contentType());
        verify(storage).get(deviceId + "/2026/10/06/" + eventId + ".jpg");
    }

    @Test
    void eventWithoutEvidenceReturns404() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(List.of(assignment(userId, deviceId)));
        when(evidenceRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThrows(TelemetryEventNotFoundException.class,
                () -> service.getForUser(eventId, userId));
        verify(storage, never()).get(any());
    }

    @Test
    void otherUserDeviceIsRejected() {
        UUID otherDevice = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(List.of(assignment(userId, otherDevice)));

        assertThrows(DeviceForbiddenException.class,
                () -> service.getForUser(eventId, userId));
        verify(evidenceRepository, never()).findByEventId(any());
        verify(storage, never()).get(any());
    }

    @Test
    void unknownEventReturns404() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.empty());

        assertThrows(TelemetryEventNotFoundException.class,
                () -> service.getForUser(eventId, userId));
    }

    @Test
    void inactiveEvidenceIsRejected() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(List.of(assignment(userId, deviceId)));
        when(evidenceRepository.findByEventId(eventId))
                .thenReturn(Optional.of(evidence(false)));

        assertThrows(TelemetryEventNotFoundException.class,
                () -> service.getForUser(eventId, userId));
        verify(storage, never()).get(any());
    }

    @Test
    void missingObjectInStorageIsControlledError() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(List.of(assignment(userId, deviceId)));
        when(evidenceRepository.findByEventId(eventId))
                .thenReturn(Optional.of(evidence(true)));
        when(mediaTypeRepository.findById(mediaTypeId))
                .thenReturn(Optional.of(mediaType()));
        when(storage.get(any())).thenThrow(
                new EvidenceStorageException("No se pudo leer evidencia", null));

        assertThrows(EvidenceStorageException.class,
                () -> service.getForUser(eventId, userId));
    }

    @Test
    void ownershipCannotBeBypassedWithIds() {
        // La firma solo recibe (eventId, userId del JWT): no hay deviceId/userId
        // manipulable por el frontend; sin assignment activo siempre es 403.
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(List.of());

        assertThrows(DeviceForbiddenException.class,
                () -> service.getForUser(eventId, userId));
    }
}
