package com.somnguard.analytics.adapter.in.web;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsReportResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsSummaryResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsTimelineResponse;
import com.somnguard.analytics.application.port.in.AnalyticsMetricsQuery;
import com.somnguard.analytics.application.port.in.AnalyticsReportQuery;
import com.somnguard.analytics.application.port.in.AnalyticsSummaryQuery;
import com.somnguard.analytics.application.port.in.AnalyticsTimelineQuery;
import com.somnguard.analytics.application.usecase.AnalyticsFilter;
import com.somnguard.platform.security.ApiKeyAuthenticationFilter;
import com.somnguard.platform.security.RequireFeature;
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
 * Consultas analíticas (solo lectura para portal/app con JWT).
 * Los dispositivos con API key no tienen {@code analytics.read/generate} y
 * reciben 403 en el interceptor; además se degradan a ámbito vacío.
 *
 * <p>Limitación documentada: el alcance no-admin usa asignaciones vigentes
 * (igual que {@code GET /api/v1/events}); un evento histórico de un
 * dispositivo reasignado se atribuye al propietario actual.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final AnalyticsTimelineQuery timelineQuery;
    private final AnalyticsMetricsQuery metricsQuery;
    private final AnalyticsSummaryQuery summaryQuery;
    private final AnalyticsReportQuery reportQuery;

    public AnalyticsController(AnalyticsTimelineQuery timelineQuery,
            AnalyticsMetricsQuery metricsQuery,
            AnalyticsSummaryQuery summaryQuery,
            AnalyticsReportQuery reportQuery) {
        this.timelineQuery = timelineQuery;
        this.metricsQuery = metricsQuery;
        this.summaryQuery = summaryQuery;
        this.reportQuery = reportQuery;
    }

    @GetMapping("/timeline")
    @RequireFeature("analytics.read")
    public AnalyticsTimelineResponse timeline(
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
        UUID scopeUser = scopeUser();
        return timelineQuery.timeline(
                new AnalyticsFilter(deviceId, eventTypeId, severity, from, to),
                scopeUser, admin, page, pageSize);
    }

    @GetMapping("/metrics")
    @RequireFeature("analytics.read")
    public AnalyticsMetricsResponse metrics(
            @RequestParam(value = "device_id", required = false) UUID deviceId,
            @RequestParam(value = "event_type_id", required = false) UUID eventTypeId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        boolean admin = isAdmin();
        UUID scopeUser = scopeUser();
        return metricsQuery.metrics(
                new AnalyticsFilter(deviceId, eventTypeId, severity, from, to),
                scopeUser, admin);
    }

    @GetMapping("/summary")
    @RequireFeature("analytics.read")
    public AnalyticsSummaryResponse summary(
            @RequestParam(value = "device_id", required = false) UUID deviceId,
            @RequestParam(value = "event_type_id", required = false) UUID eventTypeId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        boolean admin = isAdmin();
        UUID scopeUser = scopeUser();
        return summaryQuery.summary(
                new AnalyticsFilter(deviceId, eventTypeId, severity, from, to),
                scopeUser, admin);
    }

    @GetMapping("/reports")
    @RequireFeature("analytics.generate")
    public AnalyticsReportResponse reports(
            @RequestParam(value = "device_id", required = false) UUID deviceId,
            @RequestParam(value = "event_type_id", required = false) UUID eventTypeId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        boolean admin = isAdmin();
        UUID scopeUser = scopeUser();
        return reportQuery.report(
                new AnalyticsFilter(deviceId, eventTypeId, severity, from, to),
                scopeUser, admin);
    }

    private static UUID scopeUser() {
        boolean admin = isAdmin();
        UUID scopeUser = admin ? null : currentUserId();
        if (!isJwt() && currentDeviceId() != null) {
            scopeUser = SYSTEM_ID;
        }
        return scopeUser;
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
