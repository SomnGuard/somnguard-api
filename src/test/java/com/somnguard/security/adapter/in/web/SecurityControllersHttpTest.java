package com.somnguard.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.somnguard.security.adapter.out.persistence.entity.RefreshTokenEntity;
import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.PasswordResetRequestRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.security.application.port.out.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityControllersHttpTest {

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("\\b\\d{6}\\b");
    private static final String TEST_PASSWORD = "StrongPass123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PasswordResetRequestRepository passwordResetRequestRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void ensureDefaultRoleExists() {
        roleRepository.findByCodeAndIsActiveTrue("user").orElseGet(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            RoleEntity role = new RoleEntity();
            role.setId(UUID.randomUUID());
            role.setCode("user");
            role.setName("User");
            role.setIsActive(true);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            return roleRepository.save(role);
        });
    }

    @Test
    void authControllerRegistersValidatesAndRejectsDuplicateAccounts() throws Exception {
        String email = uniqueEmail("register");
        String payload = registerPayload(email);

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        UUID userId = UUID.fromString(readJson(registration).get("id").asText());
        assertTrue(userRepository.findById(userId).isPresent());
        assertFalse(emailVerificationRepository.findAll().stream().noneMatch(code -> code.getUserId().equals(userId)));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad-email\",\"password\":\"short\",\"first_name\":\"\",\"last_name\":\"User\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authControllerAuthenticatesAndRotatesRefreshTokens() throws Exception {
        UserEntity user = createVerifiedUser(uniqueEmail("login"));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        JsonNode loginBody = readJson(login);
        String originalRefresh = loginBody.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", "IncorrectPass123"))))
                .andExpect(status().isUnauthorized());

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refresh_token", originalRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String rotatedRefresh = readJson(refreshed).get("refreshToken").asText();
        assertNotEquals(originalRefresh, rotatedRefresh);

        RefreshTokenEntity original = refreshTokenRepository.findByTokenHash(sha256(originalRefresh)).orElseThrow();
        RefreshTokenEntity rotated = refreshTokenRepository.findByTokenHash(sha256(rotatedRefresh)).orElseThrow();
        assertFalse(original.getIsActive());
        assertNotNull(original.getRevokedAt());
        assertTrue(rotated.getIsActive());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refresh_token", originalRefresh))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refresh_token", "not-a-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountControllerRequiresAuthenticationAndOwnAccountFeatures() throws Exception {
        UserEntity user = createVerifiedUser(uniqueEmail("profile"));
        UserEntity other = createVerifiedUser(uniqueEmail("other"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me").with(jwtFor(user.getId(), "device.read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/me").with(jwtFor(user.getId(), "user.own_read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.password_hash").doesNotExist());

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(jwtFor(user.getId(), "user.own_write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("first_name", "Updated", "email", other.getEmail()))))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(jwtFor(user.getId(), "user.own_write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(jwtFor(user.getId(), "user.own_write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("first_name", "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));

        assertEquals("Updated", userRepository.findById(user.getId()).orElseThrow().getFirstName());
    }

    @Test
    void accountControllerResetsPasswordAndDeletesOnlyAuthenticatedAccount() throws Exception {
        UserEntity user = createVerifiedUser(uniqueEmail("account"));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail()))))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> emailText = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(user.getEmail()), anyString(), emailText.capture(), anyString());
        Matcher codeMatcher = SIX_DIGIT_CODE.matcher(emailText.getValue());
        assertTrue(codeMatcher.find(), "El correo debe incluir el código de restablecimiento");
        String code = codeMatcher.group();

        mockMvc.perform(post("/api/v1/auth/verify-reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "code", code))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "code", code, "new_password", "NewStrongPass123"))))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("NewStrongPass123",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash()));
        assertTrue(passwordResetRequestRepository.findByUserIdAndIsUsedFalseAndIsActiveTrue(user.getId()).isEmpty());

        mockMvc.perform(delete("/api/v1/users/me").with(jwtFor(user.getId(), "user.delete")))
                .andExpect(status().isNoContent());

        UserEntity deleted = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
        assertFalse(deleted.getIsActive());
    }

    @Test
    void adminRbacControllerEnforcesFeaturesAndManagesRoleAssignments() throws Exception {
        UserEntity target = createVerifiedUser(uniqueEmail("rbac"));

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/roles").with(jwtFor(target.getId(), "user.read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/roles").with(jwtFor(target.getId(), "role.read")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/roles")
                        .with(jwtFor(target.getId(), "user.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "analyst-" + UUID.randomUUID(), "name", "Analyst"))))
                .andExpect(status().isForbidden());

        String roleCode = "qa-" + UUID.randomUUID();
        MvcResult createdRole = mockMvc.perform(post("/api/v1/roles")
                        .with(jwtFor(target.getId(), "role.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", roleCode, "name", "QA role"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID roleId = UUID.fromString(readJson(createdRole).get("id").asText());

        mockMvc.perform(post("/api/v1/roles")
                        .with(jwtFor(target.getId(), "role.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", roleCode, "name", "Duplicate"))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/roles/{id}", UUID.randomUUID())
                        .with(jwtFor(target.getId(), "role.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "missing-role", "name", "Missing"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/users/{id}/roles", target.getId())
                        .with(jwtFor(UUID.randomUUID(), "role.assign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role_id", roleId))))
                .andExpect(status().isCreated());

        assertEquals(1, userRoleRepository.findByUserIdAndIsActiveTrueAndDeletedAtIsNull(target.getId()).size());

        mockMvc.perform(post("/api/v1/users/{id}/roles", target.getId())
                        .with(jwtFor(UUID.randomUUID(), "role.assign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role_id", roleId))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/users/{id}/roles/{roleId}", target.getId(), roleId)
                        .with(jwtFor(UUID.randomUUID(), "role.assign")))
                .andExpect(status().isNoContent());

        assertTrue(userRoleRepository.findByUserIdAndIsActiveTrueAndDeletedAtIsNull(target.getId()).isEmpty());
    }

    private UserEntity createVerifiedUser(String email) {
        OffsetDateTime now = OffsetDateTime.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setIsActive(true);
        user.setEmailVerifiedAt(now);
        user.setFailedLoginAttempts((short) 0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setVersion(1);
        user.setStatus("ACTIVE");
        user.setStatusCategory("ACTIVE");
        return userRepository.save(user);
    }

    private String registerPayload(String email) throws Exception {
        return json(Map.of(
                "email", email,
                "password", TEST_PASSWORD,
                "first_name", "Test",
                "last_name", "User",
                "phone", "+1555" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
    }

    private RequestPostProcessor jwtFor(UUID userId, String... features) {
        List<String> featureList = List.of(features);
        return jwt().jwt(token -> token.subject(userId.toString()).claim("features", featureList));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.test";
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
