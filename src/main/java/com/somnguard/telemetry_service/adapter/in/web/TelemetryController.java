package com.somnguard.telemetry_service.adapter.in.web;

import com.somnguard.telemetry_service.adapter.in.web.dto.EvidenceUploadResponse;
import com.somnguard.telemetry_service.adapter.in.web.dto.TelemetryBatchRequest;
import com.somnguard.telemetry_service.adapter.in.web.dto.TelemetryBatchResponse;
import com.somnguard.telemetry_service.application.usecase.EvidenceService;
import com.somnguard.telemetry_service.application.usecase.TelemetryService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingesta idempotente de eventos y evidencia (HU-API-007, FEA-TEL-INGEST).
 * Auth {@code X-Device-ID + X-API-Key} (sin JWT); la verificación real vive en la capa
 * de servicio, igual que {@code POST /devices/{id}/heartbeat} (HU-API-006).
 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final EvidenceService evidenceService;

    public TelemetryController(TelemetryService telemetryService, EvidenceService evidenceService) {
        this.telemetryService = telemetryService;
        this.evidenceService = evidenceService;
    }

    // AC-001/002/003/005/006/007: solo metadata JSON {"events":[]}, lote máx 100.
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TelemetryBatchResponse> ingest(
            @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody TelemetryBatchRequest request) {
        TelemetryBatchResponse response = telemetryService.ingest(
                parseDeviceId(deviceIdHeader), apiKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // AC-004: 1 evidencia/evento MVP, multipart 1 JPG + checksum_sha256 obligatorio.
    @PostMapping(value = "/events/{id}/evidence",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceUploadResponse> uploadEvidence(
            @PathVariable("id") UUID eventId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "checksum_sha256", required = false) String checksumSha256) {
        UUID evidenceId = evidenceService.upload(parseDeviceId(deviceIdHeader), apiKey, eventId,
                readBytes(file), file.getContentType(), checksumSha256);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EvidenceUploadResponse(evidenceId));
    }

    private UUID parseDeviceId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("X-Device-ID debe ser un UUID válido");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file JPG es requerido");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo de evidencia");
        }
    }
}
