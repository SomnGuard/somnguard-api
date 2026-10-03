package com.somnguard.monitoring.adapter.in.web.dto;

import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Actualización parcial de preferencias (HU-API-009 AC-004).
 * {@code quietHours*} aceptan {@code HH:mm}; {@code null} limpia el horario de silencio.
 */
public record UpdatePreferenceRequest(
        Boolean pushEnabled,
        Boolean emailEnabled,
        Boolean inAppEnabled,
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime quietHoursStart,
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime quietHoursEnd,
        String timezone,
        @Pattern(regexp = "(?i)info|warning|high|critical|leve|moderada|severa|critica",
                message = "min_severity inválida") String minSeverityCode
) {}
