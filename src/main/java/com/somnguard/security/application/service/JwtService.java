package com.somnguard.security.application.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final long accessExpiryMinutes;
    private final long refreshExpiryDays;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.private-key-location:classpath:keys/dev/private.pem}") Resource privateKeyResource,
            @Value("${app.jwt.public-key-location:classpath:keys/dev/public.pem}") Resource publicKeyResource,
            @Value("${app.jwt.access-token-expiry-minutes:15}") long accessExpiryMinutes,
            @Value("${app.jwt.refresh-token-expiry-days:7}") long refreshExpiryDays,
            @Value("${app.jwt.issuer:somnguard-api}") String issuer) {
        this.privateKey = loadPrivateKey(privateKeyResource);
        this.publicKey = loadPublicKey(publicKeyResource);
        this.accessExpiryMinutes = accessExpiryMinutes;
        this.refreshExpiryDays = refreshExpiryDays;
        this.issuer = issuer;
    }

    public String generateAccessToken(UUID userId, String email, List<String> roles, List<String> features) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("features", features)
                .issuer(issuer)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessExpiryMinutes * 60)))
                .claim("type", "access")
                .build();
        return sign(claims);
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(refreshExpiryDays * 24 * 60 * 60)))
                .claim("type", "refresh")
                .build();
        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("somnguard-key-1").build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private RSAPrivateKey loadPrivateKey(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String pem = reader.lines().filter(l -> !l.startsWith("-----")).reduce("", String::concat);
            byte[] decoded = java.util.Base64.getDecoder().decode(pem);
            var spec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
            return (RSAPrivateKey) java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la clave privada JWT", e);
        }
    }

    private RSAPublicKey loadPublicKey(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String pem = reader.lines().filter(l -> !l.startsWith("-----")).reduce("", String::concat);
            byte[] decoded = java.util.Base64.getDecoder().decode(pem);
            var spec = new java.security.spec.X509EncodedKeySpec(decoded);
            return (RSAPublicKey) java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la clave pública JWT", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
