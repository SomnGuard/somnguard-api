package com.somnguard.device_management.application.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceApiKeyServiceTest {

    private final DeviceApiKeyService service = new DeviceApiKeyService();

    @Test
    void generateVerifyAndRotate() {
        String key1 = service.generatePlainKey();
        String hash1 = service.hash(key1);
        assertTrue(service.verify(key1, hash1));
        assertFalse(service.verify("otra-clave", hash1));
        String key2 = service.generatePlainKey();
        assertNotEquals(key1, key2);
        assertFalse(service.verify(key1, service.hash(key2)));
    }
}
