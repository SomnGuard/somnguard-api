package com.somnguard.security.application.usecase;

import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserStatusAuditEntity;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserStatusAuditRepository;
import com.somnguard.security.application.port.in.RegisterUserUseCase;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserStatusAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserStatusAuditRepository auditRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditRepository = auditRepository;
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

        return userId;
    }
}
