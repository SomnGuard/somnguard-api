package com.somnguard.security.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.somnguard.security.adapter.out.persistence.entity.PasswordResetRequestEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.repository.PasswordResetRequestRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.application.port.out.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetRequestRepository resetRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSender emailSender;

    private PasswordResetService service;

    private static final String VALID_CODE = "123456";
    private static final String WRONG_CODE = "999999";

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository,
                resetRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailSender,
                15
        );
    }

    private String sha256(String val) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(val.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verifyResetCode_success_whenEmailAndCodeMatch() throws Exception {
        String email = "conductor@somnguard.com";
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setIsActive(true);
        user.setStatus("ACTIVE");

        PasswordResetRequestEntity req = new PasswordResetRequestEntity();
        req.setId(UUID.randomUUID());
        req.setUserId(userId);
        req.setTokenHash(sha256(VALID_CODE));
        req.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        req.setIsActive(true);
        req.setIsUsed(false);

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(resetRepository.findByUserIdAndIsUsedFalseAndIsActiveTrue(userId)).thenReturn(List.of(req));

        // Debe ejecutarse sin lanzar excepciones
        assertDoesNotThrow(() -> service.verifyResetCode(email, VALID_CODE));
    }

    @Test
    void verifyResetCode_fails_whenCodeBelongsToAnotherUser() throws Exception {
        String victimEmail = "victima@somnguard.com";
        String attackerTargetEmail = "atacante@somnguard.com";
        UUID attackerId = UUID.randomUUID();

        // El usuario atacante existe pero sus códigos activos no coinciden con el código que intenta adivinar
        UserEntity attacker = new UserEntity();
        attacker.setId(attackerId);
        attacker.setEmail(attackerTargetEmail);
        attacker.setIsActive(true);
        attacker.setStatus("ACTIVE");

        when(userRepository.findByEmailAndDeletedAtIsNull(attackerTargetEmail)).thenReturn(Optional.of(attacker));
        when(resetRepository.findByUserIdAndIsUsedFalseAndIsActiveTrue(attackerId)).thenReturn(List.of());

        // Debe rechazar la verificación porque el código no le pertenece a esa cuenta
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.verifyResetCode(attackerTargetEmail, VALID_CODE));
        assertEquals("Código inválido o ya usado", ex.getMessage());
    }

    @Test
    void verifyResetCode_blocksAndInvalidates_afterFiveFailedAttempts() throws Exception {
        String email = "target@somnguard.com";
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setIsActive(true);
        user.setStatus("ACTIVE");

        PasswordResetRequestEntity activeCode = new PasswordResetRequestEntity();
        activeCode.setId(UUID.randomUUID());
        activeCode.setUserId(userId);
        activeCode.setTokenHash(sha256(VALID_CODE));
        activeCode.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        activeCode.setIsActive(true);
        activeCode.setIsUsed(false);

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(resetRepository.findByUserIdAndIsUsedFalseAndIsActiveTrue(userId)).thenReturn(List.of(activeCode));

        // Intentos 1 al 4 fallan con mensaje normal
        for (int i = 1; i <= 4; i++) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.verifyResetCode(email, WRONG_CODE));
            assertEquals("Código inválido o ya usado", ex.getMessage());
        }

        // Al 5to intento fallido: se bloquea e invalida el código activo
        IllegalArgumentException blockedEx = assertThrows(IllegalArgumentException.class,
                () -> service.verifyResetCode(email, WRONG_CODE));
        assertTrue(blockedEx.getMessage().contains("Código bloqueado tras 5 intentos fallidos"));

        // Se verifica que se guardó la invalidación (setIsActive = false)
        assertFalse(activeCode.getIsActive());
        verify(resetRepository, atLeastOnce()).saveAll(any());
    }
}
