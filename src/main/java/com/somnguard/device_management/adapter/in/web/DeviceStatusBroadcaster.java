package com.somnguard.device_management.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somnguard.device_management.domain.model.DeviceStatusChangedEvent;
import com.somnguard.platform.config.StreamSignalingHandler;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Push WS de estado del device en tiempo real (offline→activo sin esperar 30s).
 * Escucha transiciones de DeviceService.applyStatus y avisa a los viewers
 * suscritos con {type:subscribe-status, device_id}.
 */
@Component
public class DeviceStatusBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceStatusBroadcaster.class);

    private final StreamSignalingHandler signaling;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeviceStatusBroadcaster(StreamSignalingHandler signaling) {
        this.signaling = signaling;
    }

    @Async
    @EventListener
    public void onStatusChanged(DeviceStatusChangedEvent event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "status");
            payload.put("device_id", event.deviceId().toString());
            payload.put("status", event.status());
            payload.put("category", event.category());
            payload.put("reason", event.reason());
            payload.put("at", event.at().toString());
            signaling.broadcastStatus(event.deviceId().toString(), mapper.writeValueAsString(payload));
        } catch (Exception e) {
            LOG.debug("Broadcast status falló: {}", e.getMessage());
        }
    }
}
