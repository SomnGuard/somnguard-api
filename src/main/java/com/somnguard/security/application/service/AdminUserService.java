package com.somnguard.security.application.service;

import com.somnguard.security.adapter.in.web.dto.AdminUserResponse;
import com.somnguard.security.adapter.in.web.dto.AdminUserRoleResponse;
import com.somnguard.security.adapter.in.web.dto.CreateAdminUserRequest;
import com.somnguard.security.adapter.in.web.dto.UpdateAdminUserRequest;
import com.somnguard.security.adapter.out.email.EmailTemplates;
import com.somnguard.security.adapter.out.persistence.entity.EmailVerificationEntity;
import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserStatusAuditEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserStatusAuditRepository;
import com.somnguard.security.application.port.in.RegisterUserUseCase;
import com.somnguard.security.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.somnguard.security.application.port.out.EmailSender;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserStatusAuditRepository auditRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RegisterUserUseCase registerUserUseCase;
    private final EmailSender emailSender;

    public AdminUserService(UserRepository userRepository, UserRoleRepository userRoleRepository,
            RoleRepository roleRepository, UserStatusAuditRepository auditRepository,
            RefreshTokenRepository refreshTokenRepository, EmailVerificationRepository emailVerificationRepository,
            RegisterUserUseCase registerUserUseCase, EmailSender emailSender) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.auditRepository = auditRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.registerUserUseCase = registerUserUseCase;
        this.emailSender = emailSender;
    }

    @Transactional
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse getUser(UUID id) {
        return toResponse(findUser(id));
    }

    @Transactional
    public AdminUserResponse createUser(CreateAdminUserRequest request, UUID actorId) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        validateUnique(null, email, phone);
        UUID id = registerUserUseCase.registerAdmin(new RegisterUserCommand(
                email, request.password(), request.firstName().trim(), request.lastName().trim(), phone), actorId);
        return toResponse(findUser(id));
    }

    @Transactional
    public AdminUserResponse updateUser(UUID id, UpdateAdminUserRequest request, UUID actorId) {
        UserEntity user = findUser(id);
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        validateUnique(id, email, phone);

        OffsetDateTime now = OffsetDateTime.now();
        boolean emailChanged = !email.equalsIgnoreCase(user.getEmail());
        String previousStatus = user.getStatus();
        String previousCategory = user.getStatusCategory();
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(phone);
        if (emailChanged) {
            user.setEmailVerifiedAt(null);
            user.setStatus("USER_PENDING_VERIFICATION");
            user.setStatusCategory("PENDING");
        }
        user.setUpdatedAt(now);
        user.setUpdatedBy(actorId);
        UserEntity saved = userRepository.save(user);

        if (emailChanged) {
            recordStatusChange(saved, previousStatus, previousCategory, actorId, now, "admin-email-change");
            sendEmailVerification(saved, actorId, now);
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID id, UUID actorId) {
        UserEntity user = findUser(id);
        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = user.getStatus();
        String previousCategory = user.getStatusCategory();
        user.setIsActive(false);
        user.setDeletedAt(now);
        user.setDeletedBy(actorId);
        user.setUpdatedAt(now);
        user.setUpdatedBy(actorId);
        user.setStatus("USER_SOFT_DELETED");
        user.setStatusCategory("ARCHIVED");
        userRepository.save(user);

        for (UserRoleEntity relation : userRoleRepository.findByUserIdAndIsActiveTrueAndDeletedAtIsNull(id)) {
            relation.setIsActive(false);
            relation.setDeletedAt(now);
            relation.setDeletedBy(actorId);
            relation.setUpdatedAt(now);
            relation.setUpdatedBy(actorId);
            userRoleRepository.save(relation);
        }
        var refreshTokens = refreshTokenRepository.findByUserId(id);
        for (var token : refreshTokens) {
            if (Boolean.TRUE.equals(token.getIsActive())) {
                token.setIsActive(false);
                token.setRevokedAt(now);
            }
        }
        refreshTokenRepository.saveAll(refreshTokens);
        recordStatusChange(user, previousStatus, previousCategory, actorId, now, "admin-delete");
    }

    private UserEntity findUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private void validateUnique(UUID currentId, String email, String phone) {
        userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) throw new DuplicateEmailException(email);
        });
        if (phone != null) userRepository.findByPhoneAndDeletedAtIsNull(phone).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) throw new DuplicatePhoneException(phone);
        });
    }

    private AdminUserResponse toResponse(UserEntity user) {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> roleIds = userRoleRepository.findActiveRoleIdsByUserId(user.getId(), now).stream().distinct().toList();
        Map<UUID, RoleEntity> rolesById = roleRepository
                .findAllById(roleIds).stream()
                .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                .collect(Collectors.toMap(
                        RoleEntity::getId,
                        Function.identity()));
        List<AdminUserRoleResponse> roles = roleIds.stream()
                .map(rolesById::get)
                .filter(role -> role != null)
                .map(role -> new AdminUserRoleResponse(role.getId(), role.getCode(), role.getName()))
                .toList();
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getIsActive(), user.getEmailVerifiedAt(), user.getLastLoginAt(),
                user.getStatus(), user.getStatusCategory(), user.getCreatedAt(), user.getUpdatedAt(), roles);
    }

    private void recordStatusChange(UserEntity user, String previousStatus, String previousCategory,
            UUID actorId, OffsetDateTime now, String source) {
        if (java.util.Objects.equals(previousStatus, user.getStatus())
                && java.util.Objects.equals(previousCategory, user.getStatusCategory())) return;
        UserStatusAuditEntity audit = new UserStatusAuditEntity();
        audit.setUserId(user.getId());
        audit.setFromStatus(previousStatus);
        audit.setToStatus(user.getStatus());
        audit.setFromCategory(previousCategory);
        audit.setToCategory(user.getStatusCategory());
        audit.setChangedBy(actorId);
        audit.setChangedAt(now);
        audit.setContextJson("{\"source\":\"" + source + "\"}");
        auditRepository.save(audit);
    }

    private void sendEmailVerification(UserEntity user, UUID actorId, OffsetDateTime now) {
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        EmailVerificationEntity verification = new EmailVerificationEntity();
        verification.setId(UUID.randomUUID());
        verification.setUserId(user.getId());
        verification.setTokenHash(sha256(code));
        verification.setExpiresAt(now.plusMinutes(15));
        verification.setCreatedAt(now);
        verification.setCreatedBy(actorId);
        verification.setIsActive(true);
        verification.setIsUsed(false);
        emailVerificationRepository.save(verification);
        try {
            String text = "Hola " + user.getFirstName() + ",\n\nTu correo fue actualizado por un administrador. "
                    + "Verifica esta dirección usando el código: " + code + "\n\nEl código expira en 15 minutos.";
            emailSender.send(user.getEmail(), "SomnGuard - Confirma tu correo", text,
                    EmailTemplates.verificationEmail(user.getFirstName(), code));
        } catch (RuntimeException ignored) {
            log.warn("Could not send email verification after admin email change for userId={}", user.getId());
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo proteger el código de verificación", e);
        }
    }
}
