package com.somnguard.security.application.service;

import com.somnguard.security.adapter.out.persistence.entity.PasswordResetRequestEntity;
import com.somnguard.security.adapter.out.persistence.repository.PasswordResetRequestRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.domain.exception.InvalidCredentialsException;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public PasswordResetService(UserRepository userRepository, PasswordResetRequestRepository resetRepository,
            RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
            JavaMailSender mailSender, @Value("${spring.mail.username}") String mailFrom) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase();
        var userOpt = userRepository.findByEmailAndDeletedAtIsNull(normalized);
        if (userOpt.isEmpty()) {
            log.info("forgot-password for non-existent email {}", normalized);
            return;
        }
        var user = userOpt.get();
        // Generate opaque token 32 bytes base64url
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256(token);

        PasswordResetRequestEntity req = new PasswordResetRequestEntity();
        req.setId(UUID.randomUUID());
        req.setUserId(user.getId());
        req.setTokenHash(hash);
        req.setExpiresAt(OffsetDateTime.now().plusHours(1));
        req.setIsUsed(false);
        req.setCreatedAt(OffsetDateTime.now());
        req.setCreatedBy(user.getId());
        req.setIsActive(true);
        resetRepository.save(req);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(normalized);
            msg.setSubject("SomnGuard - Restablecer contraseña");
            msg.setText("Hola,\n\nRecibimos una solicitud para restablecer la contraseña de tu cuenta de SomnGuard.\n\nTu código de recuperación\n\n" + token + "\n\nEste código:\n\nExpira en 1 hora\nSolo puede utilizarse una vez\nEs válido únicamente para restablecer tu contraseña\n\nSi no solicitaste este cambio, puedes ignorar este correo. Tu contraseña actual seguirá siendo segura.\n\nSaludos,\nEquipo SomnGuard");
            mailSender.send(msg);
            log.info("Password reset email sent to {} userId={} expiresAt={}", normalized, user.getId(), req.getExpiresAt());
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", normalized, e.getMessage(), e);
            throw new IllegalStateException("No se pudo enviar el correo, intenta más tarde", e);
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String hash = sha256(token);
        var reqOpt = resetRepository.findByTokenHashAndIsUsedFalseAndIsActiveTrue(hash);
        if (reqOpt.isEmpty()) throw new IllegalArgumentException("Token inválido o ya usado");
        var req = reqOpt.get();
        if (req.getExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("Token expirado");
        var user = userRepository.findById(req.getUserId()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) throw new IllegalArgumentException("Cuenta eliminada");

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(OffsetDateTime.now());
        user.setUpdatedBy(user.getId());
        userRepository.save(user);

        req.setIsUsed(true);
        req.setUsedAt(OffsetDateTime.now());
        req.setIsActive(false);
        resetRepository.save(req);

        // Invalidate all refresh tokens for user
        var tokens = refreshTokenRepository.findByUserId(user.getId());
        for (var rt : tokens) {
            if (Boolean.TRUE.equals(rt.getIsActive())) {
                rt.setRevokedAt(OffsetDateTime.now());
                rt.setIsActive(false);
            }
        }
        refreshTokenRepository.saveAll(tokens);
        log.info("Password reset completed for userId={}", user.getId());
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hash failed", e);
        }
    }
}
