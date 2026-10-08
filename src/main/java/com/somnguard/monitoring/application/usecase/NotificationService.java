package com.somnguard.monitoring.application.usecase;

import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.monitoring.adapter.out.persistence.entity.DeviceTokenEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationStatusAuditEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationTemplateEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.UserNotificationPreferenceEntity;
import com.somnguard.monitoring.adapter.out.persistence.repository.DeviceTokenRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.NotificationRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.NotificationStatusAuditRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.NotificationTemplateRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.UserNotificationPreferenceRepository;
import com.somnguard.monitoring.application.port.out.PushSender;
import com.somnguard.monitoring.domain.exception.NotificationNotFoundException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones de eventos críticos (HU-API-009, FEA-MON-NOTIFY).
 *
 * <p>AC-001: trigger tras persistir evento/alerta crítico. Dispara si
 * {@code severity = critical} o si {@code event_type} está en la lista crítica
 * (EV-SOM-05, EV-DIS-02, EV-DIS-04, EV-CIN-01/02): el device reporta DIS como
 * MODERADA/high y CIN como INFO, así que filtrar solo por severidad perdería
 * 4 de los 5 códigos del AC.
 *
 * <p>AC-002: plantilla por {@code event_type + severity} y canal (push/in_app).
 * AC-003: tracking {@code sent -> delivered -> read} + reintentos exponenciales (max 3).
 * AC-004: preferencias (canales, horario silencio, severidad mínima).
 */
@Service
public class NotificationService {

    private static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Map<String, Integer> SEVERITY_RANK =
            Map.of("info", 1, "warning", 2, "high", 3, "critical", 4);

    private static final String ST_PENDING = "NOTIFICATION_PENDING";
    private static final String ST_SENT = "NOTIFICATION_SENT";
    private static final String ST_DELIVERED = "NOTIFICATION_DELIVERED";
    private static final String ST_READ = "NOTIFICATION_READ";
    private static final String ST_FAILED = "NOTIFICATION_FAILED";
    private static final String CAT_PENDING = "PENDING";
    private static final String CAT_ACTIVE = "ACTIVE";
    private static final String CAT_ERROR = "ERROR";

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final NotificationStatusAuditRepository auditRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final PushSender pushSender;
    private final Set<String> criticalEventTypes;
    private final int maxRetries;

