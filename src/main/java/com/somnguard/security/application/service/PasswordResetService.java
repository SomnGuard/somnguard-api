package com.somnguard.security.application.service;

import com.somnguard.security.adapter.out.persistence.entity.PasswordResetRequestEntity;
import com.somnguard.security.adapter.out.persistence.repository.PasswordResetRequestRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import java.net.URLEncoder;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
    private final String frontendBaseUrl;
    private final String frontendResetPath;

    public PasswordResetService(UserRepository userRepository, PasswordResetRequestRepository resetRepository,
            RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
            JavaMailSender mailSender, @Value("${MAIL_FROM:${spring.mail.username}}") String mailFrom,
            @Value("${FRONTEND_URL:http://localhost:5173}") String frontendBaseUrl,
            @Value("${FRONTEND_RESET_PASSWORD_PATH:/reset-password}") String frontendResetPath) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl;
        this.frontendResetPath = frontendResetPath;
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

        String resetLink = buildResetLink(token);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(normalized);
            helper.setSubject("SomnGuard - Restablecer contraseña");
            String textContent = "Hola,\n\nRecibimos una solicitud para restablecer la contraseña de tu cuenta de SomnGuard.\n\n"
                    + "Restablece tu contraseña haciendo clic en el siguiente enlace:\n" + resetLink + "\n\n"
                    + "Este enlace expira en 1 hora y solo puede utilizarse una vez.\n"
                    + "Si el botón no funciona, copia y pega el siguiente token en la app:\n" + token + "\n\n"
                    + "Si no solicitaste este cambio, puedes ignorar este correo. Tu contraseña actual seguirá siendo segura.\n\n"
                    + "Saludos,\nEquipo SomnGuard";
            String htmlContent = "<!doctype html><html><body style=\"font-family:Arial,sans-serif;color:#111;\">"
                    + "<p>Hola,</p>"
                    + "<p>Recibimos una solicitud para restablecer la contraseña de tu cuenta de <strong>SomnGuard</strong>.</p>"
                    + "<p><a href=\"" + resetLink + "\" style=\"display:inline-block;padding:12px 24px;background:#0f766e;color:#fff;text-decoration:none;border-radius:6px;\">Restablecer contraseña</a></p>"
                    + "<p>Este enlace <strong>expira en 1 hora</strong> y solo puede utilizarse una vez.</p>"
                    + "<p>Si el botón no funciona, copia este enlace:<br><a href=\"" + resetLink + "\">" + resetLink + "</a></p>"
                    + "<p style=\"font-size:12px;color:#666;\">Token de respaldo: <code>" + token + "</code></p>"
                    + "<p>Si no solicitaste este cambio, puedes ignorar este correo.</p>"
                    + "<p>Saludos,<br>Equipo SomnGuard</p>"
                    + "</body></html>";
            helper.setText(textContent, htmlContent);
            mailSender.send(mimeMessage);
            log.info("Password reset email sent to {} userId={} expiresAt={} link={}", normalized, user.getId(), req.getExpiresAt(), resetLink);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", normalized, e.getMessage(), e);
            throw new IllegalStateException("No se pudo enviar el correo, intenta más tarde", e);
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String clean = token != null ? token.trim() : "";
        String hash = sha256(clean);
        var reqOpt = resetRepository.findByTokenHashAndIsUsedFalseAndIsActiveTrue(hash);
        if (reqOpt.isEmpty()) throw new IllegalArgumentException("Token inválido o ya usado");
        var req = reqOpt.get();
        if (req.getExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("Token expirado");
        var user = userRepository.findById(req.getUserId()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null || Boolean.FALSE.equals(user.getIsActive())) throw new IllegalArgumentException("Cuenta suspendida o eliminada, no se puede restablecer");
        if ("USER_SUSPENDED".equals(user.getStatus()) || "USER_SOFT_DELETED".equals(user.getStatus())) throw new IllegalArgumentException("Cuenta no elegible para restablecimiento");

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

    private String buildResetLink(String token) {
        String base = frontendBaseUrl != null ? frontendBaseUrl.replaceAll("/+$", "") : "";
        String path = frontendResetPath != null ? frontendResetPath : "/reset-password";
        if (!path.startsWith("/")) path = "/" + path;
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return base + path + "?token=" + encoded;
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
