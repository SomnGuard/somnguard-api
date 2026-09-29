package com.somnguard.device_management.adapter.in.web;

import com.somnguard.device_management.adapter.in.web.dto.StreamSessionResponse;
import com.somnguard.device_management.adapter.in.web.dto.StreamStartResponse;
import com.somnguard.device_management.adapter.in.web.dto.StreamStopRequest;
import com.somnguard.device_management.adapter.in.web.dto.StreamStopResponse;
import com.somnguard.device_management.application.usecase.StreamSessionService;
import com.somnguard.device_management.domain.model.StreamSession;
import com.somnguard.platform.security.RequireFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Streaming en vivo post-MVP (HU-API-012, ADR-013 Propuesta).
 * Solo a demanda: start crea sesión en memoria (TTL 5min), stop es idempotente.
 * Requiere device Activo + ownership (dueño o admin). Rate-limit: 5/min por user (TODO HU-API-012).
 */
@RestController
@RequestMapping("/api/v1/devices/{id}/stream")
@Tag(name = "streaming", description = "Video en vivo WebRTC a demanda (post-MVP)")
public class StreamController {

    private final StreamSessionService sessions;

    public StreamController(StreamSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/start")
    @RequireFeature({"device.read", "device.write", "device.assign"})
    @Operation(summary = "Iniciar sesión en vivo")
    public ResponseEntity<StreamStartResponse> start(@PathVariable("id") UUID id) {
        UUID me = DeviceAuthSupport.currentUserId();
        boolean admin = DeviceAuthSupport.isAdmin();
        StreamSession s = sessions.start(id, me, admin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StreamStartResponse(
                        s.sessionId(), s.deviceId(), s.room(), s.tokenViewer(), sessions.wsPath(), s.expiresAt()));
    }

    @PostMapping("/stop")
    @RequireFeature({"device.read", "device.write", "device.assign"})
    @Operation(summary = "Cerrar sesión en vivo (idempotente)")
    public StreamStopResponse stop(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) StreamStopRequest req) {
        UUID me = DeviceAuthSupport.currentUserId();
        boolean admin = DeviceAuthSupport.isAdmin();
        UUID closed = sessions.stop(id, req == null ? null : req.sessionId(), me, admin);
        return new StreamStopResponse(closed, true);
    }

    @GetMapping("/session")
    @Operation(summary = "Estado de la sesión activa (viewer JWT o poll del Pi con API key)")
    public StreamSessionResponse session(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        StreamSession s;
        if (DeviceAuthSupport.isJwt()) {
            UUID me = DeviceAuthSupport.currentUserId();
            boolean admin = DeviceAuthSupport.isAdmin();
            s = sessions.current(id, me, admin);
        } else {
            UUID headerId = null;
            if (deviceIdHeader != null && !deviceIdHeader.isBlank()) {
                try {
                    headerId = UUID.fromString(deviceIdHeader.trim());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("X-Device-ID debe ser un UUID válido");
                }
            }
            s = sessions.currentForDevice(id, headerId, apiKey);
        }
        return new StreamSessionResponse(
                s.sessionId(), s.deviceId(), s.room(), 1, s.startedAt(), s.expiresAt());
    }
}
