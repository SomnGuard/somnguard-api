package com.somnguard.security.application.service;

import com.somnguard.security.adapter.out.persistence.entity.EmailVerificationEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserStatusAuditEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserStatusAuditRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final UserStatusAuditRepository auditRepository;

    public EmailVerificationService(EmailVerificationRepository verificationRepository, UserRepository userRepository, UserStatusAuditRepository auditRepository) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void verify(String token) {
        String clean = token != null ? token.trim() : "";
        String hash = sha256(clean);
        var ev = verificationRepository.findByTokenHashAndIsUsedFalseAndIsActiveTrue(hash)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o ya usado"));
        if (ev.getExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("Token expirado");
        var user = userRepository.findById(ev.getUserId()).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null || Boolean.FALSE.equals(user.getIsActive())) throw new IllegalArgumentException("Cuenta suspendida o eliminada, no se puede verificar");
        if ("USER_SUSPENDED".equals(user.getStatus()) || "USER_SOFT_DELETED".equals(user.getStatus())) throw new IllegalArgumentException("Cuenta no elegible para verificación");
        String prevStatus = user.getStatus();
        String prevCategory = user.getStatusCategory();
        user.setEmailVerifiedAt(OffsetDateTime.now());
        user.setStatus("USER_ACTIVE");
        user.setStatusCategory("ACTIVE");
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        ev.setIsUsed(true);
        ev.setUsedAt(OffsetDateTime.now());
        ev.setIsActive(false);
        verificationRepository.save(ev);

        UserStatusAuditEntity audit = new UserStatusAuditEntity();
        audit.setUserId(user.getId());
        audit.setFromStatus(prevStatus);
        audit.setToStatus("USER_ACTIVE");
        audit.setFromCategory(prevCategory);
        audit.setToCategory("ACTIVE");
        audit.setChangedAt(OffsetDateTime.now());
        audit.setContextJson("{\"source\":\"email_verification\"}");
        auditRepository.save(audit);
    }

    private String sha256(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
