package com.somnguard.telemetry_service.application.usecase;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.adapter.out.storage.EvidenceStoragePort;
import com.somnguard.telemetry_service.domain.exception.EvidenceConflictException;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import com.somnguard.telemetry_service.domain.exception.TelemetryEventNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evidencia 1/evento MVP (HU-API-007 AC-004, ADR-006).
 * Multipart 1 JPG a MinIO {@code somnguard-evidence} con key
 * {@code {device_id}/{YYYY}/{MM}/{DD}/{event_id}.jpg} + {@code checksum_sha256}.
 */
@Service
public class EvidenceService {

    static final String MEDIA_TYPE_CODE = "image_jpeg";
    static final String CONTENT_TYPE_JPEG = "image/jpeg";

    private final TelemetryService telemetryService;
    private final EventRepository eventRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaTypeRepository mediaTypeRepository;
    private final EvidenceStoragePort storage;

    public EvidenceService(TelemetryService telemetryService,
            EventRepository eventRepository,
            EvidenceRepository evidenceRepository,
            MediaTypeRepository mediaTypeRepository,
            EvidenceStoragePort storage) {
        this.telemetryService = telemetryService;
        this.eventRepository = eventRepository;
        this.evidenceRepository = evidenceRepository;
        this.mediaTypeRepository = mediaTypeRepository;
        this.storage = storage;
    }

    @Transactional
    public UUID upload(UUID headerDeviceId, String plainKey, UUID eventId,
            byte[] content, String contentType, String checksumSha256) {
        DeviceEntity device = telemetryService.requireAuthorizedDevice(headerDeviceId, plainKey);
        EventEntity event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new TelemetryEventNotFoundException(
                        "Evento no encontrado: " + eventId));
        if (!device.getId().equals(event.getDeviceId())) {
            throw new DeviceForbiddenException("El evento no pertenece a este dispositivo");
        }
        if (evidenceRepository.existsByEventId(eventId)) {
            throw new EvidenceConflictException("El evento ya tiene evidencia");
        }
        String checksum = requireChecksum(checksumSha256);
        requireJpeg(content, contentType);
        requireChecksumMatch(content, checksum);
        MediaTypeEntity mediaType = mediaTypeRepository.findByCodeAndIsActiveTrue(MEDIA_TYPE_CODE)
                .orElseThrow(() -> new InvalidCatalogReferenceException(
                        "media_type desconocido: " + MEDIA_TYPE_CODE));
        String key = buildKey(device.getId(), event);
        storage.put(key, content, CONTENT_TYPE_JPEG);
        EvidenceEntity evidence = new EvidenceEntity();
        evidence.setId(UUID.randomUUID());
        evidence.setEventId(eventId);
        evidence.setMediaTypeId(mediaType.getId());
        evidence.setMinioKey(key);
        evidence.setSizeBytes((long) content.length);
        evidence.setChecksumSha256(checksum.toLowerCase());
        evidence.setCreatedAt(java.time.OffsetDateTime.now());
        evidence.setCreatedBy(device.getId());
        evidence.setIsActive(true);
        evidenceRepository.save(evidence);
        return evidence.getId();
    }

    private String requireChecksum(String checksum) {
        if (checksum == null || !checksum.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException("checksum_sha256 (64 hex) es obligatorio");
        }
        return checksum;
    }

    private void requireJpeg(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file JPG es requerido");
        }
        if (!CONTENT_TYPE_JPEG.equalsIgnoreCase(contentType)
                && !"image/jpg".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("file debe ser image/jpeg");
        }
    }

    private void requireChecksumMatch(byte[] content, String checksum) {
        String actual = sha256Hex(content);
        if (!actual.equalsIgnoreCase(checksum)) {
            throw new IllegalArgumentException("checksum_sha256 no coincide con el archivo");
        }
    }

    static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(content);
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    static String buildKey(UUID deviceId, EventEntity event) {
        ZonedDateTime occurred = event.getOccurredAt().atZoneSameInstant(ZoneOffset.UTC);
        return String.format("%s/%04d/%02d/%02d/%s.jpg", deviceId,
                occurred.getYear(), occurred.getMonthValue(), occurred.getDayOfMonth(),
                event.getId());
    }
}
