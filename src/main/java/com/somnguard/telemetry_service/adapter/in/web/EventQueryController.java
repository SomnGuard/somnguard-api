package com.somnguard.telemetry_service.adapter.in.web;

import com.somnguard.platform.security.ApiKeyAuthenticationFilter;
import com.somnguard.platform.security.RequireFeature;
import com.somnguard.telemetry_service.adapter.in.web.dto.EventPageResponse;
import com.somnguard.telemetry_service.application.usecase.EventQueryFilters;
import com.somnguard.telemetry_service.application.usecase.EventQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de eventos (HU-API-008, FEA-TEL-QUERY).
 * Solo lectura para portal/app (JWT); los devices con API key reciben 403.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventQueryController {

    private static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final EventQueryService eventQueryService;

    public EventQueryController(EventQueryService eventQueryService) {
        this.eventQueryService = eventQueryService;
    }

    // AC-001: filtros device, tipo, severidad, fechas + AC-002 paginación.
    @GetMapping
    @RequireFeature({"event.read", "device.read", "device.write", "device.assign"})
    public EventPageResponse list(
            @RequestParam(value = "device_id", required = false) UUID deviceId,
            @RequestParam(value = "event_type_id", required = false) UUID eventTypeId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        boolean admin = isAdmin();
        UUID scopeUser = admin ? null : currentUserId();
        if (!isJwt() && currentDeviceId() != null) {
            scopeUser = SYSTEM_ID;
        }
        return eventQueryService.list(
                new EventQueryFilters(deviceId, eventTypeId, severity, from, to),
                scopeUser, admin, page, pageSize);
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException ex) {
                return SYSTEM_ID;
            }
        }
        return SYSTEM_ID;
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> features = currentFeatures(auth);
        return features.contains("device.write") || features.contains("ROLE_ADMIN");
    }

    private static List<String> currentFeatures(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) {
            Object claim = jwt.getToken().getClaim("features");
            if (claim instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
            return List.of();
        }
        if (auth != null) {
            return auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        }
        return List.of();
    }

    private static boolean isJwt() {
        return SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken;
    }

    private static UUID currentDeviceId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.getPrincipal() instanceof ApiKeyAuthenticationFilter.DevicePrincipal p) {
            return p.deviceId();
        }
        return null;
    }
}
