package com.somnguard.telemetry_service.application.usecase;

import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.adapter.out.storage.EvidenceStoragePort;
import com.somnguard.telemetry_service.domain.exception.TelemetryEventNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura de evidencia para usuario autenticado (JWT).
 * Ownership estricto: el {@code device_id} del evento debe estar asignado
 * activamente al {@code userId} del JWT. Conocer el {@code eventId} no otorga acceso.
 * Solo lectura: no altera el flujo de subida del device.
 */
@Service
public class UserEventEvidenceService {

    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";

    private final EventRepository eventRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaTypeRepository mediaTypeRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final EvidenceStoragePort storage;

    public UserEventEvidenceService(EventRepository eventRepository,
            EvidenceRepository evidenceRepository,
            MediaTypeRepository mediaTypeRepository,
            DeviceAssignmentRepository assignmentRepository,
            EvidenceStoragePort storage) {
        this.eventRepository = eventRepository;
        this.evidenceRepository = evidenceRepository;
        this.mediaTypeRepository = mediaTypeRepository;
        this.assignmentRepository = assignmentRepository;
        this.storage = storage;
    }

    public record EvidenceContent(byte[] bytes, String contentType) {}

    @Transactional(readOnly = true)
    public EvidenceContent getForUser(UUID eventId, UUID userId) {
        EventEntity event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new TelemetryEventNotFoundException(
                        "Evento no encontrado: " + eventId));
        boolean owns = assignmentRepository
                .findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId)
                .stream().anyMatch(a -> event.getDeviceId().equals(a.getDeviceId()));
        if (!owns) {
            throw new DeviceForbiddenException("Sin acceso al dispositivo del evento");
        }
        EvidenceEntity evidence = evidenceRepository.findByEventId(eventId)
                .orElseThrow(() -> new TelemetryEventNotFoundException(
                        "Evento sin evidencia: " + eventId));
        if (Boolean.FALSE.equals(evidence.getIsActive())) {
            throw new TelemetryEventNotFoundException(
                    "Evidencia no disponible: " + eventId);
        }
        String contentType = mediaTypeRepository.findById(evidence.getMediaTypeId())
                .map(m -> m.getMimeType()).orElse(DEFAULT_CONTENT_TYPE);
        byte[] bytes = storage.get(evidence.getMinioKey());
        return new EvidenceContent(bytes, contentType);
    }
}
