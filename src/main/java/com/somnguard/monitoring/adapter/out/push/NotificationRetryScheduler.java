package com.somnguard.monitoring.adapter.out.push;

import com.somnguard.monitoring.application.usecase.NotificationService;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintento automático de push diferidos (HU-API-009 AC-003).
 * Cada 5 minutos reprocesa las notificaciones push con
 * {@code next_retry_at} vencido (backoff 1→2→4 min, tope max-retries).
 */
@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationService notificationService;

    public NotificationRetryScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.retry-period-ms:300000}")
    public void retryPending() {
        try {
            int processed = notificationService.retryPending(OffsetDateTime.now());
            if (processed > 0) {
                log.info("HU-API-009 reintentos push procesados={}", processed);
            }
        } catch (Exception ex) {
            log.warn("HU-API-009 reintento programado fallido: {}", ex.getMessage());
        }
    }
}
