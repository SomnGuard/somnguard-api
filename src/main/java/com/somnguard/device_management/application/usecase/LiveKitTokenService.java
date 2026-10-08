package com.somnguard.device_management.application.usecase;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tokens LiveKit (SFU) firmados HS256 con la secret compartida.
 * Sin SDK extra: se usa nimbus-jose-jwt que ya trae el proyecto.
 * Room determinista por device: somnguard-&lt;8 chars&gt;.
 */
@Service
public class LiveKitTokenService {

    private final boolean enabled;
    private final String url;
    private final String apiKey;
    private final String apiSecret;
    private final long ttlMinutes;

    public LiveKitTokenService(
            @Value("${app.livekit.enabled:false}") boolean enabled,
            @Value("${app.livekit.url:}") String url,
            @Value("${app.livekit.api-key:}") String apiKey,
            @Value("${app.livekit.api-secret:}") String apiSecret,
            @Value("${app.livekit.token-ttl-minutes:60}") long ttlMinutes) {
        this.enabled = enabled;
        this.url = url == null ? "" : url.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.ttlMinutes = ttlMinutes;
    }

    public boolean isEnabled() {
        return enabled && !url.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    public String url() {
        return url;
    }

    public static String roomFor(UUID deviceId) {
        return "somnguard-" + deviceId.toString().substring(0, 8);
    }

    /** Token viewer: solo suscribirse. Identidad única por emisión para que
     *  dos pestañas no se pateen entre sí (LiveKit permite una conexión por identity). */
    public String viewerToken(UUID deviceId, UUID viewerId) {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        return mint(roomFor(deviceId), "viewer-" + viewerId + "-" + nonce, false, true);
    }

    /** Token publisher para el Pi: solo publicar. */
    public String publisherToken(UUID deviceId) {
        return mint(roomFor(deviceId), "device-" + deviceId, true, false);
    }

    private String mint(String room, String identity, boolean canPublish, boolean canSubscribe) {
        try {
            Instant now = Instant.now();
            Map<String, Object> video = new HashMap<>();
            video.put("roomJoin", true);
            video.put("room", room);
            video.put("canPublish", canPublish);
            video.put("canSubscribe", canSubscribe);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(apiKey)
                    .subject(identity)
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlMinutes * 60)))
                    .claim("video", video)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims);
            jwt.sign(new MACSigner(apiSecret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo emitir token LiveKit: " + e.getMessage(), e);
        }
    }
}
