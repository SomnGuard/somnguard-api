package com.somnguard.monitoring.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Registro de token push (HU-APP-002 AC-001, HU-API-009 AC-002).
 */
public record RegisterTokenRequest(
        @NotBlank(message = "token es requerido") String token,
        @NotBlank(message = "platform es requerida")
        @Pattern(regexp = "(?i)fcm|apns|webpush", message = "platform inválida (fcm|apns|webpush)")
        String platform,
        String appVersion,
        String locale
) {}
