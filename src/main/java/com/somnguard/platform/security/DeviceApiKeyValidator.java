package com.somnguard.platform.security;

import java.util.UUID;

public interface DeviceApiKeyValidator {
    boolean isValid(UUID deviceId, String plainApiKey);
}
