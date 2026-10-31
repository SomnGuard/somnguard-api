package com.somnguard.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.somnguard.security.adapter.out.persistence.entity.RefreshTokenEntity;
import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import com.somnguard.security.adapter.out.persistence.repository.EmailVerificationRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import com.somnguard.security.application.port.out.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "postgres-test"})
@Testcontainers
class AuthPostgresIntegrationTest {

    private static final String PASSWORD = "PostgresPass123";
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("\\b\\d{6}\\b");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("somnguard_test")
            .withUsername("somnguard_test")
            .withPassword("somnguard_test")
            .withInitScript("postgres/init.sql");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailSender emailSender;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "true");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

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
    void registrationLoginAndRefreshPersistConsistentStateInPostgres() throws Exception {
        String email = "postgres+" + UUID.randomUUID() + "@example.test";
        String phone = "+1555" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "first_name", "Postgres",
                                "last_name", "Integration",
                                "phone", phone))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID userId = UUID.fromString(readJson(registered).get("id").asText());
        UserEntity registeredUser = userRepository.findById(userId).orElseThrow();
        assertEquals(email, registeredUser.getEmail());
        assertTrue(passwordEncoder.matches(PASSWORD, registeredUser.getPasswordHash()));
        assertEquals(1, userRoleRepository.findByUserIdAndIsActiveTrueAndDeletedAtIsNull(userId).size());
        assertFalse(emailVerificationRepository.findAll().stream()
                .filter(code -> code.getUserId().equals(userId)).toList().isEmpty());

        org.mockito.ArgumentCaptor<String> emailText = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(email), anyString(), emailText.capture(), anyString());
        Matcher codeMatcher = SIX_DIGIT_CODE.matcher(emailText.getValue());
        assertTrue(codeMatcher.find(), "El correo de verificación debe incluir su código");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", codeMatcher.group()))))
                .andExpect(status().isOk());
        assertNotNull(userRepository.findById(userId).orElseThrow().getEmailVerifiedAt());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String originalRefresh = readJson(login).get("refreshToken").asText();

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refresh_token", originalRefresh))))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedRefresh = readJson(refresh).get("refreshToken").asText();

        RefreshTokenEntity original = refreshTokenRepository.findByTokenHash(sha256(originalRefresh)).orElseThrow();
        RefreshTokenEntity replacement = refreshTokenRepository.findByTokenHash(sha256(rotatedRefresh)).orElseThrow();
        assertEquals(userId, original.getUserId());
        assertFalse(original.getIsActive());
        assertNotNull(original.getRevokedAt());
        assertNotNull(original.getReplacedBy());
        assertEquals(replacement.getId(), original.getReplacedBy());
        assertEquals(userId, replacement.getUserId());
        assertTrue(replacement.getIsActive());
        assertEquals(2, refreshTokenRepository.findByUserId(userId).size());
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
