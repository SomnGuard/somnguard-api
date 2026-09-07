package com.somnguard.device_management.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Generación y verificación de API keys (HU-API-006 AC-001/AC-007, RF-DEV-09).
 * Se almacena solo el hash SHA-256 hexadecimal; la clave en claro se muestra una única vez.
 */
@Service
public class DeviceApiKeyService {

    private final SecureRandom random = new SecureRandom();

    public String generatePlainKey() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Claim code imprimible (RF-DEV-12, ADR-010): 12 caracteres Crockford Base32
     * agrupados {@code XXXX-XXXX-XXXX} para dictado/transcripción manual.
     * Se almacena solo su hash SHA-256; el valor en claro se expone una única vez.
     */
    public String generateClaimCode() {
        final String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder raw = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            raw.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
    }

    public String hash(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public boolean verify(String plainKey, String storedHash) {
        if (plainKey == null || storedHash == null) return false;
        String candidate = hash(plainKey);
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
