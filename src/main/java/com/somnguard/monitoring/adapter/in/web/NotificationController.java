package com.somnguard.monitoring.adapter.in.web;

import com.somnguard.monitoring.adapter.in.web.dto.NotificationDto;
import com.somnguard.monitoring.adapter.in.web.dto.NotificationPageResponse;
import com.somnguard.monitoring.adapter.in.web.dto.PreferenceDto;
import com.somnguard.monitoring.adapter.in.web.dto.RegisterTokenRequest;
import com.somnguard.monitoring.adapter.in.web.dto.UpdatePreferenceRequest;
import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationEntity;
import com.somnguard.monitoring.application.usecase.NotificationService;
import com.somnguard.platform.security.RequireFeature;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificaciones push + in-app (HU-API-009, FEA-MON-NOTIFY).
 * Solo lectura/escritura propia (JWT); features {@code notification.read/send}
 * ya existen en el seed {@code 011_insert_security_features}.
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // AC-002 in-app: bandeja propia paginada (docs: GET /api/v1/notifications).
    @GetMapping("/notifications")
    @RequireFeature({"notification.read", "notification.send"})
    public NotificationPageResponse list(Authentication auth,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        UUID userId = extractUserId(auth);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<NotificationEntity> result =
                notificationService.list(userId, page, pageSize);
        return new NotificationPageResponse(
                result.getContent().stream().map(NotificationDto::from).toList(),
                new NotificationPageResponse.Pagination(Math.max(page, 1), safeSize,
                        result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/notifications/unread-count")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        return ResponseEntity.ok(Map.of(
                "unread_count", notificationService.unreadCount(extractUserId(auth))));
    }

    // AC-003: el canal confirma entrega; el usuario confirma lectura (docs: POST /{id}/read).
    @PostMapping("/notifications/{id}/delivered")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<NotificationDto> markDelivered(Authentication auth,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(NotificationDto.from(
                notificationService.markDelivered(id, extractUserId(auth))));
    }

    @PostMapping("/notifications/{id}/read")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<NotificationDto> markRead(Authentication auth,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(NotificationDto.from(
                notificationService.markRead(id, extractUserId(auth))));
    }

    // HU-APP-002 AC-001: registro de token FCM/APNs al login.
    @PostMapping("/notifications/device-tokens")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<Map<String, String>> registerToken(Authentication auth,
            @Valid @RequestBody RegisterTokenRequest req) {
        UUID userId = extractUserId(auth);
        notificationService.registerToken(userId, req.token(), req.platform(),
                req.appVersion(), req.locale());
        return ResponseEntity.ok(Map.of("message", "Token push registrado"));
    }

    @DeleteMapping("/notifications/device-tokens")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<Void> disableToken(Authentication auth,
            @RequestParam("token") String token) {
        notificationService.disableToken(extractUserId(auth), token);
        return ResponseEntity.noContent().build();
    }

    // AC-004: preferencias propias (canales, silencio, severidad mínima).
    @GetMapping("/users/me/notification-preferences")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<PreferenceDto> getPreferences(Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(PreferenceDto.from(
                notificationService.getOrCreatePreferences(userId, userId)));
    }

    @PutMapping("/users/me/notification-preferences")
    @RequireFeature({"notification.read", "notification.send"})
    public ResponseEntity<PreferenceDto> updatePreferences(Authentication auth,
            @Valid @RequestBody UpdatePreferenceRequest req) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(PreferenceDto.from(notificationService.updatePreferences(
                userId, req.pushEnabled(), req.emailEnabled(), req.inAppEnabled(),
                req.quietHoursStart(), req.quietHoursEnd(),
                req.timezone(), req.minSeverityCode())));
    }

    // AC-003: reintento manual de push pendientes (backoff, max 3); normalemnte lo corre un scheduler.
    @PostMapping("/notifications/retry")
    @RequireFeature({"notification.send"})
    public ResponseEntity<Map<String, Integer>> retryPending() {
        return ResponseEntity.ok(Map.of(
                "processed", notificationService.retryPending(OffsetDateTime.now())));
    }

    private UUID extractUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalArgumentException("No autenticado");
    }
}
