package com.somnguard.security.application.usecase;

import com.nimbusds.jwt.SignedJWT;
import com.somnguard.security.adapter.in.web.dto.LoginResponse;
import com.somnguard.security.adapter.out.persistence.entity.AuditLoginEntity;
import com.somnguard.security.adapter.out.persistence.entity.RefreshTokenEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.repository.AuditLoginRepository;
import com.somnguard.security.adapter.out.persistence.repository.FeatureRepository;
import com.somnguard.security.adapter.out.persistence.repository.RefreshTokenRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.application.port.in.AuthUseCase;
import com.somnguard.security.application.service.JwtService;
import com.somnguard.security.domain.exception.InvalidCredentialsException;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;
    private final AuditLoginRepository auditLoginRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final FeatureRepository featureRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, AuditLoginRepository auditLoginRepository,
            RefreshTokenRepository refreshTokenRepository, RoleRepository roleRepository,
            FeatureRepository featureRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.auditLoginRepository = auditLoginRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleRepository = roleRepository;
        this.featureRepository = featureRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public LoginResponse login(String email, String password, String ip, String userAgent) {
        String normalizedEmail = email.trim().toLowerCase();
        var userOpt = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);

        if (userOpt.isEmpty()) {
            audit(null, normalizedEmail, "INVALID_CREDENTIALS", ip, userAgent);
            throw new InvalidCredentialsException("Invalid credentials");
        }
        UserEntity user = userOpt.get();

        if (Boolean.FALSE.equals(user.getIsActive()) || user.getDeletedAt() != null) {
            audit(user.getId(), normalizedEmail, "ACCOUNT_SUSPENDED", ip, userAgent);
            throw new InvalidCredentialsException("Account suspended");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            audit(user.getId(), normalizedEmail, "ACCOUNT_LOCKED", ip, userAgent);
            throw new InvalidCredentialsException("Account locked until " + user.getLockedUntil());
        }
        if (user.getEmailVerifiedAt() == null) {
            audit(user.getId(), normalizedEmail, "EMAIL_NOT_VERIFIED", ip, userAgent);
            throw new InvalidCredentialsException("Correo no verificado, revisa tu Gmail");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            short attempts = (short) (user.getFailedLoginAttempts() == null ? 1 : user.getFailedLoginAttempts() + 1);
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setLockedUntil(OffsetDateTime.now().plusMinutes(15));
            }
            userRepository.save(user);
            audit(user.getId(), normalizedEmail, "INVALID_CREDENTIALS", ip, userAgent);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        audit(user.getId(), normalizedEmail, "SUCCESS", ip, userAgent);

        List<String> roles = resolveRoles(user.getId());
        List<String> features = resolveFeatures(user.getId());

        String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles, features);
        String refresh = jwtService.generateRefreshToken(user.getId());

        // Persist refresh token hash in security.refresh_token
        SignedJWT parsed = parse(refresh);
        OffsetDateTime expiresAt;
        try {
            expiresAt = OffsetDateTime.ofInstant(parsed.getJWTClaimsSet().getExpirationTime().toInstant(), OffsetDateTime.now().getOffset());
        } catch (java.text.ParseException e) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setId(UUID.randomUUID());
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(refresh));
        rt.setExpiresAt(expiresAt);
        rt.setCreatedAt(OffsetDateTime.now());
        rt.setCreatedBy(user.getId());
        rt.setIsActive(true);
        refreshTokenRepository.save(rt);

        return new LoginResponse(access, refresh, "Bearer", 15 * 60);
    }

    @Override
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        var stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));

        if (Boolean.FALSE.equals(stored.getIsActive()) || stored.getRevokedAt() != null) {
            throw new InvalidCredentialsException("Refresh token revoked");
        }
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token expired");
        }
        // Validate JWT signature/exp/type still
        SignedJWT jwt = parse(refreshToken);
        try {
            String type = (String) jwt.getJWTClaimsSet().getClaim("type");
            if (!"refresh".equals(type)) {
                throw new InvalidCredentialsException("Invalid token type");
            }
            if (jwt.getJWTClaimsSet().getExpirationTime().before(new java.util.Date())) {
                throw new InvalidCredentialsException("Refresh token expired");
            }
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        // Rotation: revoke old, create new
        UUID userId = stored.getUserId();
        var user = userRepository.findById(userId).orElseThrow(() -> new InvalidCredentialsException("User not found"));

        List<String> refreshedRoles = resolveRoles(user.getId());
        List<String> refreshedFeatures = resolveFeatures(user.getId());
        String newAccess = jwtService.generateAccessToken(user.getId(), user.getEmail(), refreshedRoles, refreshedFeatures);
        String newRefresh = jwtService.generateRefreshToken(user.getId());
        SignedJWT newParsed = parse(newRefresh);
        OffsetDateTime newExpires;
        try {
            newExpires = OffsetDateTime.ofInstant(newParsed.getJWTClaimsSet().getExpirationTime().toInstant(), OffsetDateTime.now().getOffset());
        } catch (java.text.ParseException e) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        RefreshTokenEntity newRt = new RefreshTokenEntity();
        newRt.setId(UUID.randomUUID());
        newRt.setUserId(userId);
        newRt.setTokenHash(sha256(newRefresh));
        newRt.setExpiresAt(newExpires);
        newRt.setCreatedAt(OffsetDateTime.now());
        newRt.setCreatedBy(userId);
        newRt.setIsActive(true);
        refreshTokenRepository.save(newRt);

        stored.setRevokedAt(OffsetDateTime.now());
        stored.setIsActive(false);
        stored.setReplacedBy(newRt.getId());
        refreshTokenRepository.save(stored);

        return new LoginResponse(newAccess, newRefresh, "Bearer", 15 * 60);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        String hash = sha256(refreshToken);
        var stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));
        if (Boolean.TRUE.equals(stored.getIsActive())) {
            stored.setRevokedAt(OffsetDateTime.now());
            stored.setIsActive(false);
            refreshTokenRepository.save(stored);
        }
    }

    private void audit(UUID userId, String email, String outcome, String ip, String userAgent) {
        AuditLoginEntity a = new AuditLoginEntity();
        a.setId(UUID.randomUUID());
        a.setUserId(userId);
        a.setEmailAttempted(email);
        a.setOutcome(outcome);
        a.setIpAddress(ip != null ? ip : "0.0.0.0");
        a.setUserAgent(userAgent);
        a.setAttemptedAt(OffsetDateTime.now());
        a.setCreatedAt(OffsetDateTime.now());
        a.setIsActive(true);
        auditLoginRepository.save(a);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (Exception e) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
    }

    private List<String> resolveRoles(UUID userId) {
        List<String> roles = roleRepository.findActiveCodesByUserId(userId);
        return roles != null ? roles : List.of();
    }

    private List<String> resolveFeatures(UUID userId) {
        List<String> features = featureRepository.findCodesByUserId(userId);
        return features != null ? features : List.of();
    }
}
