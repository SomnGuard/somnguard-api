package com.somnguard.device_management.domain.model;

/**
 * Estados de negocio del dispositivo (HU-API-006, RF-DEV-05).
 * Códigos canónicos según {@code parameterization.status} (entity_type='device').
 */
public enum DeviceStatus {
    DEVICE_REGISTERED("DEVICE_REGISTERED", "PENDING"),
    DEVICE_ASSIGNED("DEVICE_ASSIGNED", "PENDING"),
    DEVICE_ACTIVE("DEVICE_ACTIVE", "ACTIVE"),
    DEVICE_OFFLINE("DEVICE_OFFLINE", "INACTIVE"),
    DEVICE_SUSPENDED("DEVICE_SUSPENDED", "INACTIVE"),
    DEVICE_RETIRED("DEVICE_RETIRED", "ARCHIVED");

    private final String code;
    private final String category;

    DeviceStatus(String code, String category) {
        this.code = code;
        this.category = category;
    }

    public String code() { return code; }
    public String category() { return category; }

    public static DeviceStatus fromCode(String code) {
        if (code == null) return null;
        for (DeviceStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
