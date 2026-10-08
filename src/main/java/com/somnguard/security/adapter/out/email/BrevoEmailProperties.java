package com.somnguard.security.adapter.out.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración Brevo (API HTTP, sin SMTP).
 * El secreto solo llega por variable de entorno {@code BREVO_API_KEY};
 * nunca se escribe en código ni se registra en logs.
 */
@Component
@ConfigurationProperties(prefix = "app.email.brevo")
public class BrevoEmailProperties {

    private String apiKey = "";
    private String senderEmail = "somnguard.noreply@gmail.com";
    private String senderName = "SomnGuard";
    private String url = "https://api.brevo.com/v3/smtp/email";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
