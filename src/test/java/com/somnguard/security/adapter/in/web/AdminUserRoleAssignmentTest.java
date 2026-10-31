package com.somnguard.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.security.adapter.in.web.dto.AssignRoleToUserPathRequest;
import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import com.somnguard.security.adapter.out.persistence.repository.FeatureRepository;
import com.somnguard.security.adapter.out.persistence.repository.ModuleRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleFeatureRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.platform.security.RequireFeature;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AdminUserRoleAssignmentTest {

    @Test
    void assigningOrRemovingRolesRequiresRoleAssignmentFeature() throws Exception {
        var assign = AdminRbacController.class.getDeclaredMethod("assignRoleToUserByPath", UUID.class,
                AssignRoleToUserPathRequest.class, org.springframework.security.core.Authentication.class);
        var remove = AdminRbacController.class.getDeclaredMethod("removeRoleFromUser", UUID.class, UUID.class,
                org.springframework.security.core.Authentication.class);
        assertTrue(List.of(assign.getAnnotation(RequireFeature.class).value()).contains("role.assign"));
        assertTrue(List.of(remove.getAnnotation(RequireFeature.class).value()).contains("role.assign"));
    }

    @Mock private RoleRepository roleRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private ModuleRepository moduleRepository;
    @Mock private RoleFeatureRepository roleFeatureRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserRepository userRepository;

    private AdminRbacController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminRbacController(roleRepository, featureRepository, moduleRepository,
                roleFeatureRepository, userRoleRepository, userRepository);
    }

    @Test
    void assignmentPersistsAuditedRelationAndRejectsInactiveRole() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setIsActive(true);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.findByUserIdAndRoleIdAndDeletedAtIsNull(userId, roleId)).thenReturn(Optional.empty());

        var response = controller.assignRoleToUserByPath(userId, new AssignRoleToUserPathRequest(roleId), jwt(actorId));

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<UserRoleEntity> saved = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleRepository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(roleId, saved.getValue().getRoleId());
        assertEquals(actorId, saved.getValue().getCreatedBy());
        assertEquals(true, saved.getValue().getIsActive());

        role.setIsActive(false);
        assertThrows(IllegalArgumentException.class, () -> controller.assignRoleToUserByPath(
                userId, new AssignRoleToUserPathRequest(roleId), jwt(actorId)));
    }

    @Test
    void activeRoleAssignmentIsNotDuplicated() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setIsActive(true);
        UserRoleEntity existing = new UserRoleEntity();
        existing.setIsActive(true);
        existing.setExpiresAt(null);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.findByUserIdAndRoleIdAndDeletedAtIsNull(userId, roleId)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> controller.assignRoleToUserByPath(
                userId, new AssignRoleToUserPathRequest(roleId), jwt(UUID.randomUUID())));
    }

    @Test
    void removalSoftDeletesAndAuditsTheRoleRelation() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        UserRoleEntity relation = new UserRoleEntity();
        relation.setRoleId(roleId);
        relation.setIsActive(true);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveByUserId(any(), any())).thenReturn(List.of(relation));

        var response = controller.removeRoleFromUser(userId, roleId, jwt(actorId));

        assertEquals(204, response.getStatusCode().value());
        assertEquals(false, relation.getIsActive());
        assertEquals(actorId, relation.getDeletedBy());
        verify(userRoleRepository).save(relation);
    }

    private JwtAuthenticationToken jwt(UUID actorId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(actorId.toString())
                .claim("features", List.of("role.assign"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
