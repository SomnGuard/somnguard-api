package com.somnguard.device_management.adapter.in.web;

import com.somnguard.device_management.application.usecase.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Transición automática Activo -&gt; Offline tras 5 min sin heartbeat (HU-API-006 AC-004/AC-006).
 */
@Component
public class DeviceHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeviceHeartbeatScheduler.class);

    private final DeviceService deviceService;

    public DeviceHeartbeatScheduler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Scheduled(fixedDelayString = "${app.devices.offline-sweep-ms:60000}")
    public void markOfflineDevices() {
        try {
            int count = deviceService.sweepOffline();
            if (count > 0) log.info("devices marcados offline: {}", count);
        } catch (Exception ex) {
            log.warn("Fallo barrido offline de dispositivos: {}", ex.getMessage());
        }
    }
}
