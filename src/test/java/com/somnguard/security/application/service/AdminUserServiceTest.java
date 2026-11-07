package com.somnguard.security.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.security.adapter.in.web.dto.CreateAdminUserRequest;
import com.somnguard.security.adapter.in.web.dto.UpdateAdminUserRequest;
import com.somnguard.security.adapter.out.persistence.entity.RefreshTokenEntity;
import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserStatusAuditRepository;
import com.somnguard.security.application.port.in.RegisterUserUseCase;
import com.somnguard.security.application.port.out.EmailSender;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserStatusAuditRepository auditRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private RegisterUserUseCase registerUserUseCase;
    @Mock private EmailSender emailSender;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, userRoleRepository, roleRepository, auditRepository,
                refreshTokenRepository, emailVerificationRepository, registerUserUseCase, emailSender);
    }

    @Test
    void listUsersReturnsSafeDetailsAndActiveRoles() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserEntity user = user(userId);
        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode("operator");
        role.setName("Operador");
        role.setIsActive(true);
        when(userRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(user));
        when(userRoleRepository.findActiveRoleIdsByUserId(eq(userId), any())).thenReturn(List.of(roleId));
        when(roleRepository.findAllById(List.of(roleId))).thenReturn(List.of(role));

        var result = service.listUsers();

        assertEquals(1, result.size());
        assertEquals("person@example.com", result.getFirst().email());
        assertEquals(roleId, result.getFirst().roles().getFirst().id());
        assertTrue(java.util.Arrays.stream(result.getFirst().getClass().getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("password")));
    }

    @Test
    void createNormalizesValuesAndDelegatesToExistingRegistrationFlow() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserEntity created = user(userId);
        when(userRepository.findByEmailAndDeletedAtIsNull("person@example.com")).thenReturn(Optional.empty());
        when(registerUserUseCase.registerAdmin(any(), eq(actorId))).thenReturn(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(created));
        when(userRoleRepository.findActiveRoleIdsByUserId(eq(userId), any())).thenReturn(List.of());

        var response = service.createUser(new CreateAdminUserRequest(" PERSON@EXAMPLE.COM ", "StrongPass1",
                " Ana ", " Perez ", " 555-0100 "), actorId);

        assertEquals("person@example.com", response.email());
        var command = ArgumentCaptor.forClass(RegisterUserUseCase.RegisterUserCommand.class);
        verify(registerUserUseCase).registerAdmin(command.capture(), eq(actorId));
        assertEquals("555-0100", command.getValue().phone());
        assertEquals("Ana", command.getValue().firstName());
    }

    @Test
    void createRejectsDuplicateEmail() {
        UserEntity existing = user(UUID.randomUUID());
        when(userRepository.findByEmailAndDeletedAtIsNull("person@example.com")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateEmailException.class, () -> service.createUser(
                new CreateAdminUserRequest("person@example.com", "StrongPass1", "Ana", "Perez", null), UUID.randomUUID()));
    }

    @Test
    void updateRejectsDuplicatePhoneOwnedByAnotherAccount() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId);
        UserEntity other = user(UUID.randomUUID());
        other.setPhone("555-0200");
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailAndDeletedAtIsNull("person@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneAndDeletedAtIsNull("555-0200")).thenReturn(Optional.of(other));

        assertThrows(DuplicatePhoneException.class, () -> service.updateUser(userId,
                new UpdateAdminUserRequest("person@example.com", "Ana", "Perez", "555-0200"), UUID.randomUUID()));
    }

    @Test
    void updateEmailInvalidatesVerificationAndSendsNewVerificationCode() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserEntity user = user(userId);
        user.setEmailVerifiedAt(OffsetDateTime.now());
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);
        when(userRoleRepository.findActiveRoleIdsByUserId(eq(userId), any())).thenReturn(List.of());

        var response = service.updateUser(userId,
                new UpdateAdminUserRequest("NEW@example.com", "Ana", "Perez", null), actorId);

        assertEquals("new@example.com", response.email());
        assertEquals("USER_PENDING_VERIFICATION", response.status());
        assertEquals(null, response.emailVerifiedAt());
        verify(emailVerificationRepository).save(any());
        verify(emailSender).send(eq("new@example.com"), any(), any(), any());
        verify(auditRepository).save(any());
    }

    @Test
    void deleteSoftDeletesUserAssignmentsAndRevokesRefreshTokens() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserEntity user = user(userId);
        UserRoleEntity relation = new UserRoleEntity();
        relation.setIsActive(true);
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setIsActive(true);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserIdAndIsActiveTrueAndDeletedAtIsNull(userId)).thenReturn(List.of(relation));
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(List.of(token));

        service.deleteUser(userId, actorId);

        assertEquals("USER_SOFT_DELETED", user.getStatus());
        assertEquals("ARCHIVED", user.getStatusCategory());
        assertEquals(actorId, user.getDeletedBy());
        assertTrue(Boolean.FALSE.equals(relation.getIsActive()));
        assertEquals(actorId, relation.getDeletedBy());
        assertTrue(Boolean.FALSE.equals(token.getIsActive()));
        assertTrue(token.getRevokedAt() != null);
        verify(userRepository).save(user);
        verify(userRoleRepository).save(relation);
        verify(refreshTokenRepository).saveAll(List.of(token));
        verify(auditRepository).save(any());
    }

    private UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("person@example.com");
        user.setFirstName("Ana");
        user.setLastName("Perez");
        user.setIsActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setStatus("USER_ACTIVE");
        user.setStatusCategory("ACTIVE");
        return user;
    }
}
