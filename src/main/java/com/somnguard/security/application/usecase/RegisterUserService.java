package com.somnguard.security.application.usecase;

import com.somnguard.security.adapter.out.persistence.entity.EmailVerificationEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserStatusAuditEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserStatusAuditRepository;
import com.somnguard.security.application.port.in.RegisterUserUseCase;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterUserService implements RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserService.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserStatusAuditRepository auditRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserStatusAuditRepository auditRepository,
            EmailVerificationRepository emailVerificationRepository,
            JavaMailSender mailSender,
            @Value("${MAIL_FROM:${spring.mail.username}}") String mailFrom,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditRepository = auditRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UUID register(RegisterUserCommand command) {
        String normalizedEmail = command.email().trim().toLowerCase();
        String normalizedPhone = command.phone() != null && !command.phone().isBlank()
                ? command.phone().trim()
                : null;

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }
        if (normalizedPhone != null && userRepository.existsByPhoneAndDeletedAtIsNull(normalizedPhone)) {
            throw new DuplicatePhoneException(normalizedPhone);
        }

        var role = roleRepository.findByCodeAndIsActiveTrue("user")
                .orElseThrow(() -> new IllegalStateException("Default role 'user' not found or inactive"));

        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setFirstName(command.firstName().trim());
        user.setLastName(command.lastName().trim());
        user.setPhone(normalizedPhone);
        user.setIsActive(true);
        user.setFailedLoginAttempts((short) 0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setVersion(1);
        user.setStatus("USER_PENDING_VERIFICATION");
        user.setStatusCategory("PENDING");

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: unique violation -> map to 409
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
            if (msg != null && msg.contains("uq_user_email")) {
                throw new DuplicateEmailException(normalizedEmail);
            }
            if (msg != null && msg.contains("uq_user_phone")) {
                throw new DuplicatePhoneException(normalizedPhone);
            }
            throw ex;
        }

        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setId(UUID.randomUUID());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setAssignedAt(now);
        userRole.setCreatedAt(now);
        userRole.setUpdatedAt(now);
        userRole.setVersion(1);
        userRole.setIsActive(true);
        userRoleRepository.save(userRole);

        UserStatusAuditEntity audit = new UserStatusAuditEntity();
        audit.setUserId(userId);
        audit.setFromStatus(null);
        audit.setToStatus("USER_PENDING_VERIFICATION");
        audit.setFromCategory(null);
        audit.setToCategory("PENDING");
        audit.setChangedAt(now);
        audit.setContextJson("{\"source\":\"register\"}");
        auditRepository.save(audit);

        // Generate email verification token 24h, send via Gmail
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256(token);
        EmailVerificationEntity ev = new EmailVerificationEntity();
        ev.setId(UUID.randomUUID());
        ev.setUserId(userId);
        ev.setTokenHash(hash);
        ev.setExpiresAt(now.plusHours(24));
        ev.setCreatedAt(now);
        ev.setCreatedBy(userId);
        ev.setIsActive(true);
        ev.setIsUsed(false);
        emailVerificationRepository.save(ev);
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(normalizedEmail);
            msg.setSubject("SomnGuard - Verifica tu correo");
            msg.setText("Hola " + command.firstName() + ",\n\nGracias por registrarte en SomnGuard.\n\nPara completar la creación de tu cuenta, verifica tu dirección de correo electrónico.\n\nTu código de verificación\n\n" + token + "\n\nEste código expira en 24 horas.\n\nSi no creaste una cuenta en SomnGuard, puedes ignorar este correo.\n\nSaludos,\nEquipo SomnGuard");
            mailSender.send(msg);
            log.info("Verification email sent to {} userId={}", normalizedEmail, userId);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", normalizedEmail, e.getMessage(), e);
            // no rollback, user can re-request via resend endpoint
        }

        return userId;
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