    public NotificationService(NotificationRepository notificationRepository,
            NotificationTemplateRepository templateRepository,
            DeviceTokenRepository deviceTokenRepository,
            UserNotificationPreferenceRepository preferenceRepository,
            NotificationStatusAuditRepository auditRepository,
            DeviceAssignmentRepository assignmentRepository,
            PushSender pushSender,
            @Value("${app.notifications.critical-event-types:EV-SOM-05,EV-DIS-02,EV-DIS-04,EV-CIN-01,EV-CIN-02}")
            Set<String> criticalEventTypes,
            @Value("${app.notifications.max-retries:3}") int maxRetries) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.preferenceRepository = preferenceRepository;
        this.auditRepository = auditRepository;
        this.assignmentRepository = assignmentRepository;
        this.pushSender = pushSender;
        this.criticalEventTypes = criticalEventTypes;
        this.maxRetries = maxRetries;
    }

    /**
     * AC-001: invocado por {@code TelemetryService} tras persistir evento + alert_log.
     * No lanza si no hay dueño o si las preferencias lo filtran (notificar es best-effort).
     */
    @Transactional
    public List<NotificationEntity> triggerCriticalEvent(UUID alertLogId, UUID deviceId,
            String eventTypeCode, String severityCode, UUID actor) {
        List<NotificationEntity> created = new ArrayList<>();
        if (!isCritical(eventTypeCode, severityCode)) {
            return created;
        }
        UUID ownerId = assignmentRepository
                .findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId)
                .map(a -> a.getUserId()).orElse(null);
        if (ownerId == null) {
            return created;
        }
        UserNotificationPreferenceEntity prefs = getOrCreatePreferences(ownerId, actor);
        if (!meetsMinSeverity(severityCode, prefs.getMinSeverityCode())) {
            return created;
        }
        // Entrega única con ruta inteligente: si el usuario tiene app (token push
        // activo) la novedad va por push; si solo usa el portal, va in_app.
        // Sin token y con in_app apagado, la push queda diferida (reintento AC-003).
        String canonicalSeverity = canonicalSeverity(severityCode);
        boolean wantPush = Boolean.TRUE.equals(prefs.getPushEnabled());
        boolean wantInApp = Boolean.TRUE.equals(prefs.getInAppEnabled());
        List<DeviceTokenEntity> tokens = wantPush ? deviceTokenRepository
                .findByUserIdAndDeletedAtIsNullAndIsActiveTrue(ownerId) : List.of();
        String channel = null;
        if (wantPush && !tokens.isEmpty()) {
            channel = "push";
        } else if (wantInApp) {
            channel = "in_app";
        } else if (wantPush) {
            channel = "push";
        }
        if (channel == null) {
            return created;
        }
        created.add(createAndDeliver(ownerId, alertLogId, eventTypeCode,
                canonicalSeverity, channel, deviceId, prefs, actor, tokens));
        return created;
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> list(UUID userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
    }

    // AC-003: delivered lo confirma el canal/dispositivo; read el usuario (in-app).
    @Transactional
    public NotificationEntity markDelivered(UUID notificationId, UUID userId) {
        NotificationEntity entity = requireOwned(notificationId, userId);
        if (ST_READ.equals(entity.getStatus()) || ST_DELIVERED.equals(entity.getStatus())) {
            return entity;
        }
        transition(entity, ST_DELIVERED, CAT_ACTIVE, userId, "{\"by\":\"channel\"}");
        entity.setDeliveredAt(OffsetDateTime.now());
        if (entity.getSentAt() == null) {
            entity.setSentAt(entity.getDeliveredAt());
        }
        return notificationRepository.save(entity);
    }

    @Transactional
    public NotificationEntity markRead(UUID notificationId, UUID userId) {
        NotificationEntity entity = requireOwned(notificationId, userId);
        if (ST_READ.equals(entity.getStatus())) {
            return entity;
        }
        transition(entity, ST_READ, CAT_ACTIVE, userId, "{\"by\":\"user\"}");
        OffsetDateTime now = OffsetDateTime.now();
        entity.setReadAt(now);
        if (entity.getDeliveredAt() == null) {
            entity.setDeliveredAt(now);
        }
        if (entity.getSentAt() == null) {
            entity.setSentAt(now);
        }
        return notificationRepository.save(entity);
    }

    // AC-003: reintentos exponenciales 2^n minutos (1, 2, 4...), tope maxRetries (def. 3).
    @Transactional
    public int retryPending(OffsetDateTime now) {
        List<NotificationEntity> due = notificationRepository
                .findByStatusAndNextRetryAtBeforeAndDeletedAtIsNull(ST_FAILED, now);
        int processed = 0;
        for (NotificationEntity entity : due) {
            if (entity.getRetryCount() != null && entity.getRetryCount() >= maxRetries) {
                continue;
            }
            if (!"push".equals(entity.getChannel())) {
                continue;
            }
            List<DeviceTokenEntity> tokens = deviceTokenRepository
                    .findByUserIdAndDeletedAtIsNullAndIsActiveTrue(entity.getUserId());
            if (tokens.isEmpty()) {
                scheduleNextRetry(entity, "sin device_token activo");
                continue;
            }
            PushSender.PushResult result = pushSender.send(entity.getUserId(),
                    tokens.get(0).getToken(), entity.getTitle(), entity.getMessage());
            if (result.ok()) {
                transition(entity, ST_SENT, CAT_ACTIVE, SYSTEM_ID, "{\"by\":\"retry\"}");
                entity.setSentAt(OffsetDateTime.now());
                entity.setProviderMessageId(result.providerMessageId());
                entity.setErrorMessage(null);
                entity.setNextRetryAt(null);
            } else {
                scheduleNextRetry(entity, result.error());
            }
            notificationRepository.save(entity);
            processed++;
        }
        return processed;
    }

    // HU-APP-002 AC-001: registro/upsert de token FCM/APNs al login.
    @Transactional
    public DeviceTokenEntity registerToken(UUID userId, String token, String platform,
            String appVersion, String locale) {
        String normalizedPlatform = tokenPlatform(platform);
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("token FCM/APNs es requerido");
        }
        OffsetDateTime now = OffsetDateTime.now();
        DeviceTokenEntity entity = deviceTokenRepository
                .findByTokenAndDeletedAtIsNull(trimmed).orElseGet(DeviceTokenEntity::new);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(now);
            entity.setCreatedBy(userId);
        }
        entity.setUserId(userId);
        entity.setToken(trimmed);
        entity.setPlatform(normalizedPlatform);
        entity.setAppVersion(appVersion);
        entity.setLocale(locale);
        entity.setIsActive(true);
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        entity.setLastSeenAt(now);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(userId);
        return deviceTokenRepository.save(entity);
    }

    @Transactional
    public void disableToken(UUID userId, String token) {
        deviceTokenRepository.findByTokenAndDeletedAtIsNull(
                token == null ? "" : token.trim()).ifPresent(entity -> {
                    if (!userId.equals(entity.getUserId())) {
                        throw new NotificationNotFoundException(entity.getId());
                    }
                    entity.setIsActive(false);
                    entity.setUpdatedAt(OffsetDateTime.now());
                    entity.setUpdatedBy(userId);
                    deviceTokenRepository.save(entity);
                });
    }

    // AC-004: preferencias con defaults (push+in_app on, email off, min critical, sin silencio).
    @Transactional
    public UserNotificationPreferenceEntity getOrCreatePreferences(UUID userId, UUID actor) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            UserNotificationPreferenceEntity prefs = new UserNotificationPreferenceEntity();
            prefs.setUserId(userId);
            prefs.setPushEnabled(true);
            prefs.setEmailEnabled(false);
            prefs.setInAppEnabled(true);
            prefs.setMinSeverityCode("critical");
            prefs.setTimezone("America/Bogota");
            prefs.setCreatedAt(now);
            prefs.setCreatedBy(actor == null ? userId : actor);
            prefs.setUpdatedAt(now);
            prefs.setUpdatedBy(actor == null ? userId : actor);
            return preferenceRepository.save(prefs);
        });
    }

    @Transactional
    public UserNotificationPreferenceEntity updatePreferences(UUID userId,
            Boolean pushEnabled, Boolean emailEnabled, Boolean inAppEnabled,
            java.time.LocalTime quietStart, java.time.LocalTime quietEnd,
            String timezone, String minSeverityCode) {
        UserNotificationPreferenceEntity prefs = getOrCreatePreferences(userId, userId);
        if (pushEnabled != null) {
            prefs.setPushEnabled(pushEnabled);
        }
        if (emailEnabled != null) {
            prefs.setEmailEnabled(emailEnabled);
        }
        if (inAppEnabled != null) {
            prefs.setInAppEnabled(inAppEnabled);
        }
        prefs.setQuietHoursStart(quietStart);
        prefs.setQuietHoursEnd(quietEnd);
        if (timezone != null && !timezone.isBlank()) {
            prefs.setTimezone(timezone.trim());
        }
        if (minSeverityCode != null && !minSeverityCode.isBlank()) {
            String canonical = canonicalSeverity(minSeverityCode.trim());
            if (!SEVERITY_RANK.containsKey(canonical)) {
                throw new IllegalArgumentException(
                        "min_severity inválida: " + minSeverityCode);
            }
            prefs.setMinSeverityCode(canonical);
        }
        prefs.setUpdatedAt(OffsetDateTime.now());
        prefs.setUpdatedBy(userId);
        return preferenceRepository.save(prefs);
    }

    private NotificationEntity createAndDeliver(UUID ownerId, UUID alertLogId,
            String eventTypeCode, String severityCode, String channel, UUID deviceId,
            UserNotificationPreferenceEntity prefs, UUID actor,
            List<DeviceTokenEntity> tokens) {
        NotificationTemplateEntity template = templateRepository
                .findByEventTypeCodeAndSeverityCodeAndChannelAndIsActiveTrue(
                        eventTypeCode, severityCode, channel).orElse(null);
        String title = template == null ? defaultTitle(eventTypeCode)
                : render(template.getTitleTemplate(), eventTypeCode, severityCode, deviceId);
        String body = template == null ? defaultBody(eventTypeCode, severityCode)
                : render(template.getBodyTemplate(), eventTypeCode, severityCode, deviceId);
        OffsetDateTime now = OffsetDateTime.now();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(ownerId);
        entity.setAlertLogId(alertLogId);
        entity.setTemplateId(template == null ? null : template.getId());
        entity.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        entity.setMessage(body);
        entity.setChannel(channel);
        entity.setIsActive(true);
        entity.setRetryCount((short) 0);
        entity.setCreatedAt(now);
        entity.setCreatedBy(actor == null ? SYSTEM_ID : actor);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor == null ? SYSTEM_ID : actor);
        entity.setVersion(1);
        // La notificación debe persistir ANTES que su auditoría: la FK
        // notification_status_audit -> notification lo exige y, en la misma
        // transacción de ingesta, una violación marcaría rollback-only todo el lote.
        entity.setStatus(ST_PENDING);
        entity.setStatusCategory(CAT_PENDING);
        NotificationEntity saved = notificationRepository.save(entity);
        writeAudit(saved.getId(), null, ST_PENDING, null, CAT_PENDING, actor,
                "{\"by\":\"trigger\"}");

        if ("push".equals(channel)) {
            deliverPush(saved, prefs, tokens);
        } else {
            transition(saved, ST_SENT, CAT_ACTIVE, actor, "{\"channel\":\"in_app\"}");
            saved.setSentAt(now);
            saved.setDeliveredAt(now);
        }
        return notificationRepository.save(saved);
    }

    private void deliverPush(NotificationEntity entity,
            UserNotificationPreferenceEntity prefs, List<DeviceTokenEntity> tokens) {
        if (isQuietNow(prefs)) {
            transition(entity, ST_PENDING, CAT_PENDING, SYSTEM_ID, "{\"deferred\":\"quiet_hours\"}");
            entity.setNextRetryAt(quietEndToday(prefs));
            entity.setErrorMessage("En horario de silencio, reintentando al finalizar");
            return;
        }
        List<DeviceTokenEntity> active = tokens == null ? List.of()
                : tokens.stream().filter(t -> Boolean.TRUE.equals(t.getIsActive())).toList();
        if (active.isEmpty()) {
            active = deviceTokenRepository
                    .findByUserIdAndDeletedAtIsNullAndIsActiveTrue(entity.getUserId());
        }
        if (active.isEmpty()) {
            transition(entity, ST_FAILED, CAT_ERROR, SYSTEM_ID, "{\"deferred\":\"no_token\"}");
            entity.setErrorMessage("Sin device_token activo para push");
            entity.setNextRetryAt(OffsetDateTime.now().plusMinutes(1));
            return;
        }
        PushSender.PushResult result = pushSender.send(entity.getUserId(),
                active.get(0).getToken(), entity.getTitle(), entity.getMessage());
        if (result.ok()) {
            transition(entity, ST_SENT, CAT_ACTIVE, SYSTEM_ID, "{\"channel\":\"push\"}");
            entity.setSentAt(OffsetDateTime.now());
            entity.setProviderMessageId(result.providerMessageId());
        } else {
            scheduleNextRetry(entity, result.error());
        }
    }

    private void scheduleNextRetry(NotificationEntity entity, String error) {
        short current = entity.getRetryCount() == null ? 0 : entity.getRetryCount();
        short next = (short) (current + 1);
        entity.setRetryCount(next);
        entity.setErrorMessage(error == null ? "Fallo de envío push" : error);
        if (next >= maxRetries) {
            transition(entity, ST_FAILED, CAT_ERROR, SYSTEM_ID, "{\"retries\":\"exhausted\"}");
            entity.setNextRetryAt(null);
        } else {
            long delayMinutes = 1L << current;
            entity.setNextRetryAt(OffsetDateTime.now().plusMinutes(delayMinutes));
            if (!ST_FAILED.equals(entity.getStatus())) {
                transition(entity, ST_FAILED, CAT_ERROR, SYSTEM_ID,
                        "{\"retry\":" + next + "}");
            }
        }
    }

    private void transition(NotificationEntity entity, String toStatus,
            String toCategory, UUID actor, String contextJson) {
        writeAudit(entity.getId(), entity.getStatus(), toStatus,
                entity.getStatusCategory(), toCategory, actor, contextJson);
        entity.setStatus(toStatus);
        entity.setStatusCategory(toCategory);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(actor == null ? SYSTEM_ID : actor);
    }

    private void writeAudit(UUID notificationId, String fromStatus, String toStatus,
            String fromCategory, String toCategory, UUID actor, String contextJson) {
        NotificationStatusAuditEntity audit = new NotificationStatusAuditEntity();
        audit.setNotificationId(notificationId);
        audit.setFromStatus(fromStatus);
        audit.setToStatus(toStatus);
        audit.setFromCategory(fromCategory);
        audit.setToCategory(toCategory);
        audit.setChangedBy(actor == null ? SYSTEM_ID : actor);
        audit.setChangedAt(OffsetDateTime.now());
        audit.setContextJson(contextJson);
        auditRepository.save(audit);
    }

    private NotificationEntity requireOwned(UUID notificationId, UUID userId) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (entity.getDeletedAt() != null || !userId.equals(entity.getUserId())) {
            throw new NotificationNotFoundException(notificationId);
        }
        return entity;
    }

    boolean isCritical(String eventTypeCode, String severityCode) {
        if ("critical".equalsIgnoreCase(canonicalSeverity(severityCode))) {
            return true;
        }
        return eventTypeCode != null && criticalEventTypes.contains(eventTypeCode.trim());
    }

    private boolean meetsMinSeverity(String eventSeverity, String minSeverity) {
        int eventRank = SEVERITY_RANK.getOrDefault(
                canonicalSeverity(eventSeverity), 0);
        int minRank = SEVERITY_RANK.getOrDefault(
                canonicalSeverity(minSeverity == null ? "critical" : minSeverity), 4);
        return eventRank >= minRank;
    }

    private String canonicalSeverity(String raw) {
        if (raw == null) {
            return "info";
        }
        String upper = raw.trim().toUpperCase();
        switch (upper) {
            case "INFO":
            case "LEVE":
                return "info";
            case "WARNING":
            case "MODERADA":
                return "warning";
            case "HIGH":
            case "SEVERA":
                return "high";
            case "CRITICAL":
            case "CRITICA":
                return "critical";
            default:
                return raw.trim().toLowerCase();
        }
    }

    private boolean isQuietNow(UserNotificationPreferenceEntity prefs) {
        if (prefs.getQuietHoursStart() == null || prefs.getQuietHoursEnd() == null) {
            return false;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(prefs.getTimezone() == null ? "America/Bogota" : prefs.getTimezone());
        } catch (Exception ex) {
            zone = ZoneId.of("America/Bogota");
        }
        LocalTime now = OffsetDateTime.now(zone).toLocalTime();
        LocalTime start = prefs.getQuietHoursStart();
        LocalTime end = prefs.getQuietHoursEnd();
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private OffsetDateTime quietEndToday(UserNotificationPreferenceEntity prefs) {
        try {
            ZoneId zone = ZoneId.of(
                    prefs.getTimezone() == null ? "America/Bogota" : prefs.getTimezone());
            OffsetDateTime now = OffsetDateTime.now(zone);
            OffsetDateTime end = now.toLocalDate().atTime(prefs.getQuietHoursEnd()).atZone(zone)
                    .toOffsetDateTime();
            if (!end.isAfter(now)) {
                end = end.plusDays(1);
            }
            return end;
        } catch (Exception ex) {
            return OffsetDateTime.now().plusMinutes(60);
        }
    }

    private String render(String template, String eventCode, String severity, UUID deviceId) {
        return template.replace("{{event_code}}", eventCode == null ? "" : eventCode)
                .replace("{{severity}}", severity == null ? "" : severity)
                .replace("{{device}}", deviceId == null ? "" : deviceId.toString());
    }

    private String defaultTitle(String eventTypeCode) {
        return "Evento crítico " + (eventTypeCode == null ? "" : eventTypeCode);
    }

    private String defaultBody(String eventTypeCode, String severityCode) {
        return "Se detectó el evento " + eventTypeCode + " con severidad "
                + severityCode + ". Revisa de inmediato.";
    }

    private String tokenPlatform(String platform) {
        if (platform == null) {
            throw new IllegalArgumentException("platform es requerida (fcm|apns|webpush)");
        }
        String normalized = platform.trim().toLowerCase();
        if (!Set.of("fcm", "apns", "webpush").contains(normalized)) {
            throw new IllegalArgumentException("platform inválida: " + platform);
        }
        return normalized;
    }
}
