package com.somnguard.platform.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.somnguard.device_management.domain.exception.ConfigDeprecatedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConfigDeprecatedMappingTest {

    @Test
    void patchConfigGoneIs410() {
        var handler = new GlobalExceptionHandler();
        var response = handler.handleConfigDeprecated(
                new ConfigDeprecatedException("deprecated"));
        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals("CONFIG_DEPRECATED", response.getBody().error().code());
    }
}
