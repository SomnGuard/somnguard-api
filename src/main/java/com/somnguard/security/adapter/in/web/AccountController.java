package com.somnguard.security.adapter.in.web;

import com.somnguard.security.adapter.in.web.dto.ForgotPasswordRequest;
import com.somnguard.security.adapter.in.web.dto.ResetPasswordRequest;
import com.somnguard.security.adapter.in.web.dto.UpdateMeRequest;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.application.service.PasswordResetService;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AccountController(PasswordResetService passwordResetService, UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository) {
        this.passwordResetService = passwordResetService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // AC-001
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.forgotPassword(req.email());
        return ResponseEntity.ok(Map.of("message", "Si el correo existe, se envió un token con expiración 1h"));
    }

    // AC-002
    @PostMapping("/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada, tokens previos invalidados"));
    }

    // AC-003 PATCH /users/me
    @PatchMapping("/users/me")
    public ResponseEntity<UserEntity> updateMe(Authentication auth, @Valid @RequestBody UpdateMeRequest req) {
        UUID userId = extractUserId(auth);
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) throw new IllegalArgumentException("Cuenta eliminada");

        if (req.email() != null && !req.email().trim().equalsIgnoreCase(user.getEmail())) {
            String norm = req.email().trim().toLowerCase();
            if (userRepository.existsByEmailAndDeletedAtIsNull(norm)) throw new DuplicateEmailException(norm);
            user.setEmail(norm);
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
        return ResponseEntity.ok(userRepository.save(user));
    }

    // AC-004 DELETE /users/me soft-delete 30d
    @DeleteMapping("/users/me")
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
