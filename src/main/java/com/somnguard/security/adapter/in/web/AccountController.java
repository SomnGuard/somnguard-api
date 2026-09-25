package com.somnguard.security.adapter.in.web;

import com.somnguard.platform.security.RequireFeature;
import com.somnguard.security.adapter.in.web.dto.ForgotPasswordRequest;
import com.somnguard.security.adapter.in.web.dto.ResetPasswordRequest;
import com.somnguard.security.adapter.in.web.dto.UpdateMeRequest;
import com.somnguard.security.adapter.in.web.dto.UserMeResponse;
import com.somnguard.security.adapter.in.web.dto.VerifyResetCodeRequest;
import com.somnguard.security.adapter.out.persistence.entity.EmailVerificationEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.application.service.PasswordResetService;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public AccountController(PasswordResetService passwordResetService, UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository, EmailVerificationRepository emailVerificationRepository,
            JavaMailSender mailSender, @Value("${MAIL_FROM:${spring.mail.username}}") String mailFrom) {
        this.passwordResetService = passwordResetService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    // AC-001 — Nuevo flujo: código 6 dígitos sin enlace
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.forgotPassword(req.email());
        return ResponseEntity.ok(Map.of("message", "Si el correo existe, se envió un código de 6 dígitos con expiración 15 minutos"));
    }

    // AC-002a — Validación de código sin consumo (paso 7 del flujo) - solo código
    @PostMapping("/auth/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest req) {
        passwordResetService.verifyResetCode(req.code());
        return ResponseEntity.ok(Map.of("message", "Código válido"));
    }

    // AC-002b — Restablecimiento con código de un solo uso - solo código + nueva contraseña
    @PostMapping("/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada, tokens previos invalidados"));
    }

    @GetMapping("/users/me")
    @RequireFeature({"user.read", "user.own_read"})
    public ResponseEntity<UserMeResponse> getMe(Authentication auth) {
        UUID userId = extractUserId(auth);
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) throw new IllegalArgumentException("Cuenta eliminada");
        return ResponseEntity.ok(toSafeResponse(user));
    }

    // AC-003 PATCH /users/me - retorna DTO seguro sin password_hash
    // Matriz 25 features: user edita perfil propio (own_write) o admin (user.write).
    @PatchMapping("/users/me")
    @RequireFeature({"user.own_write", "user.write"})
    public ResponseEntity<UserMeResponse> updateMe(Authentication auth, @Valid @RequestBody UpdateMeRequest req) {
        UUID userId = extractUserId(auth);
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) throw new IllegalArgumentException("Cuenta eliminada");

        boolean emailChanged = false;
        if (req.email() != null && !req.email().trim().equalsIgnoreCase(user.getEmail())) {
            String norm = req.email().trim().toLowerCase();
            if (userRepository.existsByEmailAndDeletedAtIsNull(norm)) throw new DuplicateEmailException(norm);
            user.setEmail(norm);
            user.setEmailVerifiedAt(null);
            user.setStatus("USER_PENDING_VERIFICATION");
            user.setStatusCategory("PENDING");
            emailChanged = true;
        }
        if (req.phone() != null && !req.phone().trim().equals(user.getPhone())) {
            String p = req.phone().trim();
            if (!p.isBlank() && userRepository.existsByPhoneAndDeletedAtIsNull(p)) throw new DuplicatePhoneException(p);
            user.setPhone(p.isBlank() ? null : p);
        }
        if (req.firstName() != null && !req.firstName().isBlank()) user.setFirstName(req.firstName().trim());
        if (req.lastName() != null && !req.lastName().isBlank()) user.setLastName(req.lastName().trim());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setUpdatedBy(userId);
        UserEntity saved = userRepository.save(user);
        if (emailChanged) {
            String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            String hash = sha256(code);
            EmailVerificationEntity ev = new EmailVerificationEntity();
            ev.setId(UUID.randomUUID());
            ev.setUserId(userId);
            ev.setTokenHash(hash);
            ev.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
            ev.setCreatedAt(OffsetDateTime.now());
            ev.setCreatedBy(userId);
            ev.setIsActive(true);
            ev.setIsUsed(false);
            emailVerificationRepository.save(ev);
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(saved.getEmail());
                msg.setSubject("SomnGuard - Confirma tu nuevo correo");
                msg.setText("Hola " + saved.getFirstName() + ",\n\nHas solicitado cambiar tu correo electrónico en SomnGuard a " + saved.getEmail() + ".\n\nPara confirmar este cambio, verifica tu nueva dirección:\n\nTu código de verificación es:\n\n" + code + "\n\nEste código expira en 15 minutos y solo puede usarse una vez.\n\nSi no solicitaste este cambio, puedes ignorar este correo. Tu correo anterior seguirá verificado.\n\nSaludos,\nEquipo SomnGuard");
                mailSender.send(msg);
                log.info("Verification code sent to {} after email change expiresAt={}", saved.getEmail(), ev.getExpiresAt());
            } catch (Exception e) {
                log.error("Failed to send verification email to {}: {}", saved.getEmail(), e.getMessage(), e);
            }
        }
        return ResponseEntity.ok(toSafeResponse(saved));
    }

    private String sha256(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private UserMeResponse toSafeResponse(UserEntity u) {
        return new UserMeResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(),
                u.getStatus(), u.getStatusCategory());
    }

    // AC-004 DELETE /users/me soft-delete 30d
    // Matriz 25 features: user elimina cuenta propia (user.delete) o admin (user.write).
    @DeleteMapping("/users/me")
    @RequireFeature({"user.delete", "user.write"})
    public ResponseEntity<Void> deleteMe(Authentication auth) {
        UUID userId = extractUserId(auth);
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) return ResponseEntity.noContent().build();
        user.setDeletedAt(OffsetDateTime.now());
        user.setDeletedBy(userId);
        user.setIsActive(false);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        // revoke refresh tokens
        var tokens = refreshTokenRepository.findByUserId(userId);
        for (var rt : tokens) {
            if (Boolean.TRUE.equals(rt.getIsActive())) {
                rt.setRevokedAt(OffsetDateTime.now());
                rt.setIsActive(false);
            }
        }
        refreshTokenRepository.saveAll(tokens);
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalArgumentException("No autenticado");
    }
}
