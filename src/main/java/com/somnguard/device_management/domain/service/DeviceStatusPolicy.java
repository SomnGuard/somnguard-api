package com.somnguard.device_management.domain.service;

import com.somnguard.device_management.domain.model.DeviceStatus;
import java.util.Map;
import java.util.Set;

/**
 * State machine del dispositivo (HU-API-006 AC-004, es-device.mmd, RF-DEV-05).
 * Incluye transiciones de desasociación (* -&gt; REGISTERED vía unassign).
 */
public final class DeviceStatusPolicy {

    private static final Map<DeviceStatus, Set<DeviceStatus>> ALLOWED = Map.of(
            DeviceStatus.DEVICE_REGISTERED, Set.of(
                    DeviceStatus.DEVICE_ASSIGNED, DeviceStatus.DEVICE_RETIRED),
            DeviceStatus.DEVICE_ASSIGNED, Set.of(
                    DeviceStatus.DEVICE_ACTIVE, DeviceStatus.DEVICE_REGISTERED),
            DeviceStatus.DEVICE_ACTIVE, Set.of(
                    DeviceStatus.DEVICE_OFFLINE, DeviceStatus.DEVICE_SUSPENDED,
                    DeviceStatus.DEVICE_REGISTERED, DeviceStatus.DEVICE_RETIRED),
            DeviceStatus.DEVICE_OFFLINE, Set.of(
                    DeviceStatus.DEVICE_ACTIVE, DeviceStatus.DEVICE_SUSPENDED,
                    DeviceStatus.DEVICE_REGISTERED, DeviceStatus.DEVICE_RETIRED),
            DeviceStatus.DEVICE_SUSPENDED, Set.of(
                    DeviceStatus.DEVICE_ACTIVE, DeviceStatus.DEVICE_RETIRED),
            DeviceStatus.DEVICE_RETIRED, Set.of());

    private DeviceStatusPolicy() {}

    public static boolean isAllowed(DeviceStatus from, DeviceStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isUnassignTransition(DeviceStatus from) {
        return from == DeviceStatus.DEVICE_ASSIGNED
                || from == DeviceStatus.DEVICE_ACTIVE
                || from == DeviceStatus.DEVICE_OFFLINE;
    }
}
