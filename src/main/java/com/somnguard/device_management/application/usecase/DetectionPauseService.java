package com.somnguard.device_management.application.usecase;

import com.somnguard.device_management.adapter.in.web.dto.DeviceResponse;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Pausa manual de detección (botón portal). Memoria del API: el Pi la lee en
 * el poll GET /stream/session (≤5s). Prioritaria sobre presencia en el device;
 * solo se limpia con reanudar o reinicio del Pi. Sin migración de BD.
 */
@Service
public class DetectionPauseService {

    private final DeviceService deviceService;
    private final Map<UUID, Boolean> paused = new ConcurrentHashMap<>();

    public DetectionPauseService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public boolean isPaused(UUID deviceId) {
        return Boolean.TRUE.equals(paused.get(deviceId));
    }

    /** Lectura del Pi (API key, sin efectos): valida credencial y retorna flag. */
    public boolean readForDevice(UUID deviceId, UUID headerId, String plainKey) {
        deviceService.verifyDeviceKey(deviceId, headerId, plainKey);
        return isPaused(deviceId);
    }
    public boolean setPaused(UUID deviceId, boolean value, UUID requester, boolean admin) {
        checkOwnership(deviceId, requester, admin);
        if (value) {
            paused.put(deviceId, Boolean.TRUE);
        } else {
            paused.remove(deviceId);
        }
        return value;
    }

    public boolean readForViewer(UUID deviceId, UUID requester, boolean admin) {
        checkOwnership(deviceId, requester, admin);
        return isPaused(deviceId);
    }

    private void checkOwnership(UUID deviceId, UUID requester, boolean admin) {
        if (admin) {
            return;
        }
        DeviceResponse d = deviceService.get(deviceId);
        if (d.assignedUserId() == null || !d.assignedUserId().equals(requester)) {
            throw new DeviceForbiddenException("No tiene acceso a este dispositivo");
        }
    }
}
