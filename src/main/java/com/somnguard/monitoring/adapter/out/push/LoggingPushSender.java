package com.somnguard.monitoring.adapter.out.push;

import com.somnguard.monitoring.application.port.out.PushSender;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación push por defecto (HU-API-009 AC-002).
 * Activa salvo que {@code app.notifications.push.provider=fcm}.
 * Registra el envío y reporta éxito para no bloquear in-app mientras
 * Q-008 (proveedor FCM/APNs) sigue abierta; sustituir por FCM sin
 * cambiar {@code NotificationService}.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.push.provider",
        havingValue = "log", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public PushResult send(UUID userId, String token, String title, String body) {
        String providerId = "log-" + UUID.randomUUID();
        log.info("Push HU-API-009 user={} token=***{} title={} providerId={}",
                userId, tail(token), title, providerId);
        return new PushResult(true, providerId, null);
    }

    private String tail(String token) {
        if (token == null || token.length() < 6) {
            return "***";
        }
        return "***" + token.substring(token.length() - 6);
    }
}
