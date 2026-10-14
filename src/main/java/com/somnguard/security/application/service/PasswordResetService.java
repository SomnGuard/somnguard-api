package com.somnguard.security.application.service;

import com.somnguard.security.adapter.out.email.EmailTemplates;
import com.somnguard.security.adapter.out.persistence.entity.PasswordResetRequestEntity;
import com.somnguard.security.adapter.out.persistence.repository.PasswordResetRequestRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.application.port.out.EmailSender;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final int codeExpiryMinutes;

    public PasswordResetService(UserRepository userRepository, PasswordResetRequestRepository resetRepository,
            RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            @Value("${app.password-reset.code-expiry-minutes:15}") int codeExpiryMinutes) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.codeExpiryMinutes = codeExpiryMinutes;
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

        // Invalidate previous active codes for this user (single-use, latest wins)
        try {
            var previous = resetRepository.findByUserIdAndIsUsedFalseAndIsActiveTrue(user.getId());
            for (var p : previous) {
                p.setIsActive(false);
            }
            if (!previous.isEmpty()) {
                resetRepository.saveAll(previous);
                log.info("Invalidated {} previous reset codes for userId={}", previous.size(), user.getId());
            }
        } catch (Exception e) {
            log.warn("Could not invalidate previous codes for userId={}: {}", user.getId(), e.getMessage());
        }

        // Generate 6-digit numeric code
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String hash = sha256(code);

        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetRequestEntity req = new PasswordResetRequestEntity();
        req.setId(UUID.randomUUID());
        req.setUserId(user.getId());
        req.setTokenHash(hash);
        req.setExpiresAt(now.plusMinutes(codeExpiryMinutes));
        req.setIsUsed(false);
        req.setCreatedAt(now);
        req.setCreatedBy(user.getId());
        req.setIsActive(true);
        resetRepository.save(req);

        try {
            String subject = "SomnGuard - Código para restablecer contraseña";
            String textContent = "Hola,\n\n"
                    + "Recibimos una solicitud para restablecer la contraseña de tu cuenta de SomnGuard.\n\n"
                    + "Tu código temporal de recuperación es:\n\n"
                    + code + "\n\n"
                    + "Este código expira en " + codeExpiryMinutes + " minutos y solo puede utilizarse una vez.\n"
                    + "Ingresa este código en la pantalla de recuperación de contraseña para continuar con el proceso.\n\n"
                    + "Si no solicitaste este cambio, puedes ignorar este correo. Tu contraseña actual seguirá siendo segura.\n\n"
                    + "Saludos,\nEquipo SomnGuard";
            String htmlContent = EmailTemplates.passwordResetEmail(code, codeExpiryMinutes);
            emailSender.send(normalized, subject, textContent, htmlContent);
            log.info("Password reset code sent to {} userId={} expiresAt={}", normalized, user.getId(), req.getExpiresAt());
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", normalized, e.getMessage(), e);
            throw new IllegalStateException("No se pudo enviar el correo, intenta más tarde", e);
        }
    }

    /**
     * Validates code without consuming it. Used by frontend to verify before allowing password change.
     */
    @Transactional
    public void verifyResetCode(String code) {
        verifyResetCode(null, code);
    }

    @Transactional
    public void verifyResetCode(String email, String code) {
        String clean = code != null ? code.trim() : "";
        if (!clean.matches("^\\d{6}$")) {
            throw new IllegalArgumentException("Código inválido: debe ser de 6 dígitos");
        }
        String hash = sha256(clean);
        var reqOpt = resetRepository.findByTokenHashAndIsUsedFalseAndIsActiveTrue(hash);
        if (reqOpt.isEmpty()) throw new IllegalArgumentException("Código inválido o ya usado");
        var req = reqOpt.get();
        if (req.getExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("Código expirado");
        var user = userRepository.findById(req.getUserId()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null || Boolean.FALSE.equals(user.getIsActive())) throw new IllegalArgumentException("Cuenta suspendida o eliminada, no se puede restablecer");
        if ("USER_SUSPENDED".equals(user.getStatus()) || "USER_SOFT_DELETED".equals(user.getStatus())) throw new IllegalArgumentException("Cuenta no elegible para restablecimiento");
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase();
            if (!normalized.equalsIgnoreCase(user.getEmail())) {
                throw new IllegalArgumentException("Código inválido o ya usado");
            }
        }
        log.info("Password reset code verified for userId={} code expiresAt={}", user.getId(), req.getExpiresAt());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        resetPassword(null, token, newPassword);
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        String clean = code != null ? code.trim() : "";
        if (!clean.matches("^\\d{6}$")) {
            throw new IllegalArgumentException("Código inválido: debe ser de 6 dígitos");
        }
        String hash = sha256(clean);
        var reqOpt = resetRepository.findByTokenHashAndIsUsedFalseAndIsActiveTrue(hash);
        if (reqOpt.isEmpty()) throw new IllegalArgumentException("Código inválido o ya usado");
        var req = reqOpt.get();
        if (req.getExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("Código expirado");
        var user = userRepository.findById(req.getUserId()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null || Boolean.FALSE.equals(user.getIsActive())) throw new IllegalArgumentException("Cuenta suspendida o eliminada, no se puede restablecer");
        if ("USER_SUSPENDED".equals(user.getStatus()) || "USER_SOFT_DELETED".equals(user.getStatus())) throw new IllegalArgumentException("Cuenta no elegible para restablecimiento");
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase();
            if (!normalized.equalsIgnoreCase(user.getEmail())) {
                throw new IllegalArgumentException("Código inválido o ya usado");
            }
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(OffsetDateTime.now());
        user.setUpdatedBy(user.getId());
        userRepository.save(user);

        req.setIsUsed(true);
        req.setUsedAt(OffsetDateTime.now());
        req.setIsActive(false);
        resetRepository.save(req);

        // Invalidate other active codes for this user
        try {
            var others = resetRepository.findByUserIdAndIsUsedFalseAndIsActiveTrue(user.getId());
            for (var o : others) {
                o.setIsActive(false);
            }
            if (!others.isEmpty()) resetRepository.saveAll(others);
        } catch (Exception e) {
            log.warn("Could not invalidate other codes for userId={}: {}", user.getId(), e.getMessage());
        }

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
