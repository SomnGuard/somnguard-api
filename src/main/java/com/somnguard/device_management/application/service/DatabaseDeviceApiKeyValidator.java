package com.somnguard.device_management.application.service;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.platform.security.DeviceApiKeyValidator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDeviceApiKeyValidator implements DeviceApiKeyValidator {

    private final DeviceRepository deviceRepository;
    private final DeviceApiKeyService apiKeyService;

    public DatabaseDeviceApiKeyValidator(DeviceRepository deviceRepository, DeviceApiKeyService apiKeyService) {
        this.deviceRepository = deviceRepository;
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean isValid(UUID deviceId, String plainApiKey) {
        if (deviceId == null || plainApiKey == null || plainApiKey.isBlank()) {
            return false;
        }
        Optional<DeviceEntity> deviceOpt = deviceRepository.findByIdAndDeletedAtIsNull(deviceId);
        if (deviceOpt.isEmpty()) {
            return false;
        }
        DeviceEntity device = deviceOpt.get();
        if (Boolean.FALSE.equals(device.getIsActive())) {
            return false;
        }
        return apiKeyService.verify(plainApiKey, device.getApiKeyHash());
    }
}
