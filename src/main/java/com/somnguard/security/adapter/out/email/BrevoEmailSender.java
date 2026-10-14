package com.somnguard.security.adapter.out.email;

import com.somnguard.security.application.port.out.EmailSendException;
import com.somnguard.security.application.port.out.EmailSender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Envío de correo vía API HTTP de Brevo ({@code POST /v3/smtp/email}).
 * Proveedor único de la aplicación. Requiere {@code BREVO_API_KEY}
 * (fail-fast sin el secreto en mensajes/logs).
 */
@Component
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

    private final BrevoEmailProperties properties;
    private final RestClient restClient;

    @Autowired
    public BrevoEmailSender(BrevoEmailProperties properties) {
        this(properties, buildRestClient(properties));
    }

    BrevoEmailSender(BrevoEmailProperties properties, RestClient restClient) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Falta BREVO_API_KEY (variable de entorno requerida para enviar correos)");
        }
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public void send(String to, String subject, String textContent, String htmlContent) {
        Map<String, Object> payload = buildPayload(to, subject, textContent, htmlContent);
        try {
            Map<?, ?> response = restClient.post()
                    .uri(properties.getUrl())
                    .headers(headers -> {
                        headers.set("api-key", properties.getApiKey());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            Object messageId = response == null ? null : response.get("messageId");
            log.info("Email enviado via Brevo to={} subject={} messageId={}", to, subject, messageId);
        } catch (HttpStatusCodeException ex) {
            // 401 = API key inválida, 400 = email rechazado, etc. Sin secretos.
            log.warn("Brevo rechazo el email to={} status={}: {}", to,
                    ex.getStatusCode(), truncate(ex.getResponseBodyAsString()));
            throw new EmailSendException(
                    "No se pudo enviar el correo (proveedor brevo status=" + ex.getStatusCode() + ")", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Brevo inaccesible (timeout/conexion) to={}: {}", to, ex.getMessage());
            throw new EmailSendException("No se pudo enviar el correo (proveedor brevo inaccesible)", ex);
        } catch (EmailSendException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Fallo envio Brevo to={}: {}", to, ex.getMessage());
            throw new EmailSendException("No se pudo enviar el correo (proveedor brevo)", ex);
        }
    }

    Map<String, Object> buildPayload(String to, String subject, String textContent, String htmlContent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", properties.getSenderName());
        sender.put("email", properties.getSenderEmail());
        payload.put("sender", sender);
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", subject);
        if (htmlContent != null && !htmlContent.isBlank()) {
            payload.put("htmlContent", htmlContent);
        }
        payload.put("textContent", textContent);
        return payload;
    }

    private static RestClient buildRestClient(BrevoEmailProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }

    private String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        String singleLine = detail.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 300 ? singleLine.substring(0, 300) : singleLine;
    }
}
