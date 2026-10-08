package com.somnguard.device_management.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.in.web.dto.DeviceResponse;
import com.somnguard.device_management.domain.exception.DeviceConflictException;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamSessionServiceTest {

    @Mock
    DeviceService deviceService;

    StreamSessionService service;

    UUID deviceId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new StreamSessionService(deviceService, 5, "/ws/stream");
    }

    private DeviceResponse active(UUID assignedTo) {
        return new DeviceResponse(deviceId, "SN-1", "1.0", "DEVICE_ACTIVE", "ACTIVE",
                OffsetDateTime.now(), "127.0.0.1", assignedTo, OffsetDateTime.now(),
                "CLAIM", OffsetDateTime.now(), OffsetDateTime.now(), 1, false, OffsetDateTime.now());
    }

    @Test
    void startOk() {
        when(deviceService.get(deviceId)).thenReturn(active(owner));
        var s = service.start(deviceId, owner, false);
        assertNotNull(s.sessionId());
        assertEquals(deviceId, s.deviceId());
        assertTrue(s.room().startsWith("somnguard-"));
    }

    @Test
    void startSecondConflicts() {
        when(deviceService.get(deviceId)).thenReturn(active(owner));
        service.start(deviceId, owner, false);
        assertThrows(DeviceConflictException.class, () -> service.start(deviceId, owner, false));
    }

    @Test
    void startOfflineConflicts() {
        var offline = new DeviceResponse(deviceId, "SN-1", "1.0", "DEVICE_OFFLINE", "INACTIVE",
                OffsetDateTime.now(), "127.0.0.1", owner, OffsetDateTime.now(),
                "CLAIM", OffsetDateTime.now(), OffsetDateTime.now(), 1, false, OffsetDateTime.now());
        when(deviceService.get(deviceId)).thenReturn(offline);
        assertThrows(DeviceConflictException.class, () -> service.start(deviceId, owner, false));
    }

    @Test
    void startForbiddenWithoutOwnership() {
        when(deviceService.get(deviceId)).thenReturn(active(UUID.randomUUID()));
        assertThrows(DeviceForbiddenException.class, () -> service.start(deviceId, owner, false));
    }

    @Test
    void stopIdempotent() {
        when(deviceService.get(deviceId)).thenReturn(active(owner));
        var s = service.start(deviceId, owner, false);
        var closed = service.stop(deviceId, s.sessionId(), owner, false);
        assertEquals(s.sessionId(), closed);
        var closedAgain = service.stop(deviceId, s.sessionId(), owner, false);
        assertEquals(null, closedAgain);
    }
}
