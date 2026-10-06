package com.somnguard.monitoring.adapter.out.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.somnguard.monitoring.application.port.out.PushSender;

/**
 * Envío push vía Firebase Cloud Messaging HTTP v1 (HU-API-009 AC-002, HU-APP-002).
 * Se activa con {@code app.notifications.push.provider=fcm} + credenciales
 * de cuenta de servicio (JSON inline o ruta de archivo). Sin credenciales,
 * la app no arranca este bean y sigue {@code LoggingPushSender}.
 *
 * <p>Usa {@code RestClient} en vez del transporte interno de firebase-admin:
 * en este entorno ese transporte no logra decodificar la respuesta de FCM
 * y marcaba como fallidos envíos que Google sí aceptaba (duplicados).
 */
@Component
@ConditionalOnProperty(name = "app.notifications.push.provider", havingValue = "fcm")
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final List<String> FCM_SCOPE =
            List.of("https://www.googleapis.com/auth/firebase.messaging");

    private final GoogleCredentials credentials;
    private final String projectId;
    private final RestClient restClient;

    public FcmPushSender(
            @Value("${app.notifications.fcm.service-account-json:}") String serviceAccountJson,
            @Value("${app.notifications.fcm.service-account-path:}") String serviceAccountPath) {
        try {
            ServiceAccountCredentials serviceAccount;
            try (InputStream credentialsStream =
                    openCredentials(serviceAccountJson, serviceAccountPath)) {
                serviceAccount = ServiceAccountCredentials.fromStream(credentialsStream);
            }
            if (serviceAccount.getProjectId() == null || serviceAccount.getProjectId().isBlank()) {
                throw new IllegalStateException("La cuenta de servicio no trae project_id");
            }
            this.projectId = serviceAccount.getProjectId();
            this.credentials = serviceAccount.createScoped(FCM_SCOPE);
            this.restClient = RestClient.builder().build();
            log.info("Push FCM activo project={}", this.projectId);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "No se pudo inicializar FCM (revisa FCM_SERVICE_ACCOUNT_JSON/PATH)", ex);
        }
    }

    @Override
    public PushResult send(UUID userId, String token, String title, String body) {
        try {
            com.google.auth.oauth2.AccessToken accessToken = credentials.refreshAccessToken();
            if (accessToken == null) {
                accessToken = credentials.getAccessToken();
            }
            if (accessToken == null || accessToken.getTokenValue() == null) {
                throw new IllegalStateException("FCM sin access token (revisa la cuenta de servicio)");
            }
            String bearer = accessToken.getTokenValue();
            Map<String, Object> message = new LinkedHashMap<>();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("token", token);
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("title", title);
            notification.put("body", body);
            payload.put("notification", notification);
            payload.put("data", Map.of("type", "critical_event"));
            message.put("message", payload);
            Map<?, ?> response = restClient.post()
                    .uri("https://fcm.googleapis.com/v1/projects/{project}/messages:send", projectId)
                    .headers(headers -> {
                        headers.setBearerAuth(bearer);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(message)
                    .retrieve()
                    .body(Map.class);
            String messageId = response == null || response.get("name") == null
                    ? null : String.valueOf(response.get("name"));
            log.info("Push FCM user={} providerId={}", userId, messageId);
            return new PushResult(true, messageId, null);
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            String detail = ex.getResponseBodyAsString();
            log.warn("Push FCM rechazado user={} status={}: {}", userId,
                    ex.getStatusCode(), truncate(detail));
            return new PushResult(false, null, ex.getStatusCode() + " " + truncate(detail));
        } catch (Exception ex) {
            log.warn("Push FCM fallido user={}: {}", userId, ex.getMessage());
            return new PushResult(false, null, ex.getMessage());
        }
    }

    private InputStream openCredentials(String inlineJson, String path) throws Exception {
        if (inlineJson != null && !inlineJson.isBlank()) {
            return new ByteArrayInputStream(inlineJson.getBytes(StandardCharsets.UTF_8));
        }
        if (path != null && !path.isBlank() && Files.isRegularFile(Path.of(path))) {
            return new FileInputStream(path);
        }
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null && !envPath.isBlank() && Files.isRegularFile(Path.of(envPath))) {
            return new FileInputStream(envPath);
        }
        throw new IllegalStateException(
                "Sin credenciales FCM (FCM_SERVICE_ACCOUNT_JSON, PATH o GOOGLE_APPLICATION_CREDENTIALS)");
    }

    private String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        String singleLine = detail.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 300 ? singleLine.substring(0, 300) : singleLine;
    }
}
