package com.somnguard.device_management.application.usecase;

import com.somnguard.device_management.adapter.in.web.dto.DeviceResponse;
import com.somnguard.device_management.domain.exception.DeviceConflictException;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.device_management.domain.model.StreamSession;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sesiones de streaming en vivo (HU-API-012, ADR-013 Propuesta).
 * En memoria: 1 sesión activa por device, TTL deslizante (cada poll extiende),
 * stop idempotente. Valida device Activo + ownership (dueño o admin) antes de crear.
 */
@Service
public class StreamSessionService {

    private final DeviceService deviceService;
    private final Map<UUID, StreamSession> byDevice = new ConcurrentHashMap<>();
    private final Map<UUID, StreamSession> bySession = new ConcurrentHashMap<>();

    private final long ttlMinutes;
    private final String wsPath;

    public StreamSessionService(
            DeviceService deviceService,
            @Value("${app.stream.session-ttl-minutes:5}") long ttlMinutes,
            @Value("${app.stream.ws-path:/ws/stream}") String wsPath) {
        this.deviceService = deviceService;
        this.ttlMinutes = ttlMinutes;
        this.wsPath = wsPath;
    }

    public synchronized StreamSession start(UUID deviceId, UUID requester, boolean admin) {
        DeviceResponse d = deviceService.get(deviceId);
        enforceOwnership(d, requester, admin);
        if (!DeviceStatus.DEVICE_ACTIVE.code().equalsIgnoreCase(d.status())) {
            throw new DeviceConflictException(
                    "Device no disponible para streaming (requiere Activo, actual: " + d.status() + ")");
        }
        evictExpired(OffsetDateTime.now());
        StreamSession existing = byDevice.get(deviceId);
        if (existing != null) {
            throw new DeviceConflictException("Ya existe una sesión activa para este device");
        }
        OffsetDateTime now = OffsetDateTime.now();
        StreamSession s = new StreamSession(
                UUID.randomUUID(),
                deviceId,
                "somnguard-" + deviceId.toString().substring(0, 8),
                "viewer-" + UUID.randomUUID(),
                requester,
                now,
                now.plusMinutes(ttlMinutes));
        byDevice.put(deviceId, s);
        bySession.put(s.sessionId(), s);
        return s;
    }

    public synchronized StreamSession current(UUID deviceId, UUID requester, boolean admin) {
        DeviceResponse d = deviceService.get(deviceId);
        enforceOwnership(d, requester, admin);
        evictExpired(OffsetDateTime.now());
        StreamSession s = byDevice.get(deviceId);
        if (s == null) {
            throw new DeviceNotFoundException("Sin sesión activa para este device");
        }
        return touch(s);
    }

    /** Poll del Pi (HU-DEVICE-005): valida API key sin efectos y retorna sesión o 404. */
    public synchronized StreamSession currentForDevice(UUID deviceId, UUID headerId, String plainKey) {
        deviceService.verifyDeviceKey(deviceId, headerId, plainKey);
        evictExpired(OffsetDateTime.now());
        StreamSession s = byDevice.get(deviceId);
        if (s == null) {
            throw new DeviceNotFoundException("Sin sesión activa para este device");
        }
        return touch(s);
    }

    /** TTL deslizante: cada poll con sesión viva extiende la expiración (evita congelado a los 5min). */
    private StreamSession touch(StreamSession s) {
        StreamSession fresh = new StreamSession(
                s.sessionId(), s.deviceId(), s.room(), s.tokenViewer(),
                s.requestedBy(), s.startedAt(), OffsetDateTime.now().plusMinutes(ttlMinutes));
        byDevice.put(fresh.deviceId(), fresh);
        bySession.put(fresh.sessionId(), fresh);
        return fresh;
    }

    /** Stop idempotente: cierra por device o por session_id; si no hay nada, igual responde closed:true. */
    public synchronized UUID stop(UUID deviceId, UUID sessionId, UUID requester, boolean admin) {
        DeviceResponse d = deviceService.get(deviceId);
        enforceOwnership(d, requester, admin);
        StreamSession target = null;
        if (sessionId != null) {
            target = bySession.get(sessionId);
        }
        if (target == null) {
            target = byDevice.get(deviceId);
        }
        if (target == null) {
            return null;
        }
        byDevice.remove(target.deviceId(), target);
        bySession.remove(target.sessionId(), target);
        return target.sessionId();
    }

    public String wsPath() {
        return wsPath;
    }

    @Scheduled(fixedDelayString = "${app.stream.cleanup-ms:60000}")
    void sweepExpired() {
        evictExpired(OffsetDateTime.now());
    }

    private void evictExpired(OffsetDateTime now) {
        for (StreamSession s : bySession.values()) {
            if (s.isExpired(now)) {
                byDevice.remove(s.deviceId(), s);
                bySession.remove(s.sessionId(), s);
            }
        }
    }

    private void enforceOwnership(DeviceResponse d, UUID me, boolean admin) {
        if (admin) {
            return;
        }
        if (d.assignedUserId() == null || !d.assignedUserId().equals(me)) {
            throw new DeviceForbiddenException("No tiene acceso a este dispositivo");
        }
    }
}
