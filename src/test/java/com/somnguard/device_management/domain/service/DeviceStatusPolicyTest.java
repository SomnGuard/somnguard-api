package com.somnguard.device_management.domain.service;

import com.somnguard.device_management.domain.model.DeviceStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceStatusPolicyTest {

    @Test
    void assignFlow() {
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_REGISTERED, DeviceStatus.DEVICE_ASSIGNED));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_ASSIGNED, DeviceStatus.DEVICE_ACTIVE));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_ACTIVE, DeviceStatus.DEVICE_OFFLINE));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_OFFLINE, DeviceStatus.DEVICE_ACTIVE));
    }

    @Test
    void unassignFlow() {
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_ASSIGNED, DeviceStatus.DEVICE_REGISTERED));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_ACTIVE, DeviceStatus.DEVICE_REGISTERED));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_OFFLINE, DeviceStatus.DEVICE_REGISTERED));
        assertTrue(DeviceStatusPolicy.isUnassignTransition(DeviceStatus.DEVICE_ACTIVE));
        assertFalse(DeviceStatusPolicy.isUnassignTransition(DeviceStatus.DEVICE_SUSPENDED));
    }

    @Test
    void adminFlow() {
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_ACTIVE, DeviceStatus.DEVICE_SUSPENDED));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_SUSPENDED, DeviceStatus.DEVICE_ACTIVE));
        assertTrue(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_SUSPENDED, DeviceStatus.DEVICE_RETIRED));
        assertFalse(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_RETIRED, DeviceStatus.DEVICE_ACTIVE));
        assertFalse(DeviceStatusPolicy.isAllowed(DeviceStatus.DEVICE_REGISTERED, DeviceStatus.DEVICE_ACTIVE));
    }
}
